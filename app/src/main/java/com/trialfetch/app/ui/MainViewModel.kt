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
import com.trialfetch.app.data.BookmarkStore
import com.trialfetch.app.data.DownloadSettings
import com.trialfetch.app.data.SavedSeries
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

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    private val _series = MutableStateFlow(SeriesUiState())
    val series: StateFlow<SeriesUiState> = _series.asStateFlow()

    val progress: StateFlow<DownloadProgress> = repo.progress

    private val settingsStore = SettingsStore(app)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<DownloadSettings> = _settings.asStateFlow()

    fun updateSettings(s: DownloadSettings) {
        _settings.value = s
        settingsStore.save(s)
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
            } catch (e: SourceException) {
                _search.value = _search.value.copy(loading = false, error = e.message)
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

    // Penanda yang sedang ada, terpisah dari progress. Flag progress baru
    // berubah ke RUNNING setelah gambar pertama selesai, jadi dua tap cepat
    // bisa sama-sama lolos guard dari progress dan mengunduh chapter yang sama
    // dua kali. Flag ini disetel seketika sebelum coroutine mulai.
    private val downloading = java.util.concurrent.atomic.AtomicBoolean(false)

    fun download(info: SeriesInfo, chapter: Chapter) {
        if (!downloading.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                repo.downloadChapter(info, chapter, _settings.value)
            } finally {
                downloading.set(false)
            }
        }
    }
}
