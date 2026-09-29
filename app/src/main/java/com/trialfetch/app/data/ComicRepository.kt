package com.trialfetch.app.data

import com.trialfetch.app.core.BaozimhSource
import com.trialfetch.app.core.BannerCropper
import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.ChapterPage
import com.trialfetch.app.core.ComicSource
import com.trialfetch.app.core.HttpClient
import com.trialfetch.app.core.ImageCrypto
import com.trialfetch.app.core.ImageFormat
import com.trialfetch.app.core.KoudaimhSource
import com.trialfetch.app.core.ManwangSource
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.SeriesInfo
import com.trialfetch.app.core.Source
import com.trialfetch.app.core.SourceException
import com.trialfetch.app.core.WmanhuaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Progres satu chapter yang sedang diunduh. */
data class DownloadProgress(
    val total: Int = 0,
    val done: Int = 0,
    val currentPage: Int = 0,
    val state: State = State.IDLE,
    val error: String? = null,
    val savedPath: String? = null
) {
    enum class State { IDLE, RUNNING, DONE, FAILED, CANCELLED }

    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

class ComicRepository(
    private val context: android.content.Context,
    private val http: HttpClient = HttpClient()
) {
    private val bannerCropper = BannerCropper(context)
    private val storage = StorageWriter(context)

    private val sources: Map<Source, ComicSource> = mapOf(
        Source.BAOZIMH to BaozimhSource(http),
        Source.MANWANG to ManwangSource(http),
        Source.WMANHUA to WmanhuaSource(http),
        Source.KOUDAIMH to KoudaimhSource(http)
    )

    val availableSources: List<Source> = sources.keys.toList()

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    fun canWriteStorage(): Boolean = storage.canWrite()

    suspend fun search(source: Source, query: String): List<SearchResult> =
        sources[source]?.search(query)
            ?: throw SourceException("Sumber ${source.displayName} belum didukung")

    suspend fun series(source: Source, comicId: String): SeriesInfo =
        sources[source]?.series(comicId)
            ?: throw SourceException("Sumber ${source.displayName} belum didukung")

    suspend fun chapter(source: Source, url: String): ChapterPage =
        sources[source]?.chapter(url)
            ?: throw SourceException("Sumber ${source.displayName} belum didukung")

    /**
     * Unduh seluruh gambar satu chapter ke Download/TrialFetch.
     *
     * Hasil ditulis sesuai [settings]: folder berisi file gambar, atau satu
     * arsip zip berisi file yang sama. Penamaan mengikuti
     * [DownloadSettings.naming] (default 0001, 0002, …).
     */
    suspend fun downloadChapter(
        series: SeriesInfo,
        chapter: Chapter,
        settings: DownloadSettings = DownloadSettings()
    ): DownloadProgress = withContext(Dispatchers.IO) {
        val page = try {
            chapter(series.source, chapter.url)
        } catch (e: Exception) {
            return@withContext fail(e.message ?: "Gagal membaca chapter")
        }

        val seriesDir = sanitize(series.title)
        val chapterDir = sanitize(chapter.title)
        val parentPath = listOf(seriesDir, chapterDir).joinToString("/")

        val total = page.images.size
        val written = mutableListOf<Pair<String, ByteArray>>()
        val failed = mutableListOf<Int>()

        for (img in page.images) {
            try {
                val raw = http.getBytes(
                    url = img.url,
                    referer = originOf(img.url),
                    // Sebagian CDN memuat gambar dengan referrerpolicy
                    // "no-referrer" di HTML aslinya — meniru itu lebih
                    // aman daripada mengarang Referer.
                    sendNoReferer = series.source == Source.KOUDAIMH
                )

                val format = ImageFormat.sniff(raw)
                val payload = if (settings.cropBanner && series.source == Source.BAOZIMH) {
                    bannerCropper.crop(raw, format) ?: raw
                } else {
                    raw
                }

                val finalFormat = ImageFormat.sniff(payload)
                val fileName = settings.naming.fileName(img.page, finalFormat.extension)

                if (settings.outputMode == OutputMode.ZIP) {
                    // Ditunda sampai semua gambar terkumpul, supaya tidak
                    // menulis file lalu menghapusnya lagi.
                    written += fileName to payload
                } else {
                    storage.writeFile(parentPath, fileName, payload)
                }
                written.size.let { done ->
                    _progress.value = DownloadProgress(
                        total = total, done = done, currentPage = img.page,
                        state = DownloadProgress.State.RUNNING
                    )
                }
            } catch (e: Exception) {
                failed += img.page
            }
        }

        val done = written.size
        if (done == 0) {
            return@withContext fail("Semua $total gambar gagal diunduh")
        }

        val note = if (failed.isEmpty()) "" else "\nGagal: halaman ${failed.joinToString(", ")}"
        val info = buildString {
            appendLine(series.title)
            if (series.author.isNotBlank()) appendLine("Penulis: ${series.author}")
            appendLine("Chapter: ${chapter.title}")
            appendLine("Sumber: ${series.source.displayName}")
            appendLine("Berhasil: $done dari $total halaman")
            appendLine("Waktu: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
            append(note)
        }

        val savedPath: String = when (settings.outputMode) {
            OutputMode.FOLDER -> {
                storage.writeText(parentPath, "info.txt", info)
                "Download/${OutputPaths.ROOT}/$parentPath"
            }
            OutputMode.ZIP -> {
                val zipName = "$chapterDir.zip"
                val zipPath = "Download/${OutputPaths.ROOT}/$seriesDir"
                storage.createZip(zipPath, zipName, written)
                storage.writeText(zipPath, "$chapterDir.info.txt", info)
                "$zipPath/$zipName"
            }
        }

        DownloadProgress(
            total = total, done = done, state = DownloadProgress.State.DONE,
            savedPath = savedPath
        ).also { _progress.value = it }
    }

    private fun fail(message: String): DownloadProgress =
        DownloadProgress(state = DownloadProgress.State.FAILED, error = message)
            .also { _progress.value = it }

    private fun originOf(url: String): String = runCatching {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}/"
    }.getOrDefault(url)

    companion object {
        /** Nama folder aman untuk judul yang mengandung karakter ilegal. */
        fun sanitize(name: String): String =
            name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_").trim().take(80)
    }
}
