package com.trialfetch.app.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.SeriesInfo
import com.trialfetch.app.core.Source
import com.trialfetch.app.core.SourceException
import com.trialfetch.app.data.ComicRepository
import com.trialfetch.app.data.DownloadProgress
import com.trialfetch.app.data.DownloadQueueManager
import com.trialfetch.app.data.QueueItem
import android.util.Log
import com.trialfetch.app.core.DohConfig
import com.trialfetch.app.core.HttpClient
import com.trialfetch.app.data.BookmarkStore
import com.trialfetch.app.data.HistoryEntry
import com.trialfetch.app.data.ReadHistoryStore
import com.trialfetch.app.data.DownloadSettings
import com.trialfetch.app.data.SavedSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.trialfetch.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val source: Source = Source.BAOZIMH,
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null,
    /** true saat memakai mode "cari di semua sumber". */
    val allSources: Boolean = false
)

data class SeriesUiState(
    val loading: Boolean = false,
    val info: SeriesInfo? = null,
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = ComicRepository(app)

    // Client polos (DNS sistem) yang dipakai hanya untuk query HTTPS ke
    // resolver DoH. Ditaruh di sini (bukan di bawah) karena init memakainya.
    // Setelah DoH aktif, resolve lainnya lewat DoH.
    private val dohBootstrap = HttpClient.defaultClient()

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _series = MutableStateFlow(SeriesUiState())
    val series: StateFlow<SeriesUiState> = _series.asStateFlow()

    val progress: StateFlow<DownloadProgress> = repo.progress

    private val settingsStore = SettingsStore(app)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<DownloadSettings> = _settings.asStateFlow()

    init {
        applyNetwork(_settings.value)
    }

    fun updateSettings(s: DownloadSettings) {
        _settings.value = s
        settingsStore.save(s)
        applyNetwork(s)
    }

    // --- Cadangan data (bookmark + riwayat) ---

    /** Ekspor bookmark + riwayat ke Download/TrialFetch/cadangan_data.json. */
    fun exportBackup() {
        val ok = try {
            val json = buildString {
                append("{\"version\":1,\"bookmarks\":")
                append(org.json.JSONArray().apply {
                    _bookmarks.value.forEach { put(it.toJson()) }
                }.toString())
                append(",\"history\":")
                append(org.json.JSONArray().apply {
                    _history.value.forEach { put(it.toJson()) }
                }.toString())
                append("}")
            }
            repo.storage.writeText("", "cadangan_data.json", json) != null
        } catch (e: Exception) {
            false
        }
        if (ok) {
            Toast.makeText(getApplication(), "Cadangan tersimpan: Download/TrialFetch/cadangan_data.json", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(getApplication(), "Gagal menyimpan cadangan", Toast.LENGTH_LONG).show()
        }
    }

    /** Pulihkan bookmark + riwayat dari cadangan_data.json yang ada. */
    fun importBackup() {
        val count = try {
            val text = repo.storage.readText("", "cadangan_data.json")
                ?: return Toast.makeText(getApplication(), "File cadangan tidak ditemukan", Toast.LENGTH_LONG).show()
            val obj = org.json.JSONObject(text)
            var n = 0
            obj.optJSONArray("bookmarks")?.let { arr ->
                val existing = _bookmarks.value.toMutableList()
                for (i in 0 until arr.length()) {
                    val item = SavedSeries.fromJson(arr.optJSONObject(i) ?: continue) ?: continue
                    if (existing.none { it.key() == item.key() }) {
                        existing.add(item); n++
                    }
                }
                _bookmarks.value = existing
                bookmarkStore.saveAll(existing)
            }
            obj.optJSONArray("history")?.let { arr ->
                val existing = _history.value.toMutableList()
                for (i in 0 until arr.length()) {
                    val item = HistoryEntry.fromJson(arr.optJSONObject(i) ?: continue) ?: continue
                    if (existing.none { it.key() == item.key() }) {
                        existing.add(item); n++
                    }
                }
                _history.value = existing
                historyStore.saveAll(existing)
            }
            n
        } catch (e: Exception) {
            -1
        }
        when {
            count > 0 -> Toast.makeText(getApplication(), "Pulih $count entri cadangan", Toast.LENGTH_LONG).show()
            count == 0 -> Toast.makeText(getApplication(), "Tidak ada entri baru di cadangan", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(getApplication(), "File cadangan rusak", Toast.LENGTH_LONG).show()
        }
    }

    // --- Cek update series yang di-bookmark ---
    private val _bookmarkUpdates = MutableStateFlow<List<String>>(emptyList())
    val bookmarkUpdates: StateFlow<List<String>> = _bookmarkUpdates.asStateFlow()
    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking.asStateFlow()

    fun checkBookmarkUpdates() {
        if (_updateChecking.value) return
        _updateChecking.value = true
        viewModelScope.launch {
            val found = mutableListOf<String>()
            _bookmarks.value.take(30).forEach { b ->
                try {
                    val info = repo.series(b.source, b.comicId)
                    val latest = info.latestChapterTitle.trim()
                    if (latest.isNotBlank() && latest != b.latestChapterTitle) {
                        found.add("${b.title}: $latest")
                        val i = _bookmarks.value.indexOfFirst { it.key() == b.key() }
                        if (i >= 0) {
                            val cur = _bookmarks.value.toMutableList()
                            cur[i] = cur[i].copy(latestChapterTitle = latest)
                            _bookmarks.value = cur
                            bookmarkStore.saveAll(cur)
                        }
                    }
                    kotlinx.coroutines.delay(700)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Sumber yang sedang down tidak menggagalkan cek sisanya.
                }
            }
            _bookmarkUpdates.value = found
            _updateChecking.value = false
            if (found.isNotEmpty()) {
                repo.notifier.showBookmarkUpdates(found.size, found)
            }
        }
    }

    fun clearBookmarkUpdates() {
        _bookmarkUpdates.value = emptyList()
    }

    private fun applyNetwork(s: DownloadSettings) {
        if (!s.dohEnabled) {
            repo.updateNetwork(null)
            return
        }
        val dns = runCatching { DohConfig.buildDns(s.dohProvider, dohBootstrap) }
            .getOrElse { e ->
                Log.w("MainViewModel", "DoH gagal dibangun, pakai DNS sistem: ${e.message}")
                null
            }
        repo.updateNetwork(dns)
        Log.i("MainViewModel", "DNS: " + if (dns == null) "sistem" else "DoH " + s.dohProvider.name)
    }

    // --- Bookmark series ---
    private val bookmarkStore = BookmarkStore(app)

    private val _bookmarks = MutableStateFlow(bookmarkStore.load())
    val bookmarks: StateFlow<List<SavedSeries>> = _bookmarks.asStateFlow()

    fun isBookmarked(source: com.trialfetch.app.core.Source, comicId: String): Boolean =
        _bookmarks.value.any { it.source == source && it.comicId == comicId }

    fun toggleBookmark(info: com.trialfetch.app.core.SeriesInfo) {
        val cur = _bookmarks.value.toMutableList()
        val i = cur.indexOfFirst { it.source == info.source && it.comicId == info.comicId }
        if (i >= 0) cur.removeAt(i)
        else cur.add(
            0, SavedSeries(
                source = info.source,
                comicId = info.comicId,
                title = info.title,
                author = info.author,
                coverUrl = info.coverUrl,
                latestChapterTitle = info.latestChapterTitle
            )
        )
        _bookmarks.value = cur
        bookmarkStore.saveAll(cur)
    }

    // --- Reader bawaan ---
    data class ReaderRequest(
        val series: com.trialfetch.app.core.SeriesInfo,
        val chapter: com.trialfetch.app.core.Chapter
    )

    private val _reader = MutableStateFlow<ReaderRequest?>(null)
    val reader: StateFlow<ReaderRequest?> = _reader.asStateFlow()

    private val _readerPages = MutableStateFlow<List<com.trialfetch.app.core.ReaderPage>?>(null)
    val readerPages: StateFlow<List<com.trialfetch.app.core.ReaderPage>?> = _readerPages.asStateFlow()

    private val _readerError = MutableStateFlow<String?>(null)
    val readerError: StateFlow<String?> = _readerError.asStateFlow()

    /**
     * Buka reader: pakai file lokal bila sudah diunduh, kalau belum
     * streaming langsung dari URL seperti versi web (tanpa wajib unduh).
     * Chapter terenkripsi (URL-nya ciphertext) tetap wajib diunduh dulu.
     */
    private val _readerInitialPage = MutableStateFlow(0)
    val readerInitialPage: StateFlow<Int> = _readerInitialPage.asStateFlow()

    fun openReader(
        series: com.trialfetch.app.core.SeriesInfo,
        chapter: com.trialfetch.app.core.Chapter,
        initialPage: Int = 0
    ) {
        _reader.value = ReaderRequest(series, chapter)
        _readerPages.value = null
        _readerError.value = null
        _readerInitialPage.value = initialPage.coerceAtLeast(0)
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) {
                repo.getReadableImages(series, chapter, _settings.value)
            }
            if (_reader.value?.chapter?.chapterId != chapter.chapterId) return@launch
            if (local.isNotEmpty()) {
                _readerPages.value = local.map {
                    com.trialfetch.app.core.ReaderPage(it.toString())
                }
                return@launch
            }
            try {
                val page = withContext(Dispatchers.IO) {
                    repo.chapter(series.source, chapter.url)
                }
                if (_reader.value?.chapter?.chapterId != chapter.chapterId) return@launch
                if (page.images.isEmpty()) {
                    _readerError.value = "Chapter ini tidak berisi gambar."
                    return@launch
                }
                if (page.images.any { it.needsDecrypt }) {
                    _readerError.value = "Chapter terenkripsi — unduh dulu untuk membukanya."
                    return@launch
                }
                _readerPages.value = repo.streamPages(series, page)
            } catch (e: Exception) {
                if (_reader.value?.chapter?.chapterId != chapter.chapterId) return@launch
                _readerError.value = e.message ?: "Gagal memuat chapter"
            }
        }
    }

    fun closeReader() {
        _reader.value = null
        _readerPages.value = null
        _readerError.value = null
    }

    fun removeBookmark(item: SavedSeries) {
        val cur = _bookmarks.value.filterNot { it.key() == item.key() }
        _bookmarks.value = cur
        bookmarkStore.saveAll(cur)
    }

    fun canWriteStorage(): Boolean = repo.canWriteStorage()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _urlLoading = MutableStateFlow(false)
    val urlLoading: StateFlow<Boolean> = _urlLoading.asStateFlow()

    fun onUrlChange(v: String) {
        _urlInput.value = v
        _series.value = _series.value.copy(error = null)
    }

    /**
     * Buka series/chapter dari URL tempelan. Sukses: panggil [onLoaded]
     * agar pemanggil bisa navigasi ke layar series; gagal: Toast keras
     * karena layar Home tidak menampilkan error series.
     */
    fun openFromUrl(onLoaded: (SeriesInfo) -> Unit = {}) {
        val url = _urlInput.value
        if (url.isBlank() || _urlLoading.value) return
        _urlLoading.value = true
        viewModelScope.launch {
            try {
                val info = repo.openUrl(url)
                _series.value = SeriesUiState(info = info)
                onLoaded(info)
            } catch (e: Exception) {
                val msg = e.message ?: "Gagal membuka URL"
                _series.value = SeriesUiState(error = msg)
                Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
            } finally {
                _urlLoading.value = false
            }
        }
    }

    fun onQueryChange(q: String) {
        _search.value = _search.value.copy(query = q, error = null)
    }

    /** Reset beranda: hapus query, hasil cari, error, dan URL. Sumber tetap. */
    fun clearSearch() {
        val src = _search.value.source
        _search.value = SearchUiState(source = src)
        _urlInput.value = ""
        _urlLoading.value = false
    }

    fun onSourceChange(s: Source) {
        _search.value = _search.value.copy(source = s, results = emptyList(), error = null, allSources = false)
    }

    /** Mode pencarian lintas sumber: satu query dijalankan ke semua sumber. */
    fun searchAllSources() {
        _search.value = _search.value.copy(allSources = true, results = emptyList(), error = null)
    }

    fun doSearch() {
        val st = _search.value
        if (st.query.isBlank() || st.loading) return
        _search.value = st.copy(loading = true, error = null, results = emptyList())
        viewModelScope.launch {
            try {
                val res = if (st.allSources) {
                    repo.searchAll(st.query.trim())
                } else {
                    repo.search(st.source, st.query.trim())
                }
                _search.value = _search.value.copy(loading = false, results = res)
            } catch (e: Exception) {
                val msg = e.message ?: "Pencarian gagal"
                _search.value = _search.value.copy(loading = false, error = msg)
                // Notifikasi keras: error inline saja mudah terlewat,
                // terutama untuk sumber yang search-nya sering dibatasi.
                Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Buka entri riwayat: muat series, lompat ke chapter + halaman terakhir. */
    fun openHistory(entry: HistoryEntry) {
        _series.value = SeriesUiState(loading = true)
        viewModelScope.launch {
            try {
                val info = repo.series(entry.source, entry.comicId)
                _series.value = SeriesUiState(info = info)
                val ch = info.chapters.firstOrNull { it.chapterId == entry.chapterId }
                if (ch != null) {
                    openReader(info, ch, entry.page)
                }
            } catch (e: Exception) {
                _series.value = SeriesUiState(error = e.message ?: "Gagal memuat series")
            }
        }
    }

    fun openSaved(item: SavedSeries) {
        _series.value = SeriesUiState(loading = true)
        viewModelScope.launch {
            try {
                val info = repo.series(item.source, item.comicId)
                _series.value = SeriesUiState(info = info)
                refreshDownloaded(info)
            } catch (e: Exception) {
                _series.value = SeriesUiState(error = e.message ?: "Gagal memuat series")
            }
        }
    }

    fun openSeries(result: SearchResult) {
        openSeriesById(result.source, result.comicId)
    }

    /** Buka series dari route navigasi (source + id string). */
    fun openSeriesById(source: com.trialfetch.app.core.Source, comicId: String) {
        _series.value = SeriesUiState(loading = true)
        viewModelScope.launch {
            try {
                val info = repo.series(source, comicId)
                _series.value = SeriesUiState(info = info)
                refreshDownloaded(info)
            } catch (e: Exception) {
                _series.value = SeriesUiState(error = e.message ?: "Gagal memuat series")
            }
        }
    }

    // --- Riwayat baca ---
    private val historyStore = ReadHistoryStore(app)

    private val _history = MutableStateFlow(historyStore.load())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    /** Catat posisi baca; dipanggil tiap ganti halaman di reader. */
    fun recordReadProgress(
        series: com.trialfetch.app.core.SeriesInfo,
        chapter: com.trialfetch.app.core.Chapter,
        page: Int,
        totalPages: Int
    ) {
        val entry = HistoryEntry(
            source = series.source,
            comicId = series.comicId,
            title = series.title,
            coverUrl = series.coverUrl,
            chapterId = chapter.chapterId,
            chapterTitle = chapter.title,
            page = page,
            totalPages = totalPages
        )
        historyStore.record(entry)
        _history.value = historyStore.load()
    }

    fun removeHistory(key: String) {
        historyStore.remove(key)
        _history.value = historyStore.load()
    }

    fun clearHistory() {
        historyStore.clear()
        _history.value = emptyList()
    }

    // --- Status unduhan per chapter (satu query per series) ---
    private val _downloadedIds = MutableStateFlow(emptySet<String>())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    /** Muat ulang himpunan nama folder chapter yang sudah terunduh. */
    fun refreshDownloaded(series: com.trialfetch.app.core.SeriesInfo) {
        viewModelScope.launch {
            val set = withContext(Dispatchers.IO) {
                repo.listDownloadedChapters(series, _settings.value)
            }
            // Hanya terapkan bila series yang terbuka masih sama.
            if (_series.value.info?.let { it.source == series.source && it.comicId == series.comicId } == true) {
                _downloadedIds.value = set
            }
        }
    }

    fun isChapterDownloaded(chapter: com.trialfetch.app.core.Chapter): Boolean {
        // Bandingkan bentuk yang SAMA dengan yang tersimpan di folder/zip
        // (hasil sanitize), bukan judul mentah — judul mentah bisa cocok
        // dengan chapter lain yang namanya kebetulan sama setelah dibersihkan.
        val target = sanitizeName(chapter.title)
        return _downloadedIds.value.any { it.equals(target, ignoreCase = true) }
    }

    private fun sanitizeName(title: String): String =
        ComicRepository.sanitize(title)

    fun closeSeries() {
        _series.value = SeriesUiState()
    }

    // Semua unduhan (satuan maupun banyak) lewat satu antrian supaya
    // berurutan dan bisa dijeda. Guard double-tap lama tidak perlu lagi:
    // manajer menolak duplikat QUEUED/ACTIVE dengan kunci yang sama.
    val queue = DownloadQueueManager(viewModelScope, repo) { _settings.value }
    val queueItems: StateFlow<List<QueueItem>> = queue.items
    val queuePaused: StateFlow<Boolean> = queue.paused

    fun download(info: SeriesInfo, chapter: Chapter) {
        queue.enqueue(info, listOf(chapter))
    }

    fun enqueueChapters(info: SeriesInfo, chapters: List<Chapter>) {
        queue.enqueue(info, chapters)
    }

    /** Batalkan unduhan yang sedang berjalan lalu tutup panelnya. */
    fun cancelDownload() {
        queue.cancelCurrent()
        repo.cancelProgress()
    }

    /** Tutup panel hasil (selesai/gagal) tanpa membatalkan apa pun. */
    fun dismissProgress() {
        // Jangan tutup saat masih berjalan; pakai cancelDownload untuk itu.
        if (queue.isBusy()) return
        repo.resetProgress()
    }
}
