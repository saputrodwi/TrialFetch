package com.trialfetch.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.SeriesInfo
import com.trialfetch.app.core.Source
import com.trialfetch.app.data.ComicRepository
import com.trialfetch.app.data.DownloadProgress
import com.trialfetch.app.ui.theme.LocalExtraColors
import com.trialfetch.app.ui.theme.TrialFetchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() harus dipanggil sebelum super.onCreate supaya
        // layar splash Android 12+ langsung übernimmt dan tidak ada jeda
        // buatan di atasnya.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrialFetchTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Tab { SEARCH, SERIES, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.SEARCH) }

    val seriesState by vm.series.collectAsStateWithLifecycle()
    val searchState by vm.search.collectAsStateWithLifecycle()
    val downloadState by vm.progress.collectAsStateWithLifecycle()
    val urlInput by vm.urlInput.collectAsStateWithLifecycle()
    val urlLoading by vm.urlLoading.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    // Izin penyimpanan: dibutuhkan hanya di Android 9 ke bawah. Android 10+
    // menulis lewat MediaStore tanpa izin, jadi di sana tidak ada dialog.
    var storageGranted by remember { mutableStateOf(
        StoragePermission.isGranted(context)
    ) }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { storageGranted = StoragePermission.isGranted(context) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* hasilnya tidak dipakai; notifikasi tetap jalan bila diizinkan */ }

    LaunchedEffect(Unit) {
        StoragePermission.required()?.let { storageLauncher.launch(it) }
        StoragePermission.notificationRequired()?.let { notifLauncher.launch(it) }
    }

    val series = seriesState.info
    LaunchedEffect(series) {
        if (series != null) tab = Tab.SERIES
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Text(
                        when (tab) {
                            Tab.SEARCH -> "Trial Fetch"
                            Tab.SERIES -> series?.title ?: "Trial Fetch"
                            Tab.SETTINGS -> "Pengaturan"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (tab == Tab.SETTINGS) {
                        IconButton(onClick = { tab = Tab.SEARCH }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
                actions = {
                    if (tab == Tab.SEARCH) {
                        IconButton(onClick = { tab = Tab.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.SEARCH -> SearchScreen(
                    state = searchState,
                    sources = vm.repo.availableSources,
                    urlInput = urlInput,
                    urlLoading = urlLoading,
                    storageGranted = storageGranted,
                    onQueryChange = vm::onQueryChange,
                    onSourceChange = vm::onSourceChange,
                    onSearch = vm::doSearch,
                    onPick = vm::openSeries,
                    onUrlChange = vm::onUrlChange,
                    onOpenUrl = vm::openFromUrl
                )

                Tab.SERIES -> series?.let {
                    SeriesScreen(
                        info = it,
                        progress = downloadState,
                        onBack = {
                            vm.closeSeries()
                            tab = Tab.SEARCH
                        },
                        onDownload = { chapter ->
                            if (storageGranted) vm.download(it, chapter)
                        }
                    )
                }

                Tab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onChange = vm::updateSettings
                )
            }

            // Error dari URL manual muncul di layar pencarian.
            if (seriesState.error != null && tab == Tab.SEARCH) {
                ErrorBanner(seriesState.error!!, extra.pinkDeep, extra.card) {
                    vm.closeSeries()
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, accent: androidx.compose.ui.graphics.Color,
                        card: androidx.compose.ui.graphics.Color, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            color = card,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 3.dp
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message,
                    Modifier.weight(1f),
                    color = accent,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    state: SearchUiState,
    sources: List<Source>,
    urlInput: String,
    urlLoading: Boolean,
    storageGranted: Boolean,
    onQueryChange: (String) -> Unit,
    onSourceChange: (Source) -> Unit,
    onSearch: () -> Unit,
    onPick: (SearchResult) -> Unit,
    onUrlChange: (String) -> Unit,
    onOpenUrl: () -> Unit
) {
    val extra = LocalExtraColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (!storageGranted) {
            Surface(
                color = extra.yellow,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    "Izin penyimpanan belum diberikan — unduhan tidak bisa " +
                        "menulis ke folder Download.",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari judul komik") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            trailingIcon = {
                IconButton(onClick = onSearch, enabled = !state.loading) {
                    Icon(Icons.Default.Search, contentDescription = "Cari")
                }
            }
        )

        Spacer(Modifier.height(10.dp))
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
            else -> {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        UrlInputCard(
                            value = urlInput,
                            loading = urlLoading,
                            accent = extra.blue,
                            card = extra.card,
                            onChange = onUrlChange,
                            onOpen = onOpenUrl
                        )
                    }
                    items(state.results, key = { it.comicId }) { r ->
                        ResultRow(r) { onPick(r) }
                    }
                    if (!state.loading && state.results.isEmpty()) {
                        item {
                            Text(
                                "Cari judul, atau tempel link series di atas.\n" +
                                    "Contoh: 被校花分手后，我直接武道通神",
                                Modifier.padding(top = 24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlInputCard(
    value: String,
    loading: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    card: androidx.compose.ui.graphics.Color,
    onChange: (String) -> Unit,
    onOpen: () -> Unit
) {
    Surface(color = card, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text("Tempel URL series", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("manwang.net/book/…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onOpen, enabled = !loading && value.isNotBlank()) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Link, contentDescription = "Buka URL")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    val extra = LocalExtraColors.current
    Surface(
        onClick = onClick,
        color = extra.card,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = r.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(8.dp)),
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
    val extra = LocalExtraColors.current
    Column(Modifier.fillMaxSize()) {
        if (progress.state != DownloadProgress.State.IDLE) {
            DownloadPanel(progress, extra)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SeriesHeader(info, extra) }
            items(info.chapters, key = { it.chapterId }) { ch ->
                ChapterRow(ch, progress, onDownload)
            }
        }
    }
}

@Composable
private fun SeriesHeader(info: SeriesInfo, extra: com.trialfetch.app.ui.theme.ExtraColors) {
    Surface(
        color = extra.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = info.coverUrl,
                contentDescription = "Sampul ${info.title}",
                modifier = Modifier
                    .size(width = 96.dp, height = 128.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.author.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Penulis", info.author)
                }
                if (info.status.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    InfoRow("Status", info.status)
                }
                if (info.latestChapterTitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    InfoRow("Terbaru", info.latestChapterTitle)
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = extra.blue,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${info.chapters.size} chapter",
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DownloadPanel(
    progress: DownloadProgress,
    extra: com.trialfetch.app.ui.theme.ExtraColors
) {
    Surface(
        color = extra.yellow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            when (progress.state) {
                DownloadProgress.State.RUNNING -> {
                    Text(
                        "Mengunduh halaman ${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        fontWeight = FontWeight.SemiBold
                    )
                    progress.savedPath?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                Icon(Icons.Default.Download, contentDescription = "Unduh ${chapter.title}")
            }
        }
    }
}
