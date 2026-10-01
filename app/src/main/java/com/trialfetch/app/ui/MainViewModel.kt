package com.trialfetch.app.ui

import android.app.Application
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
    val error: String? = null
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
                coverUrl = info.coverUrl
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
    fun openReader(series: com.trialfetch.app.core.SeriesInfo, chapter: com.trialfetch.app.core.Chapter) {
        _reader.value = ReaderRequest(series, chapter)
        _readerPages.value = null
        _readerError.value = null
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

    fun openFromUrl() {
        val url = _urlInput.value
        if (url.isBlank() || _urlLoading.value) return
        _urlLoading.value = true
        viewModelScope.launch {
            try {
                val info = repo.openUrl(url)
                _series.value = SeriesUiState(info = info)
            } catch (e: Exception) {
                _series.value = SeriesUiState(error = e.message ?: "Gagal membuka URL")
            } finally {
                _urlLoading.value = false
            }
        }
    }

    fun onQueryChange(q: String) {
        _search.value = _search.value.copy(query = q, error = null)
    }

    fun onSourceChange(s: Source) {
        _search.value = _search.value.copy(source = s, results = emptyList(), error = null)
    }

    fun doSearch() {
        val st = _search.value
        if (st.query.isBlank() || st.loading) return
        _search.value = st.copy(loading = true, error = null, results = emptyList())
        viewModelScope.launch {
            try {
                val res = repo.search(st.source, st.query.trim())
                _search.value = _search.value.copy(loading = false, results = res)
            } catch (e: Exception) {
                _search.value = _search.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun openSaved(item: SavedSeries) {
        _series.value = SeriesUiState(loading = true)
        viewModelScope.launch {
            try {
                val info = repo.series(item.source, item.comicId)
                _series.value = SeriesUiState(info = info)
            } catch (e: Exception) {
                _series.value = SeriesUiState(error = e.message ?: "Gagal memuat series")
            }
        }
    }

    fun openSeries(result: SearchResult) {
        _series.value = SeriesUiState(loading = true)
        viewModelScope.launch {
            try {
                val info = repo.series(result.source, result.comicId)
                _series.value = SeriesUiState(info = info)
            } catch (e: Exception) {
                _series.value = SeriesUiState(error = e.message ?: "Gagal memuat series")
            }
        }
    }

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
