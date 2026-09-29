package com.trialfetch.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.SeriesInfo
import com.trialfetch.app.data.ComicRepository
import com.trialfetch.app.data.DownloadProgress
import com.trialfetch.app.ui.theme.TrialFetchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrialFetchTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val seriesState by vm.series.collectAsStateWithLifecycle()
    val downloadState by vm.progress.collectAsStateWithLifecycle()
    val series = seriesState.info

    if (series != null) {
        SeriesScreen(
            info = series,
            progress = downloadState,
            onBack = vm::closeSeries,
            onDownload = { vm.download(series, it) }
        )
        return
    }

    var showSettings by remember { mutableStateOf(false) }
    val searchState by vm.search.collectAsStateWithLifecycle()

    SearchScreen(
        state = searchState,
        sources = vm.repo.availableSources,
        onQueryChange = vm::onQueryChange,
        onSourceChange = vm::onSourceChange,
        onSearch = vm::doSearch,
        onPick = vm::openSeries,
        onOpenSettings = { showSettings = true }
    )

    if (showSettings) {
        SettingsDialog(
            initial = vm.settings.collectAsStateWithLifecycle().value,
            onDismiss = { showSettings = false },
            onConfirm = { vm.updateSettings(it); showSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    state: SearchUiState,
    sources: List<com.trialfetch.app.core.Source>,
    onQueryChange: (String) -> Unit,
    onSourceChange: (com.trialfetch.app.core.Source) -> Unit,
    onSearch: () -> Unit,
    onPick: (SearchResult) -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trial Fetch") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cari judul komik") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onSearch, enabled = !state.loading) {
                        Icon(Icons.Default.Search, contentDescription = "Cari")
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources) { s ->
                    FilterChip(
                        selected = state.source == s,
                        onClick = { onSourceChange(s) },
                        label = { Text(s.displayName) }
                    )
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "Ketik judul lalu tekan ikon cari.\n" +
                            "Contoh: 灵气, X战警, 被校花",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.results, key = { it.comicId }) { r ->
                        ResultRow(r) { onPick(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = r.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (r.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        r.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesScreen(
    info: SeriesInfo,
    progress: DownloadProgress,
    onBack: () -> Unit,
    onDownload: (Chapter) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(info.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            if (progress.state != DownloadProgress.State.IDLE) {
                DownloadPanel(progress)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = info.coverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 72.dp, height = 96.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            if (info.author.isNotBlank()) {
                                Text("Penulis: ${info.author}",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            if (info.latestChapterTitle.isNotBlank()) {
                                Text("Terbaru: ${info.latestChapterTitle}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${info.chapters.size} chapter",
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                items(info.chapters, key = { it.chapterId }) { ch ->
                    ChapterRow(ch, progress, onDownload)
                }
            }
        }
    }
}

@Composable
private fun DownloadPanel(progress: DownloadProgress) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            when (progress.state) {
                DownloadProgress.State.RUNNING -> {
                    Text("Mengunduh halaman ${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DownloadProgress.State.DONE -> {
                    Text(
                        "Selesai: ${progress.done}/${progress.total} halaman",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    progress.savedPath?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ComicRepository.sanitize(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DownloadProgress.State.FAILED -> Text(
                    progress.error ?: "Gagal",
                    color = MaterialTheme.colorScheme.error
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    progress: DownloadProgress,
    onDownload: (Chapter) -> Unit
) {
    val busy = progress.state == DownloadProgress.State.RUNNING
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy) { onDownload(chapter) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = { onDownload(chapter) }) {
                Icon(Icons.Default.Download, contentDescription = "Unduh chapter")
            }
        }
    }
}
