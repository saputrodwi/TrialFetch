package com.trialfetch.app.data

import com.trialfetch.app.core.BaozimhSource
import com.trialfetch.app.core.JjabtoonSource
import com.trialfetch.app.core.GoodtoonSource
import com.trialfetch.app.core.JjaptoonSource
import com.trialfetch.app.core.RumanhuaSource
import com.trialfetch.app.core.BannerCropper
import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.ChapterPage
import com.trialfetch.app.core.ComicSource
import com.trialfetch.app.core.HttpClient
import com.trialfetch.app.core.ReaderPage
import com.trialfetch.app.core.ImageCrypto
import com.trialfetch.app.core.ImageFormat
import com.trialfetch.app.core.KoudaimhSource
import com.trialfetch.app.core.ManhuahaoSource
import com.trialfetch.app.core.ManwangSource
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.SeriesInfo
import com.trialfetch.app.core.Source
import com.trialfetch.app.core.SourceException
import com.trialfetch.app.core.WmanhuaSource
import com.trialfetch.app.core.UrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import kotlinx.coroutines.withContext

/** Progres satu chapter yang sedang diunduh. */
data class DownloadProgress(
    val total: Int = 0,
    val done: Int = 0,
    val currentPage: Int = 0,
    val state: State = State.IDLE,
    val error: String? = null,
    val savedPath: String? = null,
    /**
     * Judul chapter yang sedang dikerjakan, untuk ditampilkan di
     * notifikasi dan panel unduhan.
     */
    val chapterTitle: String? = null,

    /**
     * ID chapter yang sedang berjalan — dipakai UI untuk menentukan baris
     * mana yang menampilkan spinner. Judul tidak bisa dipakai: dua chapter
     * dari volume berbeda bisa berjudul persis sama ("Chapter 12") sehingga
     * dua baris ikut berputar.
     */
    val chapterId: String? = null
) {
    enum class State { IDLE, RUNNING, DONE, FAILED, CANCELLED }

    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

class ComicRepository(
    private val context: android.content.Context,
    http: HttpClient = HttpClient()
) {
    // Cropper butuh Context untuk memuat 4 template banner dari res/raw.
    private val bannerCropper = BannerCropper(context.applicationContext)
    internal val storage = StorageWriter(context)
    internal val notifier = DownloadNotifier(context)

    private var http: HttpClient = http

    private var sources: Map<Source, ComicSource> = buildSources(http)

    val availableSources: List<Source>
        get() = sources.keys.toList()

    /**
     * Ganti DNS yang dipakai (mis. nyalakan DoH) tanpa membuat repository
     * baru. Source tidak menyimpan state sehingga aman dibangun ulang;
     * yang diganti hanya client HTTP di dalamnya.
     */
    fun updateNetwork(dns: okhttp3.Dns?) {
        http = HttpClient(HttpClient.defaultClient(dns))
        sources = buildSources(http)
    }

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    /**
     * Daftar gambar chapter yang sudah terunduh, siap dibaca reader.
     *
     * Mode Folder: langsung dari Download. Mode ZIP: diekstrak dulu ke
     * cache internal (dipakai ulang bila sudah ada).
     */
    fun getReadableImages(
        series: SeriesInfo,
        chapter: Chapter,
        settings: DownloadSettings
    ): List<android.net.Uri> {
        val chapterDir = sanitize(chapter.title)
        // Coba folder ber-prefix dulu; kalau kosong, jatuh ke folder lama
        // (tanpa prefix) supaya koleksi yang diunduh sebelum commit fc661ef
        // tetap bisa dibaca.
        for (seriesDir in candidateSeriesDirs(series)) {
            val images = if (settings.outputMode == OutputMode.ZIP) {
                storage.extractZipForRead(seriesDir, "$chapterDir.zip")
                    .map { android.net.Uri.fromFile(it) }
            } else {
                storage.listImages(listOf(seriesDir, chapterDir).joinToString("/"))
            }
            if (images.isNotEmpty()) return images
        }
        return emptyList()
    }

    /** true bila chapter sudah terunduh (ringan, tanpa membaca isi). */
    fun isDownloaded(
        series: SeriesInfo,
        chapter: Chapter,
        settings: DownloadSettings
    ): Boolean {
        val chapterDir = sanitize(chapter.title)
        return candidateSeriesDirs(series).any { seriesDir ->
            if (settings.outputMode == OutputMode.ZIP) {
                storage.hasFile(seriesDir, "$chapterDir.zip")
            } else {
                storage.countFiles(listOf(seriesDir, chapterDir).joinToString("/")) > 0
            }
        }
    }

    /** Kembalikan progres ke IDLE (menutup panel unduhan). */
    fun resetProgress() {
        _progress.value = DownloadProgress()
    }

    /** Tandai dibatalkan (panel menampilkan status batal + tombol tutup). */
    fun cancelProgress() {
        _progress.value = _progress.value.copy(state = DownloadProgress.State.CANCELLED)
    }

    /**
     * Nama folder chapter yang sudah terunduh untuk satu series.
     *
     * Dipakai badge "sudah diunduh" — satu panggilan untuk seluruh series.
     * Mode ZIP: nama zip tanpa ekstensi. Folder baru sudah ber-prefix id
     * sumber (lihat seriesDirOf), jadi status judul kembar beda sumber
     * terpisah; folder lama tanpa prefix tetap dibaca (lihat
     * [legacySeriesDirOf]) supaya unduhan lama tidak dianggap hilang.
     */
    fun listDownloadedChapters(
        series: SeriesInfo,
        settings: DownloadSettings
    ): Set<String> = candidateSeriesDirs(series).flatMap { seriesDir ->
        if (settings.outputMode == OutputMode.ZIP) {
            storage.listZipNames(seriesDir)
        } else {
            storage.listChapterDirs(seriesDir)
        }
    }.toSet()

    fun canWriteStorage(): Boolean = storage.canWrite()

    suspend fun search(source: Source, query: String): List<SearchResult> =
        sources[source]?.search(query)
            ?: throw SourceException("Sumber ${source.displayName} belum didukung")

    /**
     * Cari di semua sumber secara paralel. Sumber yang gagal tidak
     * menggagalkan hasil sumber lain; error-nya dilog saja.
     */
    suspend fun searchAll(query: String): List<SearchResult> = coroutineScope {
        // Maks 4 per grup, dan tiap request diberi delay acak kecil:
        // menembak 9 URL serentak bisa memicu rate-limit sumber.
        val flat = mutableListOf<List<SearchResult>>()
        for (group in sources.values.chunked(4)) {
            val batch = group.map { src ->
                async(Dispatchers.IO) {
                    try {
                        kotlinx.coroutines.delay(kotlin.random.Random.nextLong(0, 250))
                        src.search(query)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("ComicRepository", "pencarian ${src.source.displayName} gagal: ${e.message}")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
            flat.add(batch)
        }
        val results = flat.flatten().distinctBy { "${it.source.id}::${it.comicId}" }
        if (results.isEmpty()) {
            throw SourceException("Pencarian lintas sumber tidak menghasilkan apa pun")
        }
        results
    }
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
        settings: DownloadSettings = DownloadSettings(),
        // Hook untuk manajer antrian: progres per gambar + jeda kooperatif.
        // Default no-op sehingga pemanggil lama tidak berubah perilaku.
        onImage: ((done: Int, total: Int) -> Unit)? = null,
        isPaused: () -> Boolean = { false }
    ): DownloadProgress = withContext(Dispatchers.IO) {
        // Tandai RUNNING seketika supaya spinner di baris chapter langsung
        // muncul — sebelumnya baru berubah setelah metadata + gambar pertama
        // selesai sehingga terasa delay.
        _progress.value = DownloadProgress(
            total = 0, done = 0, currentPage = 0,
            state = DownloadProgress.State.RUNNING,
            chapterTitle = chapter.title,
            chapterId = chapter.chapterId
        )
        notifier.showRunning(chapter.title, 0, 0)
        val page = try {
            chapter(series.source, chapter.url)
        } catch (e: Exception) {
            return@withContext fail(e.message ?: "Gagal membaca chapter")
        }

        // Nama folder = prefix id sumber + judul series (lihat seriesDirOf),
        // jadi dua series judul sama dari sumber berbeda tidak berbagi folder.
        val seriesDir = seriesDirOf(series)
        val chapterDir = sanitize(chapter.title)
        val parentPath = listOf(seriesDir, chapterDir).joinToString("/")

        val total = page.images.size
        // Log ini yang menentukan apakah "dobel" berasal dari parser gambar
        // yang menghasilkan 2 URL per halaman, atau dari penulis file yang
        // membuat nama (1).
        Log.i(
            "ComicRepository",
            "chapter=${chapter.title} jumlahGambar=$total host=" +
                page.images.firstOrNull()?.url?.substringBefore("/scomic") +
                " mode=${settings.outputMode} sumber=${series.source}"
        )
        if (settings.cropBanner && series.source == Source.BAOZIMH) {
            bannerCropper.resetStats()
        }

        // Mode Folder: bersihkan isi folder chapter lebih dulu supaya
        // unduhan ulang tidak menghasilkan "nama(1).ext". clearFolder
        // mengembalikan jumlah yang berhasil dihapus; yang dilaporkan ke
        // pengguna adalah SISANYA (file yang tidak terlihat oleh aplikasi
        // sehingga tidak bisa dihapus, mis. sisa pemasangan sebelumnya).
        var leftoverFiles = 0
        if (settings.outputMode == OutputMode.FOLDER) {
            storage.clearFolder(parentPath)
            leftoverFiles = storage.countFiles(parentPath)
        }
        // Mode ZIP menulis langsung ke arsip via ZipSink (streaming, tidak
        // menampung di RAM). Mode Folder menulis per file seperti biasa.
        // Penghitung sukses terpisah untuk kedua mode.
        val zipSink = if (settings.outputMode == OutputMode.ZIP) {
            storage.openZip(seriesDir, "$chapterDir.zip")
                ?: return@withContext fail(
                    "Tidak bisa membuat arsip ZIP di folder Download. " +
                        "Periksa izin penyimpanan."
                )
        } else {
            null
        }
        var succeeded = 0
        val failed = mutableListOf<Int>()
        onImage?.invoke(0, total)

        try {
        // 3 gambar diproses bersamaan: dapat kecepatan tanpa meledakkan
        // heap (chunk kecil karena payload tiap gambar perlu ditahan).
        // Jeda user/Batal ditunggu kooperatif di batas antar-chunk supaya
        // tidak putus di tengah unduhan gambar.
        val CONCURRENCY = 3
        for (chunk in page.images.chunked(CONCURRENCY)) {
            while (isPaused()) delay(250)
            val ready = coroutineScope {
                chunk.map { img ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // Sekali retry dengan jeda pendek: CDN kadang membalas
                            // 429/timeout sesaat padahal request berikutnya lolos.
                            var raw: ByteArray? = null
                            var fetchError: Exception? = null
                            for (attempt in 0..1) {
                                try {
                                    raw = http.getBytes(
                                        url = img.url,
                                        // Sumber tertentu (Manwang/Rumanhua) host
                                        // gambarnya 403 bila Referer bukan situsnya.
                                        referer = sources[series.source]?.imageReferer
                                            ?: originOf(img.url),
                                        // Sebagian CDN memuat gambar dengan referrerpolicy
                                        // "no-referrer" di HTML aslinya — meniru itu lebih
                                        // aman daripada mengarang Referer.
                                        sendNoReferer = sources[series.source]?.noImageReferer == true
                                    )
                                    fetchError = null
                                    break
                                } catch (e: Exception) {
                                    fetchError = e
                                    if (attempt == 0) kotlinx.coroutines.delay(1500)
                                }
                            }
                            val bytes = raw ?: throw fetchError ?: SourceException("Gagal mengunduh halaman ${img.page}")
                            val plain = if (img.needsDecrypt) {
                                try {
                                    ImageCrypto.decrypt(bytes, IMAGE_KEY_MANWANG_RUMAN)
                                } catch (e: Exception) {
                                    Log.w("ComicRepository", "dekripsi halaman ${img.page} gagal: ${e.message}")
                                    return@async Triple(img, null, true)
                                }
                            } else {
                                bytes
                            }
                            val payload = if (settings.cropBanner && series.source == Source.BAOZIMH) {
                                bannerCropper.crop(plain)
                            } else {
                                plain
                            }
                            Triple(img, payload, false)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // JANGAN telan pembatalan: user menekan Batal =
                            // unduhan harus berhenti, bukan dianggap halaman gagal.
                            throw e
                        } catch (e: Exception) {
                            Log.w("ComicRepository", "halaman ${img.page} gagal: ${e.message}")
                            Triple(img, null, false)
                        }
                    }
                }.awaitAll()
            }
            // Tulis urutan asli gambar (page) satu per satu supaya arsip ZIP
            // (streaming, tidak thread-safe) aman, dan progres berurutan.
            for ((img, payload, decryptFailed) in ready.sortedBy { it.first.page }) {
                when {
                    decryptFailed -> failed += img.page
                    payload == null -> failed += img.page
                    else -> {
                        val finalFormat = ImageFormat.guessWithBitmap(payload)
                        if (finalFormat == ImageFormat.UNKNOWN) {
                            // Jangan simpan .bin: badge sukses tapi tak
                            // terbaca reader + tidak masuk listImages.
                            Log.w("ComicRepository", "format tidak dikenal halaman ${img.page}")
                            failed += img.page
                        } else {
                        val fileName = settings.naming.fileName(img.page, finalFormat.extension)
                        if (settings.outputMode == OutputMode.ZIP) {
                            try {
                                zipSink!!.put("$chapterDir/$fileName", payload)
                                succeeded++
                            } catch (e: Exception) {
                                Log.w("ComicRepository", "tulis ZIP halaman ${img.page} gagal: ${e.message}")
                                failed += img.page
                            }
                        } else {
                            if (storage.writeFile(parentPath, fileName, payload)) succeeded++
                        }
                        succeeded.let { done ->
                            _progress.value = DownloadProgress(
                                total = total, done = done, currentPage = img.page,
                                state = DownloadProgress.State.RUNNING,
                                chapterTitle = chapter.title,
                                chapterId = chapter.chapterId
                            )
                            notifier.showRunning(chapter.title, done, total)
                            onImage?.invoke(done, total)
                        }
                        }
                    }
                }
            }
        }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Arsip setengah jadi tidak ditinggalkan di Download.
            zipSink?.abort()
            throw e
        }

        val done = succeeded
        if (done == 0) {
            zipSink?.abort()
            return@withContext fail("Semua $total gambar gagal diunduh")
        }

        val note = if (failed.isEmpty()) "" else "\nGagal: halaman ${failed.joinToString(", ")}"
        val info = buildString {
            appendLine(series.title)
            if (series.author.isNotBlank()) appendLine("Penulis: ${series.author}")
            appendLine("Chapter: ${chapter.title}")
            appendLine("Sumber: ${series.source.displayName}")
            appendLine("Berhasil: $done dari $total halaman")
            if (settings.cropBanner && series.source == Source.BAOZIMH) {
                appendLine(
                    "Banner: ${bannerCropper.cut} dipotong dari ${bannerCropper.checked} diperiksa" +
                        (if (bannerCropper.failed > 0) ", ${bannerCropper.failed} gagal diperiksa" else "")
                )
            }
            if (leftoverFiles > 0) {
                appendLine(
                    "PERINGATAN: $leftoverFiles file lama tidak terlihat oleh aplikasi " +
                        "sehingga tidak bisa dihapus atau ditimpa. " +
                        "Hapus manual folder ini agar tidak muncul berkas '(1)'."
                )
            }
            appendLine("Waktu: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
            append(note)
        }

        val savedPath: String = when (settings.outputMode) {
            OutputMode.FOLDER -> {
                storage.writeText(parentPath, "info.txt", info)
                storage.displayPath(parentPath)
            }
            OutputMode.ZIP -> {
                // Entry ZIP memakai path relatif di dalam arsip supaya
                // saat diekstrak tetap rapi dan tidak bercampur
                // (ditulis streaming per gambar di loop di atas).
                val zipName = "$chapterDir.zip"
                if (zipSink == null || !zipSink.commit()) {
                    return@withContext fail("Gagal menyelesaikan arsip ZIP.")
                }
                storage.writeText(seriesDir, "$chapterDir.info.txt", info)
                storage.displayPath("$seriesDir/$zipName")
            }
        }

        val bannerLine = if (settings.cropBanner && series.source == Source.BAOZIMH &&
            bannerCropper.checked > 0
        ) {
            "\nBanner: ${bannerCropper.cut}/${bannerCropper.checked} dipotong"
        } else {
            ""
        }
        notifier.showDone(chapter.title + bannerLine, done, savedPath)
        DownloadProgress(
            total = total, done = done, state = DownloadProgress.State.DONE,
            savedPath = savedPath, chapterTitle = chapter.title,
            chapterId = chapter.chapterId
        ).also { _progress.value = it }
    }

    /** Membuka series dari URL yang ditempel pengguna. */
    suspend fun openUrl(rawUrl: String): SeriesInfo {
        return when (val parsed = UrlParser.parse(rawUrl)) {
            is UrlParser.Parsed.Series -> series(parsed.source, parsed.comicId)
            is UrlParser.Parsed.Unknown -> throw SourceException(parsed.reason)
            is UrlParser.Parsed.Chapter -> {
                // URL chapter: selesaikan ke series induknya supaya langsung
                // terbuka daftar chapternya. Praktis untuk yang hanya punya
                // link chapter dari browser.
                val src = sources[parsed.source]
                    ?: throw SourceException("Sumber ${parsed.source.displayName} belum didukung")
                val comicId = try {
                    src.seriesIdFromChapterUrl(parsed.url)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: throw SourceException(
                    "Link chapter ini tidak bisa ditelusuri ke serinya. " +
                        "Salin link halaman serinya untuk membuka daftar chapter."
                )
                series(parsed.source, comicId)
            }
        }
    }

    private fun fail(message: String, title: String = "Unduhan gagal"): DownloadProgress {
        notifier.showFailed(title, message)
        return DownloadProgress(state = DownloadProgress.State.FAILED, error = message)
            .also { _progress.value = it }
    }

    // originOf pindah ke HttpClient agar dipakai bersama getBytes/streaming.
    private fun originOf(url: String): String = http.originOf(url)

    /**
     * Halaman siap-streaming untuk reader: URL remote + header penangkal
     * hotlink yang sama persis dengan unduhan (lihat HttpClient.getBytes).
     */
    fun streamPages(series: SeriesInfo, page: ChapterPage): List<ReaderPage> {
        val noRef = sources[series.source]?.noImageReferer == true
        val siteRef = sources[series.source]?.imageReferer
        return page.images.sortedBy { it.page }.map { img ->
            ReaderPage(img.url, http.imageHeaders(img.url, noReferer = noRef, referer = siteRef))
        }
    }

    companion object {
        fun buildSources(http: HttpClient): Map<Source, ComicSource> = mapOf(
            Source.BAOZIMH to BaozimhSource(http),
            Source.MANWANG to ManwangSource(http),
            Source.WMANHUA to WmanhuaSource(http),
            Source.KOUDAIMH to KoudaimhSource(http),
            Source.JJABTOON to JjabtoonSource(http),
            Source.JJAPTOON to JjaptoonSource(http),
            Source.GOODTOON to GoodtoonSource(http),
            Source.RUMAN to RumanhuaSource(http),
            Source.MANHUAHAO to ManhuahaoSource(http)
        )

        /**
         * Kunci gambar Manwang/Rumanhua (source_id 12). Sama untuk keduanya
         * karena backend-nya sama; IV = key (lihat worker.js RUMANHUA_IMAGE_KEY,
         * dan ImageCrypto.decrypt default IV ke key).
         */
        const val IMAGE_KEY_MANWANG_RUMAN = "my2ecret782ecret"

        /** Nama folder aman untuk judul yang mengandung karakter ilegal. */
        fun sanitize(name: String): String =
            name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_").trim().take(80)

        /**
         * Folder series diawali id sumber supaya dua series judul sama
         * atau mirip dari sumber berbeda tidak menimpa satu sama lain
         * (clearFolder chapter bisa menghapus file milik series lain).
         */
        fun seriesDirOf(series: SeriesInfo): String =
            "${series.source.id}_${sanitize(series.title)}"

        /**
         * Folder series versi lama (tanpa prefix id sumber) — dipakai
         * unduhan yang dibuat sebelum folder ber-prefix diperkenalkan.
         * Hanya untuk MEMBACA; unduhan baru selalu ditulis ke
         * [seriesDirOf]. Tanpa fallback ini seluruh koleksi lama mendadak
         * tidak terbaca (badge hilang dan "buka dari hasil unduhan" gagal).
         */
        fun legacySeriesDirOf(series: SeriesInfo): String = sanitize(series.title)

        /**
         * Semua lokasi yang mungkin berisi unduhan series ini: folder
         * ber-prefix dulu (kanonik), baru folder lama. Urutannya penting —
         * yang kanonik menang kalau keduanya berisi.
         */
        fun candidateSeriesDirs(series: SeriesInfo): List<String> =
            listOf(seriesDirOf(series), legacySeriesDirOf(series)).distinct()
    }
}
