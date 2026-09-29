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
import com.trialfetch.app.data.DownloadSettings
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

    private val _settings = MutableStateFlow(DownloadSettings())
    val settings: StateFlow<DownloadSettings> = _settings.asStateFlow()

    fun updateSettings(s: DownloadSettings) { _settings.value = s }

    fun canWriteStorage(): Boolean = repo.canWriteStorage()

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

    fun download(info: SeriesInfo, chapter: Chapter) {
        if (progress.value.state == DownloadProgress.State.RUNNING) return
        viewModelScope.launch {
            repo.downloadChapter(info, chapter, _settings.value)
        }
    }
}
