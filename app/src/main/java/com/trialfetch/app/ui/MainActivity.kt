package com.trialfetch.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.ReaderPage
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.SeriesInfo
import com.trialfetch.app.core.Source
import com.trialfetch.app.data.ComicRepository
import com.trialfetch.app.data.DownloadProgress
import com.trialfetch.app.ui.theme.LocalExtraColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.trialfetch.app.data.ReaderMode
import com.trialfetch.app.data.ThemeMode
import com.trialfetch.app.data.SavedSeries
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.trialfetch.app.data.QueueItem
import com.trialfetch.app.data.QueueItemState
import com.trialfetch.app.ui.theme.BrutalCard
import com.trialfetch.app.ui.theme.PolkaDotBackground
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
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()

            // Tema mengikuti pilihan di Pengaturan, bukan hanya setelan sistem.
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            TrialFetchTheme(darkTheme = darkTheme) {
                PolkaDotBackground(Modifier.fillMaxSize()) {
                    Surface(
                        Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        AppRoot(vm)
                    }
                }
            }
        }
    }
}

private enum class Tab { SEARCH, SERIES, SAVED, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.SEARCH) }
    var exitArmed by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    // Tombol back sistem: dari Series/Pengaturan kembali ke daftar,
    // bukan keluar aplikasi. Di beranda perlu tekan 2x untuk keluar.
    BackHandler {
        when (tab) {
            Tab.SERIES -> {
                vm.closeSeries()
                tab = Tab.SEARCH
            }
            Tab.SETTINGS -> tab = Tab.SEARCH
            Tab.SAVED -> tab = Tab.SEARCH
            Tab.SEARCH -> {
                if (exitArmed) {
                    (context as? Activity)?.finish()
                } else {
                    exitArmed = true
                    Toast.makeText(
                        context,
                        "Tekan kembali sekali lagi untuk keluar",
                        Toast.LENGTH_SHORT
                    ).show()
                    uiScope.launch {
                        delay(2000)
                        exitArmed = false
                    }
                }
            }
        }
    }

    val seriesState by vm.series.collectAsStateWithLifecycle()
    val searchState by vm.search.collectAsStateWithLifecycle()
    val downloadState by vm.progress.collectAsStateWithLifecycle()
    val urlInput by vm.urlInput.collectAsStateWithLifecycle()
    val urlLoading by vm.urlLoading.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val queueItems by vm.queueItems.collectAsStateWithLifecycle()
    val queuePaused by vm.queuePaused.collectAsStateWithLifecycle()
    val readerReq by vm.reader.collectAsStateWithLifecycle()
    val readerPages by vm.readerPages.collectAsStateWithLifecycle()
    val readerError by vm.readerError.collectAsStateWithLifecycle()
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
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Kalau user menolak izin notifikasi, unduhan tetap jalan tapi
        // tanpa indikator — beri tahu sekali supaya tidak bingung.
        val denied = grants.entries.any { (perm, ok) ->
            perm == Manifest.permission.POST_NOTIFICATIONS && !ok
        }
        if (denied) {
            Toast.makeText(
                context,
                "Izin notifikasi ditolak — progres unduhan tidak akan tampil",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
                            Tab.SAVED -> "Tersimpan"
                            Tab.SETTINGS -> "Pengaturan"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (tab == Tab.SETTINGS || tab == Tab.SAVED) {
                        IconButton(onClick = { tab = Tab.SEARCH }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                },
                actions = {
                    if (tab == Tab.SEARCH) {
                        IconButton(onClick = { tab = Tab.SAVED }) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Tersimpan")
                        }
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
                        isBookmarked = bookmarks.any { b -> b.source == it.source && b.comicId == it.comicId },
                        onToggleBookmark = { vm.toggleBookmark(it) },
                        onDismissProgress = vm::dismissProgress,
                        onCancelDownload = vm::cancelDownload,
                        onRead = { ch -> vm.openReader(it, ch) },
                        onBack = {
                            vm.closeSeries()
                            tab = Tab.SEARCH
                        },
                        onDownload = { chapter ->
                            if (storageGranted) vm.download(it, chapter)
                        },
                        queueItems = queueItems,
                        queuePaused = queuePaused,
                        onEnqueue = { chapters ->
                            if (storageGranted) vm.enqueueChapters(it, chapters)
                        },
                        onPauseAll = vm.queue::pauseAll,
                        onResumeAll = vm.queue::resumeAll,
                        onCancelQueueItem = vm.queue::cancelItem,
                        onRemoveQueueItem = vm.queue::removeItem,
                        onRetryQueueItem = vm.queue::retryItem,
                        onClearFinished = vm.queue::clearFinished
                    )
                }

                Tab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onChange = vm::updateSettings
                )

                Tab.SAVED -> SavedScreen(
                    items = bookmarks,
                    onOpen = { item ->
                        vm.openSaved(item)
                        tab = Tab.SERIES
                    },
                    onRemove = vm::removeBookmark
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

    // Reader menutupi seluruh layar di atas tab apa pun.
    readerReq?.let { req ->
        val streaming = readerPages?.firstOrNull()?.uri?.startsWith("http") == true
        // Index chapter sesuai urutan daftar yang tampil di layar series.
        val chapters = req.series.chapters
        val idx = chapters.indexOfFirst { it.chapterId == req.chapter.chapterId }
        val prev = if (idx > 0) chapters[idx - 1] else null
        val next = if (idx >= 0 && idx < chapters.lastIndex) chapters[idx + 1] else null
        // Reset state pager/zoom tiap ganti chapter.
        key(req.chapter.chapterId) {
            ReaderScreen(
                title = req.chapter.title,
                pages = readerPages,
                error = readerError,
                isStreaming = streaming,
                mode = settings.readerMode,
                onModeChange = { vm.updateSettings(settings.copy(readerMode = it)) },
                prevChapter = prev,
                nextChapter = next,
                onNavigate = { vm.openReader(req.series, it) },
                onClose = vm::closeReader,
                onDownload = {
                    if (storageGranted) vm.download(req.series, req.chapter)
                },
                onRetry = { vm.openReader(req.series, req.chapter) }
            )
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
    // URL disembunyikan dulu supaya tampilan bersih; dibuka bila perlu.
    var urlExpanded by remember { mutableStateOf(false) }
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
                        TextButton(onClick = { urlExpanded = !urlExpanded }) {
                            Icon(
                                if (urlExpanded) Icons.Default.ExpandLess
                                else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (urlExpanded) "Sembunyikan input URL" else "Buka dari URL")
                        }
                    }
                    if (urlExpanded) {
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
    BrutalCard(
        modifier = Modifier.fillMaxWidth(),
        background = extra.card,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = r.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleSmall,
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
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onDismissProgress: () -> Unit,
    onCancelDownload: () -> Unit,
    onBack: () -> Unit,
    onDownload: (Chapter) -> Unit,
    onRead: (Chapter) -> Unit,
    queueItems: List<QueueItem>,
    queuePaused: Boolean,
    onEnqueue: (List<Chapter>) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelQueueItem: (Long) -> Unit,
    onRemoveQueueItem: (Long) -> Unit,
    onRetryQueueItem: (Long) -> Unit,
    onClearFinished: () -> Unit
) {
    val extra = LocalExtraColors.current
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    // Kalau pindah series, reset pilihan.
    LaunchedEffect(info.comicId, info.source) {
        selecting = false
        selectedIds = emptySet()
    }
    Column(Modifier.fillMaxSize()) {
        if (progress.state != DownloadProgress.State.IDLE) {
            DownloadPanel(progress, extra, onDismissProgress, onCancelDownload)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SeriesHeader(info, extra, isBookmarked, onToggleBookmark) }
            if (queueItems.isNotEmpty()) {
                item {
                    QueueCard(
                        items = queueItems,
                        paused = queuePaused,
                        onPauseAll = onPauseAll,
                        onResumeAll = onResumeAll,
                        onCancelItem = onCancelQueueItem,
                        onRemoveItem = onRemoveQueueItem,
                        onRetryItem = onRetryQueueItem,
                        onClearFinished = onClearFinished
                    )
                }
            }
            item {
                // Baris aksi daftar chapter: pilih banyak vs unduh biasa.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Chapter (${info.chapters.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (selecting) {
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == info.chapters.size) {
                                emptySet()
                            } else {
                                info.chapters.map { it.chapterId }.toSet()
                            }
                        }) {
                            Text(if (selectedIds.size == info.chapters.size) "Batal semua" else "Semua")
                        }
                        TextButton(onClick = {
                            val picked = info.chapters.filter { it.chapterId in selectedIds }
                            if (picked.isNotEmpty()) onEnqueue(picked)
                            selecting = false
                            selectedIds = emptySet()
                        }) {
                            Text("Unduh (${selectedIds.size})")
                        }
                        TextButton(onClick = {
                            selecting = false
                            selectedIds = emptySet()
                        }) {
                            Text("Batal")
                        }
                    } else {
                        TextButton(onClick = { selecting = true }) {
                            Text("Pilih")
                        }
                    }
                }
            }
            items(info.chapters, key = { it.chapterId }) { ch ->
                ChapterRow(
                    chapter = ch,
                    progress = progress,
                    onDownload = onDownload,
                    onRead = onRead,
                    selecting = selecting,
                    selected = ch.chapterId in selectedIds,
                    onToggleSelect = { c ->
                        selectedIds = if (c.chapterId in selectedIds) {
                            selectedIds - c.chapterId
                        } else {
                            selectedIds + c.chapterId
                        }
                    }
                )
            }
        }
    }
}

/**
 * Kartu antrian unduhan: daftar chapter yang menunggu/berjalan/selesai,
 * dengan jeda global, batal per item, ulangi yang gagal, dan bersihkan
 * yang sudah terminal.
 */
@Composable
private fun QueueCard(
    items: List<QueueItem>,
    paused: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelItem: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onRetryItem: (Long) -> Unit,
    onClearFinished: () -> Unit
) {
    val extra = LocalExtraColors.current
    val activeCount = items.count { it.state == QueueItemState.ACTIVE }
    val queuedCount = items.count { it.state == QueueItemState.QUEUED }
    val doneCount = items.count { it.state == QueueItemState.DONE }
    BrutalCard(modifier = Modifier.fillMaxWidth(), background = extra.card) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Antrian ($doneCount/${items.size} selesai)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (paused) {
                TextButton(onClick = onResumeAll) { Text("Lanjut") }
            } else {
                TextButton(onClick = onPauseAll) { Text("Jeda") }
            }
            TextButton(onClick = onClearFinished) { Text("Bersih") }
        }
        Spacer(Modifier.height(8.dp))
        if (paused && (activeCount > 0 || queuedCount > 0)) {
            Text(
                "Dijeda — unduhan lanjut dari gambar terakhir saat dilanjutkan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        items.forEach { item ->
            QueueRow(
                item = item,
                onCancel = { onCancelItem(item.id) },
                onRemove = { onRemoveItem(item.id) },
                onRetry = { onRetryItem(item.id) }
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (item.state) {
                        QueueItemState.QUEUED -> "Menunggu"
                        QueueItemState.ACTIVE ->
                            if (item.total > 0) "Mengunduh ${item.done}/${item.total}" else "Mengunduh…"
                        QueueItemState.DONE -> "Selesai"
                        QueueItemState.FAILED -> "Gagal${item.error?.let { ": $it" } ?: ""}"
                        QueueItemState.CANCELLED -> "Dibatalkan"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (item.state) {
                QueueItemState.ACTIVE -> TextButton(onClick = onCancel) { Text("Batal") }
                QueueItemState.QUEUED -> TextButton(onClick = onRemove) { Text("Hapus") }
                QueueItemState.FAILED -> {
                    TextButton(onClick = onRetry) { Text("Ulangi") }
                    TextButton(onClick = onRemove) { Text("Hapus") }
                }
                QueueItemState.DONE, QueueItemState.CANCELLED ->
                    TextButton(onClick = onRemove) { Text("Hapus") }
            }
        }
        if (item.state == QueueItemState.ACTIVE && item.total > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (item.done.toFloat() / item.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    info: SeriesInfo,
    extra: com.trialfetch.app.ui.theme.ExtraColors,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        info.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark
                            else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Hapus dari simpanan" else "Simpan series"
                        )
                    }
                }
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
    extra: com.trialfetch.app.ui.theme.ExtraColors,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    // Teks ditulis dengan warna gelap eksplisit, BUKAN warisan onSurface.
    // Surface kustom tidak menghitung contentColor otomatis, jadi di dark
    // mode teks ikut terang di atas kuning terang dan tidak terbaca.
    // onAccent gelap di kedua mode sehingga aman dipakai di sini.
    val ink = extra.onAccent
    Surface(
        color = extra.yellow,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            3.dp, MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (progress.state) {
                        DownloadProgress.State.RUNNING ->
                            "Mengunduh halaman ${progress.done}/${progress.total}"
                        DownloadProgress.State.DONE ->
                            "Selesai: ${progress.done}/${progress.total} halaman"
                        DownloadProgress.State.FAILED ->
                            progress.error ?: "Gagal"
                        DownloadProgress.State.CANCELLED ->
                            "Unduhan dibatalkan (${progress.done}/${progress.total} halaman tersimpan)"
                        else -> ""
                    },
                    Modifier.weight(1f),
                    color = if (progress.state == DownloadProgress.State.FAILED)
                        MaterialTheme.colorScheme.onErrorContainer else ink,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (progress.state == DownloadProgress.State.RUNNING) {
                    TextButton(onClick = onCancel) {
                        Text("Batal", color = ink)
                    }
                } else {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = ink)
                    }
                }
            }
            when (progress.state) {
                DownloadProgress.State.RUNNING -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = ink,
                        trackColor = ink.copy(alpha = 0.25f)
                    )
                }
                DownloadProgress.State.DONE -> {
                    progress.savedPath?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            color = ink.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
@Composable
private fun ChapterRow(
    chapter: Chapter,
    progress: DownloadProgress,
    onDownload: (Chapter) -> Unit,
    onRead: (Chapter) -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: ((Chapter) -> Unit)? = null
) {
    // Hanya chapter yang sedang diunduh yang tampil spinner. Sebelumnya
    // pemeriksaannya global, jadi mengunduh satu chapter membuat SEMUA baris
    // terlihat sedang berjalan.
    val busy = progress.state == DownloadProgress.State.RUNNING &&
        progress.chapterTitle == chapter.title
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (selecting && onToggleSelect != null) {
                    Modifier.clickable { onToggleSelect(chapter) }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect?.invoke(chapter) }
            )
            Spacer(Modifier.width(4.dp))
        }
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
        } else if (!selecting) {
            IconButton(onClick = { onRead(chapter) }) {
                Icon(Icons.Default.MenuBook, contentDescription = "Baca ${chapter.title}")
            }
            IconButton(onClick = { onDownload(chapter) }) {
                Icon(Icons.Default.Download, contentDescription = "Unduh ${chapter.title}")
            }
        }
    }
}

/** Daftar series yang disimpan pengguna. Ketuk untuk membuka, ikon tong untuk menghapus. */
@Composable
private fun SavedScreen(
    items: List<SavedSeries>,
    onOpen: (SavedSeries) -> Unit,
    onRemove: (SavedSeries) -> Unit
) {
    val extra = LocalExtraColors.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "Belum ada series tersimpan.\nBuka series lalu ketuk ikon bookmark.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.key() }) { item ->
            BrutalCard(
                modifier = Modifier.fillMaxWidth(),
                background = extra.card,
                onClick = { onOpen(item) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = item.coverUrl.ifBlank { null },
                        contentDescription = "Sampul ${item.title}",
                        modifier = Modifier
                            .size(width = 48.dp, height = 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.source.displayName +
                                (if (item.author.isNotBlank()) " · ${item.author}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus ${item.title}")
                    }
                }
            }
        }
    }
}

/**
 * Pembaca komik bawaan: fullscreen imersif, geser per halaman atau gulir
 * vertikal ala webtoon, cubit untuk zoom, shortcut chapter sebelum/berikut.
 *
 * Membaca file lokal hasil unduhan (folder atau hasil ekstrak ZIP),
 * jadi bisa offline penuh. [pages] null = masih dimuat.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReaderScreen(
    title: String,
    pages: List<ReaderPage>?,
    error: String?,
    isStreaming: Boolean,
    mode: ReaderMode,
    onModeChange: (ReaderMode) -> Unit,
    prevChapter: Chapter?,
    nextChapter: Chapter?,
    onNavigate: (Chapter) -> Unit,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit
) {
    BackHandler(onBack = onClose)
    val view = LocalView.current
    // Fullscreen imersif: sembunyikan status bar + navigation bar HP selama
    // membaca agar jadi full konten aplikasi; kembalikan saat keluar. User
    // tetap bisa memunculkan sesaat dengan swipe dari tepi layar.
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Kunci geser pager saat ada halaman yang di-zoom, supaya cubit
    // tidak malah pindah halaman.
    var pagerLocked by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            error != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    error,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onRetry) {
                    Text("Coba lagi", color = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDownload) {
                    Text("Unduh saja", color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onClose) {
                    Text("Tutup", color = Color.White.copy(alpha = 0.7f))
                }
            }
            pages == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            pages.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Tidak ada gambar untuk dibaca.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onClose) {
                    Text("Tutup", color = Color.White.copy(alpha = 0.7f))
                }
            }
            else -> {
                val list = pages
                val hState = rememberPagerState(pageCount = { list.size })
                val vList = rememberLazyListState()
                val setLock: (Boolean) -> Unit = { locked ->
                    if (locked != pagerLocked) pagerLocked = locked
                }
                if (mode == ReaderMode.WEBTOON) {
                    // Aliran kontinu tanpa sekat: tiap gambar selebar layar
                    // dengan tinggi mengikuti aspek aslinya. VerticalPager
                    // TIDAK dipakai di sini karena memaksa tiap halaman
                    // setinggi viewport sehingga gambar pendek mengambang
                    // dengan pita hitam di atas/bawah.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !pagerLocked
                    ) {
                        items(list, key = { it.uri }) { item ->
                            ZoomablePage(
                                item = item,
                                active = true,
                                flow = true,
                                onLockChange = setLock
                            )
                        }
                    }
                } else {
                    HorizontalPager(
                        state = hState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !pagerLocked
                    ) { page ->
                        ZoomablePage(
                            item = list[page],
                            active = hState.currentPage == page,
                            onLockChange = setLock
                        )
                    }
                }
                val currentPage =
                    if (mode == ReaderMode.WEBTOON) vList.firstVisibleItemIndex else hState.currentPage
                // Bilah atas.
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                    }
                    Text(
                        title,
                        Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isStreaming) {
                        Text(
                            "online",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Unduh chapter", tint = Color.White)
                    }
                    Text(
                        "${currentPage + 1}/${list.size}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                // Bilah bawah: chapter sebelum/berikut + ganti mode baca.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { prevChapter?.let(onNavigate) },
                        enabled = prevChapter != null
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Chapter sebelumnya",
                            tint = if (prevChapter != null) Color.White
                            else Color.White.copy(alpha = 0.3f)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        onModeChange(
                            if (mode == ReaderMode.WEBTOON) ReaderMode.PAGED
                            else ReaderMode.WEBTOON
                        )
                    }) {
                        Text(
                            if (mode == ReaderMode.WEBTOON) "Halaman" else "Webtoon",
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { nextChapter?.let(onNavigate) },
                        enabled = nextChapter != null
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Chapter berikutnya",
                            tint = if (nextChapter != null) Color.White
                            else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Satu halaman yang bisa dicubit-zoom. Dipakai ulang oleh pager
 * horizontal maupun aliran vertikal webtoon.
 *
 * [flow] = true berarti gambar mengisi lebar layar dengan tinggi
 * mengikuti aspek aslinya (tanpa pita kosong), sehingga beberapa gambar
 * tersambung mulus seperti webtoon.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ZoomablePage(
    item: ReaderPage,
    active: Boolean,
    onLockChange: (Boolean) -> Unit,
    flow: Boolean = false
) {
    val context = LocalContext.current
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
    // Aspek asli diketahui setelah gambar termuat; sebelum itu pakai
    // placeholder ramping agar tidak ada lompatan besar.
    var aspect by remember(item.uri) { mutableStateOf<Float?>(null) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        var next = (scale * zoom).coerceIn(1f, 4f)
        // Snap: cegah drift float (mis. 1,0003 dari jitter jari)
        // yang mengunci gulir selamanya.
        if (next < 1.02f) next = 1f
        scale = next
        offset = if (next <= 1f) Offset.Zero else offset + pan
        onLockChange(next > 1f)
    }
    // URL remote dimuat dengan header penangkal hotlink (sama seperti
    // unduhan); file lokal langsung.
    val model = remember(item.uri) {
        if (item.uri.startsWith("http")) {
            ImageRequest.Builder(context).data(item.uri).apply {
                for ((k, v) in item.headers) addHeader(k, v)
            }.build()
        } else {
            item.uri
        }
    }
    val sizeMod = if (flow) {
        val a = aspect
        if (a != null && a > 0f) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(a)
        } else {
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 240.dp)
        }
    } else {
        Modifier.fillMaxSize()
    }
    AsyncImage(
        model = model,
        contentDescription = "Halaman",
        contentScale = if (flow) ContentScale.FillWidth else ContentScale.Fit,
        onSuccess = {
            val s = it.painter.intrinsicSize
            if (s.width.isFinite() && s.height.isFinite() && s.height > 0f) {
                aspect = s.width / s.height
            }
        },
        modifier = sizeMod
            .graphicsLayer(
                scaleX = if (active) scale else 1f,
                scaleY = if (active) scale else 1f,
                translationX = if (active) offset.x else 0f,
                translationY = if (active) offset.y else 0f
            )
            // canPan (pola dokumen resmi): saat belum zoom, pan TIDAK
            // dikonsumsi sehingga pager/kolom menerima geseran. Cubit-zoom
            // tetap jalan karena zoom bukan pan.
            .transformable(
                state = transform,
                canPan = { scale > 1f }
            )
    )
}
