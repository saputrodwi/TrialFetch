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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import com.trialfetch.app.data.HistoryEntry
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.trialfetch.app.data.DownloadSettings
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

private const val EXIT_PRESS_WINDOW_MS = 2000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onHome = route == null || route == Routes.HOME

    var exitArmed by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    // Keluar 2x hanya di beranda. Di layar lain, back = kembali (nav default).
    BackHandler(enabled = onHome) {
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
                delay(EXIT_PRESS_WINDOW_MS)
                exitArmed = false
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
    val history by vm.history.collectAsStateWithLifecycle()
    val queueItems by vm.queueItems.collectAsStateWithLifecycle()
    val queuePaused by vm.queuePaused.collectAsStateWithLifecycle()
    val readerReq by vm.reader.collectAsStateWithLifecycle()
    val readerPages by vm.readerPages.collectAsStateWithLifecycle()
    val readerError by vm.readerError.collectAsStateWithLifecycle()
    val readerInitialPage by vm.readerInitialPage.collectAsStateWithLifecycle()
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
    val activeDownloads = queueItems.count { it.state == QueueItemState.ACTIVE }

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
                        when {
                            route == null || route == Routes.HOME -> "Trial Fetch"
                            route.startsWith(Routes.SERIES) -> series?.title ?: "Series"
                            route == Routes.SAVED -> "Tersimpan"
                            route == Routes.HISTORY -> "Riwayat"
                            route == Routes.DOWNLOADS -> "Unduhan"
                            route == Routes.SETTINGS -> "Pengaturan"
                            else -> "Trial Fetch"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (!onHome) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                BottomTab(
                    selected = onHome,
                    onClick = {
                        nav.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    icon = Icons.Default.Home,
                    label = "Beranda"
                )
                BottomTab(
                    selected = route == Routes.SAVED,
                    onClick = {
                        nav.navigate(Routes.SAVED) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                    icon = Icons.Default.Bookmark,
                    label = "Simpan"
                )
                BottomTab(
                    selected = route == Routes.HISTORY,
                    onClick = {
                        nav.navigate(Routes.HISTORY) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                    icon = Icons.Default.History,
                    label = "Riwayat"
                )
                BottomTab(
                    selected = route == Routes.DOWNLOADS,
                    onClick = {
                        nav.navigate(Routes.DOWNLOADS) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                    icon = Icons.Default.Download,
                    label = "Unduhan",
                    badgeCount = activeDownloads
                )
                BottomTab(
                    selected = route == Routes.SETTINGS,
                    onClick = {
                        nav.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                    icon = Icons.Default.Settings,
                    label = "Atur"
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        query = searchState.query,
                        onQueryChange = vm::onQueryChange,
                        onSearch = vm::doSearch,
                        searching = searchState.loading,
                        sources = vm.repo.availableSources,
                        source = searchState.source,
                        onSourceChange = vm::onSourceChange,
                        results = searchState.results,
                        error = seriesState.error,
                        urlInput = urlInput,
                        urlLoading = urlLoading,
                        onUrlChange = vm::onUrlChange,
                        onOpenUrl = vm::openFromUrl,
                        history = history,
                        onOpenHistory = vm::openHistory,
                        onPick = { r ->
                            nav.navigate(Routes.series(r.source.id, r.comicId))
                        },
                        storageGranted = storageGranted,
                        onReset = vm::clearSearch
                    )
                }
                composable(
                    "${Routes.SERIES}?${Routes.ARG_SOURCE}={${Routes.ARG_SOURCE}}&${Routes.ARG_COMIC_ID}={${Routes.ARG_COMIC_ID}}",
                    arguments = listOf(
                        navArgument(Routes.ARG_SOURCE) { type = NavType.StringType; defaultValue = "" },
                        navArgument(Routes.ARG_COMIC_ID) { type = NavType.StringType; defaultValue = "" }
                    )
                ) { entry ->
                    SeriesRoute(
                        sourceId = entry.arguments?.getString(Routes.ARG_SOURCE).orEmpty(),
                        comicId = entry.arguments?.getString(Routes.ARG_COMIC_ID).orEmpty(),
                        vm = vm,
                        storageGranted = storageGranted
                    )
                }
                composable(Routes.SAVED) {
                    SavedScreen(
                        items = bookmarks,
                        onOpen = { item ->
                            nav.navigate(Routes.series(item.source.id, item.comicId))
                        },
                        onRemove = vm::removeBookmark
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        items = history,
                        onOpen = vm::openHistory,
                        onRemove = vm::removeHistory,
                        onClear = vm::clearHistory
                    )
                }
                composable(Routes.DOWNLOADS) {
                    DownloadsScreen(
                        progress = downloadState,
                        onDismissProgress = vm::dismissProgress,
                        onCancelDownload = vm::cancelDownload,
                        queueItems = queueItems,
                        queuePaused = queuePaused,
                        onPauseAll = vm.queue::pauseAll,
                        onResumeAll = vm.queue::resumeAll,
                        onCancelItem = vm.queue::cancelItem,
                        onRemoveItem = vm.queue::removeItem,
                        onRetryItem = vm.queue::retryItem,
                        onClearFinished = vm.queue::clearFinished
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        onChange = vm::updateSettings,
                        onReset = { vm.updateSettings(DownloadSettings()) }
                    )
                }
            }
        }
    }

    // Reader menutupi seluruh layar di atas route apa pun.
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
                initialPage = readerInitialPage,
                onPageChange = { page, total ->
                    vm.recordReadProgress(req.series, req.chapter, page, total)
                },
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
private fun RowScope.BottomTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int = 0
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            if (badgeCount > 0) {
                BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                    Icon(icon, contentDescription = label)
                }
            } else {
                Icon(icon, contentDescription = label)
            }
        },
        // maxLines=1 eksplisit: di layar sempit label 5 tab bisa
        // bungkus ke 2 baris dan terlihat rusak.
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
            )
        }
    )
}

/** Bungkus layar series: muat dari argumen route, teruskan semua aksi. */
@Composable
private fun SeriesRoute(
    sourceId: String,
    comicId: String,
    vm: MainViewModel,
    storageGranted: Boolean
) {
    val seriesState by vm.series.collectAsStateWithLifecycle()
    val downloadState by vm.progress.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    val queueItems by vm.queueItems.collectAsStateWithLifecycle()

    LaunchedEffect(sourceId, comicId) {
        val src = Source.from(sourceId)
        if (src != null && comicId.isNotBlank()) {
            vm.openSeriesById(src, comicId)
        }
    }

    val info = seriesState.info
    when {
        seriesState.loading || info == null && seriesState.error == null -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        seriesState.error != null && info == null -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    seriesState.error ?: "Gagal memuat series",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        info != null -> {
            val infoNow = info
            // Refresh badge unduhan saat series dibuka dan tiap ada
            // chapter yang selesai diunduh.
            val doneSig = queueItems
                .filter { it.state == QueueItemState.DONE }
                .map { it.chapter.chapterId }
                .toSet()
            LaunchedEffect(infoNow.source, infoNow.comicId, doneSig) {
                vm.refreshDownloaded(infoNow)
            }
            val readMap = remember(history, infoNow.source, infoNow.comicId) {
                history.filter {
                    it.source == infoNow.source && it.comicId == infoNow.comicId
                }.associateBy { it.chapterId }
            }
            SeriesScreen(
                info = infoNow,
                progress = downloadState,
                isBookmarked = bookmarks.any { b -> b.source == infoNow.source && b.comicId == infoNow.comicId },
                onToggleBookmark = { vm.toggleBookmark(infoNow) },
                onDownload = { chapter ->
                    if (storageGranted) vm.download(infoNow, chapter)
                },
                onRead = { ch -> vm.openReader(infoNow, ch) },
                onEnqueue = { chapters ->
                    if (storageGranted) vm.enqueueChapters(infoNow, chapters)
                },
                downloadedIds = downloadedIds,
                readMap = readMap
            )
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
    onDownload: (Chapter) -> Unit,
    onRead: (Chapter) -> Unit,
    onEnqueue: (List<Chapter>) -> Unit,
    downloadedIds: Set<String>,
    readMap: Map<String, HistoryEntry>
) {
    val extra = LocalExtraColors.current
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    // true = terbaru di atas. Normalisasi selalu dari urutan menaik agar
    // konsisten antar-sumber (ada yang memberi lama-dulu, ada yang baru-dulu).
    var newestFirst by remember { mutableStateOf(true) }
    val shownChapters = remember(info.chapters, newestFirst) {
        val asc = info.chapters.mapIndexed { i, c -> Triple(c, c.chapterNumber, i) }
            .sortedWith(compareBy({ it.second ?: Int.MAX_VALUE }, { it.third }))
            .map { it.first }
        if (newestFirst) asc.asReversed() else asc
    }
    // Kalau pindah series, reset pilihan.
    LaunchedEffect(info.comicId, info.source) {
        selecting = false
        selectedIds = emptySet()
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SeriesHeader(info, extra, isBookmarked, onToggleBookmark) }
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
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { newestFirst = !newestFirst }) {
                        Icon(
                            if (newestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = if (newestFirst) "Urut lama ke baru" else "Urut baru ke lama"
                        )
                    }
                    if (selecting) {
                        IconButton(onClick = {
                            selecting = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Batalkan pilihan")
                        }
                    } else {
                        TextButton(onClick = { selecting = true }) {
                            Text("Pilih")
                        }
                    }
                }
            }
            if (selecting) item {
                // Baris aksi sendiri di bawah judul agar tombol-tombol
                // tidak berdesakan segaris dengan judul di layar sempit.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val allSelected = selectedIds.size == info.chapters.size
                    TextButton(onClick = {
                        selectedIds = if (allSelected) {
                            emptySet()
                        } else {
                            info.chapters.map { it.chapterId }.toSet()
                        }
                    }) {
                        Text(if (allSelected) "Kosongkan" else "Semua")
                    }
                    TextButton(
                        onClick = {
                            val picked = info.chapters.filter { it.chapterId in selectedIds }
                            if (picked.isNotEmpty()) onEnqueue(picked)
                            selecting = false
                            selectedIds = emptySet()
                        },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Text("Unduh (${selectedIds.size})")
                    }
                }
            }
            items(shownChapters, key = { it.chapterId }) { ch ->
                val entry = readMap[ch.chapterId]
                ChapterRow(
                    chapter = ch,
                    progress = progress,
                    downloaded = ComicRepository.sanitize(ch.title) in downloadedIds,
                    readText = entry?.let {
                        val f = it.fraction
                        if (f != null && it.totalPages > 0) "Terakhir: hal ${it.page}/${it.totalPages}"
                        else "Terakhir: ${it.chapterTitle}"
                    },
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

@Composable
private fun SeriesHeader(
    info: SeriesInfo,
    extra: com.trialfetch.app.ui.theme.ExtraColors,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit
) {
    BrutalCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        background = extra.card
    ) {
        Row(verticalAlignment = Alignment.Top) {
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
                if (info.synopsis.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    var synExpanded by remember { mutableStateOf(false) }
                    Text(
                        info.synopsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (synExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { synExpanded = !synExpanded }) {
                        Text(if (synExpanded) "Tutup" else "Sinopsis")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = extra.blue,
                    // Eksplisit (jangan andalkan bawaan): biru pastel di
                    // kedua mode sehingga teksnya harus gelap.
                    contentColor = extra.onAccent,
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
private fun ChapterRow(
    chapter: Chapter,
    progress: DownloadProgress,
    downloaded: Boolean,
    readText: String?,
    onDownload: (Chapter) -> Unit,
    onRead: (Chapter) -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: ((Chapter) -> Unit)? = null
) {
    // Spinner hanya untuk chapter yang sedang berjalan (state diset
    // seketika saat unduhan mulai, jadi tidak ada delay animasi).
    val busy = progress.state == DownloadProgress.State.RUNNING &&
        progress.chapterTitle == chapter.title
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                // Ketuk baris = baca langsung (tanpa tombol baca terpisah).
                // Mode pilih: ketuk = centang.
                if (onToggleSelect != null) {
                    Modifier.clickable {
                        if (selecting) onToggleSelect(chapter) else onRead(chapter)
                    }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Badge "sudah diunduh" — terdeteksi dari folder/zip lokal,
                // berlaku lintas sumber selama judulnya sama.
                if (downloaded && !busy) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Sudah diunduh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (readText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    readText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (!selecting) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
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
    initialPage: Int = 0,
    onPageChange: (Int, Int) -> Unit = { _, _ -> },
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
                // Lompat ke halaman terakhir dibaca (dari riwayat).
                LaunchedEffect(list.size) {
                    val target = initialPage.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                    if (target > 0) {
                        try {
                            hState.scrollToPage(target)
                        } catch (_: Exception) {
                        }
                        try {
                            vList.scrollToItem(target)
                        } catch (_: Exception) {
                        }
                    }
                }
                // Laporkan posisi agar tersimpan ke riwayat.
                val currentPage =
                    if (mode == ReaderMode.WEBTOON) vList.firstVisibleItemIndex else hState.currentPage
                LaunchedEffect(currentPage, list.size) {
                    onPageChange(currentPage, list.size)
                }
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
