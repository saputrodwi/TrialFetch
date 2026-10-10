package com.trialfetch.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.trialfetch.app.ui.theme.hardShadow
import com.trialfetch.app.ui.theme.TrialFetchTheme

class MainActivity : ComponentActivity() {
    /** Target layar dari ketukan notifikasi; dikonsumsi sekali oleh AppRoot. */
    private var deepLinkTarget by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkTarget = intent.getStringExtra(
            com.trialfetch.app.data.DownloadNotifier.EXTRA_OPEN
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() harus dipanggil sebelum super.onCreate supaya
        // layar splash Android 12+ langsung mengambil alih dan tidak ada
        // jeda buatan di atasnya. Nilainya tidak dipakai — cukup dipanggil.
        installSplashScreen()
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
                        AppRoot(
                            vm = vm,
                            deepLink = intent.getStringExtra(
                                com.trialfetch.app.data.DownloadNotifier.EXTRA_OPEN
                            ) ?: deepLinkTarget,
                            onDeepLinkConsumed = { deepLinkTarget = null }
                        )
                    }
                }
            }
        }
    }
}

private const val EXIT_PRESS_WINDOW_MS = 2000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    vm: MainViewModel = viewModel(),
    deepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
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
    val cloudflare by vm.cloudflare.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    // Izin penyimpanan: dibutuhkan hanya di Android 9 ke bawah. Android 10+
    // menulis lewat MediaStore tanpa izin, jadi di sana tidak ada dialog.
    var storageGranted by remember { mutableStateOf(
        StoragePermission.isGranted(context)
    ) }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { storageGranted = StoragePermission.isGranted(context) }

    // Cadangan via file picker sistem (SAF): bisa simpan ke Drive/WA/
    // Bluetooth lalu pulihkan di HP baru. Tidak terkunci di folder app.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(vm.backupJson())
            } ?: throw IllegalStateException("tak tertulis")
            Toast.makeText(context, "Cadangan tersimpan", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Gagal menyimpan cadangan", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("tak terbaca")
            when (val n = vm.restoreJson(text)) {
                -1 -> Toast.makeText(context, "File cadangan rusak", Toast.LENGTH_LONG).show()
                0 -> Toast.makeText(context, "Tidak ada entri baru di cadangan", Toast.LENGTH_LONG).show()
                else -> Toast.makeText(context, "Pulih $n entri cadangan", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Gagal membaca file", Toast.LENGTH_LONG).show()
        }
    }

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

    // Ketukan aksi notifikasi mendarat di sini: lompat ke layar tujuan sekali.
    LaunchedEffect(deepLink) {
        when (deepLink) {
            com.trialfetch.app.data.DownloadNotifier.OPEN_DOWNLOADS ->
                nav.navigate(Routes.DOWNLOADS) { launchSingleTop = true }
            com.trialfetch.app.data.DownloadNotifier.OPEN_SAVED ->
                nav.navigate(Routes.SAVED) {
                    popUpTo(Routes.HOME)
                    launchSingleTop = true
                }
            null -> return@LaunchedEffect
            else -> return@LaunchedEffect
        }
        onDeepLinkConsumed()
    }

    val series = seriesState.info
    val activeDownloads = queueItems.count { it.state == QueueItemState.ACTIVE }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // TopBar brutal: permukaan surface + garis tinta bawah 3dp,
            // judul Baloo2 dengan title-shadow khas web.
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        com.trialfetch.app.ui.theme.BrutalTitle(
                            text = when {
                                route == null || route == Routes.HOME -> "Trial Fetch"
                                route.startsWith(Routes.SERIES) -> series?.title ?: "Series"
                                route == Routes.SAVED -> "Tersimpan"
                                route == Routes.HISTORY -> "Riwayat"
                                route == Routes.DOWNLOADS -> "Unduhan"
                                route == Routes.SETTINGS -> "Pengaturan"
                                else -> "Trial Fetch"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (!onHome) {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                            }
                        }
                    },
                    // Aksi cari di Beranda sesuai mockup (ikon 🔍 kanan atas).
                    actions = {
                        if (onHome) {
                            IconButton(onClick = vm::doSearch) {
                                Icon(Icons.Default.Search, contentDescription = "Cari")
                            }
                        }
                    }
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onBackground)
                )
            }
        },
        bottomBar = {
            // BottomNav brutal: garis tinta atas 3dp, bukan Material polos.
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onBackground)
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
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
                        allSelected = searchState.allSources,
                        onAllSources = vm::searchAllSources,
                        results = searchState.results,
                        error = searchState.error,
                        urlInput = urlInput,
                        urlLoading = urlLoading,
                        onUrlChange = vm::onUrlChange,
                        onOpenUrl = {
                            vm.openFromUrl { info ->
                                nav.navigate(Routes.series(info.source.id, info.comicId))
                            }
                        },
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
                    val updates by vm.bookmarkUpdates.collectAsStateWithLifecycle()
                    val checking by vm.updateChecking.collectAsStateWithLifecycle()
                    SavedScreen(
                        items = bookmarks,
                        onOpen = { item ->
                            nav.navigate(Routes.series(item.source.id, item.comicId))
                        },
                        onRemove = vm::removeBookmark,
                        updates = updates,
                        checking = checking,
                        onCheckUpdates = vm::checkBookmarkUpdates,
                        onClearUpdates = vm::clearBookmarkUpdates
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
                        onReset = { vm.updateSettings(DownloadSettings()) },
                        onExportBackup = { exportLauncher.launch("trialfetch-backup.json") },
                        onImportBackup = { importLauncher.launch(arrayOf("application/json")) }
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
                seriesTitle = req.series.title,
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
                onOpenSeries = {
                    vm.closeReader()
                    nav.navigate(Routes.series(req.series.source.id, req.series.comicId))
                },
                onDownload = {
                    if (storageGranted) vm.download(req.series, req.chapter)
                },
                onRetry = { vm.openReader(req.series, req.chapter) }
            )
        }
    }

    // Layar verifikasi Cloudflare menutupi seluruh layar di atas route apa pun.
    cloudflare?.let { cf ->
        key(cf.url) {
            CloudflareVerifyScreen(
                url = cf.url,
                userAgent = vm.userAgent,
                onDone = vm::onCloudflareDone,
                onClose = vm::closeCloudflareVerify
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
        // labelSmall 11sp: labelLarge 14sp terbukti kepotong ("Beran…",
        // "Riway…") di 5 tab layar sempit. Selalu satu baris.
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        },
        // Indikator kuning brutal, bukan pil ungu bawaan Material.
        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
            indicatorColor = LocalExtraColors.current.yellow
        )
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
    // Nama folder unduhan bisa beda kapitalisasi dengan judul chapter yang
    // tampil sekarang (judul situs berubah antar kunjungan), jadi dibandingkan
    // dalam bentuk huruf kecil semua.
    val downloadedKey = remember(downloadedIds) {
        downloadedIds.map { it.lowercase() }.toSet()
    }
    // Kalau pindah series, reset pilihan.
    LaunchedEffect(info.comicId, info.source) {
        selecting = false
        selectedIds = emptySet()
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item { SeriesHeader(info, extra, isBookmarked, onToggleBookmark) }
            // Seluruh daftar chapter dalam satu kartu brutal (bukan
            // baris-baris polos), lengkap dengan sekat antar baris.
            item {
                BrutalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    background = extra.cardLight
                ) {
                    // Baris aksi daftar chapter: pilih banyak vs unduh biasa.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Chapter (${info.chapters.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ReaderCircleButton(onClick = { newestFirst = !newestFirst }) {
                            Icon(
                                if (newestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = if (newestFirst) "Urut lama ke baru" else "Urut baru ke lama",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (selecting) {
                            ReaderCircleButton(onClick = {
                                selecting = false
                                selectedIds = emptySet()
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Batalkan pilihan",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            com.trialfetch.app.ui.theme.BrutalButton(
                                text = "Pilih",
                                onClick = { selecting = true }
                            )
                        }
                    }
                    if (selecting) {
                        Spacer(Modifier.height(8.dp))
                        // Baris aksi sendiri di bawah judul agar tombol-tombol
                        // tidak berdesakan segaris dengan judul di layar sempit.
                        Row(
                            Modifier.fillMaxWidth(),
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
                            Spacer(Modifier.width(8.dp))
                            com.trialfetch.app.ui.theme.BrutalButton(
                                text = "Unduh (${selectedIds.size})",
                                onClick = {
                                    val picked = info.chapters.filter { it.chapterId in selectedIds }
                                    if (picked.isNotEmpty()) onEnqueue(picked)
                                    selecting = false
                                    selectedIds = emptySet()
                                },
                                enabled = selectedIds.isNotEmpty()
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    shownChapters.forEachIndexed { index, ch ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 2.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                            )
                        }
                        val entry = readMap[ch.chapterId]
                ChapterRow(
                    chapter = ch,
                    progress = progress,
                    downloaded = ComicRepository.sanitize(ch.title).lowercase() in downloadedKey,
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
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sumber: ${info.source.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.onBackground,
                            RoundedCornerShape(8.dp)
                        )
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
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
        progress.chapterId != null && progress.chapterId == chapter.chapterId
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
            .padding(vertical = 8.dp),
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
            ReaderCircleButton(onClick = { onDownload(chapter) }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Unduh ${chapter.title}",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Daftar series yang disimpan pengguna. Ketuk untuk membuka, ikon tong untuk menghapus. */
@Composable
private fun SavedScreen(
    items: List<SavedSeries>,
    onOpen: (SavedSeries) -> Unit,
    onRemove: (SavedSeries) -> Unit,
    updates: List<String> = emptyList(),
    checking: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    onClearUpdates: () -> Unit = {}
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
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCheckUpdates, enabled = !checking) {
                    Text(if (checking) "Mengecek…" else "Cek update")
                }
                Spacer(Modifier.weight(1f))
                if (updates.isNotEmpty()) {
                    TextButton(onClick = onClearUpdates) { Text("Bersihkan") }
                }
            }
            if (updates.isNotEmpty()) {
                BrutalCard(
                    modifier = Modifier.fillMaxWidth(),
                    background = extra.yellow
                ) {
                    Text(
                        "Chapter baru:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    updates.forEach { u ->
                        Text(
                            "• $u",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.onAccent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
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
    seriesTitle: String = "",
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
    onOpenSeries: () -> Unit,
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
    // Ala Mihon: ketuk konten untuk sembunyikan/tampilkan bilah atas
    // + bawah. Tap tidak dikonsumsi gambar (lihat ZoomablePage) jadi
    // cubit/geser tetap jalan, double-tap zoom juga akur.
    var menusVisible by remember { mutableStateOf(true) }
    val readerScope = rememberCoroutineScope()
    val readerBg = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .background(readerBg)
            .pointerInput(Unit) {
                // Detektor tahan-banting: requireUnconsumed=false sehingga
                // ketuk TERDETEKSI walau gambar/pager mengonsumsi duluan.
                // waitForUpOrCancellation = null saat geser/cubit (sudah
                // dikonsumsi scroll/zoom) sehingga hanya ketuk murni yang
                // toggle. Double-tap = toggle 2x (netral) + reset zoom.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation()
                    if (up != null) menusVisible = !menusVisible
                }
            }
    ) {
        when {
            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                com.trialfetch.app.ui.theme.BrutalCard(
                    modifier = Modifier.fillMaxWidth(),
                    background = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    com.trialfetch.app.ui.theme.BrutalButton(
                        text = "Coba lagi",
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Unduh saja")
                    }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("Tutup")
                    }
                }
            }
            pages == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                com.trialfetch.app.ui.theme.BrutalCard(
                    background = MaterialTheme.colorScheme.surface
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Memuat halaman…",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
            pages.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                com.trialfetch.app.ui.theme.BrutalCard(
                    modifier = Modifier.fillMaxWidth(),
                    background = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        "Tidak ada gambar untuk dibaca.",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(16.dp))
                    com.trialfetch.app.ui.theme.BrutalButton(
                        text = "Tutup",
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        // WAJIB: tanpa state ini LazyColumn memakai state
                        // internal sendiri sehingga firstVisibleItemIndex
                        // (counter + riwayat) macet di 0 selamanya.
                        state = vList,
                        userScrollEnabled = !pagerLocked
                    ) {
                        items(list, key = { it.uri }) { item ->
                            ZoomablePage(
                                item = item,
                                active = true,
                                flow = true,
                                onLockChange = setLock,
                                onImageTap = { menusVisible = !menusVisible }
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
                            onLockChange = setLock,
                            onImageTap = { menusVisible = !menusVisible }
                        )
                    }
                }
                // Bilah atas full-width sesuai mockup Stitch yang
                // disetujui: surface krem + garis tinta bawah 2dp.
                // Sembunyi/tampil mengikuti ketukan ala Mihon.
                var menuOpen by remember { mutableStateOf(false) }
                AnimatedVisibility(
                    visible = menusVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it }
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ReaderCircleButton(onClick = onClose) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                com.trialfetch.app.ui.theme.BrutalTitle(
                                    text = seriesTitle.ifBlank { title },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    (if (seriesTitle.isNotBlank()) title else "Membaca") +
                                        if (isStreaming) " • online" else "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            ReaderCircleButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Menu baca",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                shape = RoundedCornerShape(12.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.onBackground,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .hardShadow(
                                        LocalExtraColors.current.shadow,
                                        3.dp,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Unduh chapter") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onDownload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Daftar chapter") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.List,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onOpenSeries()
                                    }
                                )
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onBackground)
                        )
                    }
                }
                // Bilah bawah ala Mihon: slider halaman + tombol navigasi.
                AnimatedVisibility(
                    visible = menusVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onBackground)
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ReaderCircleButton(
                                    onClick = { prevChapter?.let(onNavigate) },
                                    enabled = prevChapter != null
                                ) {
                                    Icon(
                                        Icons.Default.SkipPrevious,
                                        contentDescription = "Chapter sebelumnya",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (prevChapter != null) 1f else 0.3f
                                        )
                                    )
                                }
                                Slider(
                                    value = currentPage.toFloat()
                                        .coerceIn(0f, (list.size - 1).coerceAtLeast(0).toFloat()),
                                    onValueChange = { v ->
                                        val target = v.toInt().coerceIn(0, list.size - 1)
                                        readerScope.launch {
                                            if (mode == ReaderMode.WEBTOON) {
                                                vList.scrollToItem(target)
                                            } else {
                                                hState.scrollToPage(target)
                                            }
                                        }
                                    },
                                    valueRange = 0f..(list.size - 1).coerceAtLeast(1).toFloat(),
                                    steps = (list.size - 2).coerceAtLeast(0),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF2563EB),
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                                )
                                )
                                ReaderCircleButton(
                                    onClick = { nextChapter?.let(onNavigate) },
                                    enabled = nextChapter != null
                                ) {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = "Chapter berikutnya",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (nextChapter != null) 1f else 0.3f
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pill counter halaman.
                                Box(
                                    modifier = Modifier
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onBackground,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                        .background(
                                            LocalExtraColors.current.card,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${currentPage + 1} / ${list.size}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                // Pilihan mode segmented brutal, bukan teks
                                // sempit yang membingungkan.
                                // Dua pil fixed (bukan FlowRow): lebar total
                                // pasti muat, tidak bisa clip/overflow.
                                val ctx = LocalContext.current
                                val setMode = { next: ReaderMode ->
                                    onModeChange(next)
                                    Toast.makeText(
                                        ctx,
                                        if (next == ReaderMode.WEBTOON) "Mode: Webtoon"
                                        else "Mode: Halaman",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                ModePill(
                                    text = "Halaman",
                                    selected = mode != ReaderMode.WEBTOON,
                                    onClick = { setMode(ReaderMode.PAGED) }
                                )
                                Spacer(Modifier.width(6.dp))
                                ModePill(
                                    text = "Webtoon",
                                    selected = mode == ReaderMode.WEBTOON,
                                    onClick = { setMode(ReaderMode.WEBTOON) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pil mode kecil fixed: selalu muat berdampingan dengan pill counter,
 * tidak seperti FlowRow yang bisa overflow/clip di layar sempit.
 */
@Composable
private fun ModePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val extra = LocalExtraColors.current
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier = Modifier
            .border(2.dp, MaterialTheme.colorScheme.onBackground, shape)
            .background(
                if (selected) extra.yellow else MaterialTheme.colorScheme.surface,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) extra.onAccent
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * Tombol ikon lingkaran bergaris tinta + bayangan keras untuk reader.
 * Pengganti IconButton polos yang tenggelam di atas gambar.
 */
@Composable
private fun ReaderCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val extra = LocalExtraColors.current
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier = Modifier
            .size(36.dp)
            .hardShadow(extra.shadow, 2.dp, shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape
            )
            .border(2.dp, MaterialTheme.colorScheme.onBackground, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
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
    flow: Boolean = false,
    // Ketuk gambar toggle menu via clickable STANDAR (bukan adu
    // gesture detector): clickable hanya memakan ketuk murni, geser
    // untuk pindah halaman dan cubit-zoom tetap lolos. Tanpa ripple
    // (indication=null) supaya tidak ada kilatan di atas gambar.
    onImageTap: () -> Unit = {}
) {
    val context = LocalContext.current
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
    // Bersihkan zoom sisa saat halaman tidak lagi aktif: zoom basi yang
    // tertinggal membuat gulir berubah jadi geser-gambar sehingga index
    // macet (counter + riwayat ikut macet).
    LaunchedEffect(active) {
        if (!active && (scale != 1f || offset != Offset.Zero)) {
            scale = 1f
            offset = Offset.Zero
            onLockChange(false)
        }
    }
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
                            // Double-tap mengembalikan zoom ke 1x: jalan keluar
                            // yang jelas bila pengguna tersangkut dalam
                            // keadaan zoom tanpa sadar. Tap tidak dikonsumsi
                            // oleh transformable sehingga keduanya akur.
                            .pointerInput(item.uri) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = 1f
                                        offset = Offset.Zero
                                        onLockChange(false)
                                    }
                                )
                            }
                            .transformable(
                                state = transform,
                                canPan = { scale > 1f }
                            )
                            .clickable(
                                interactionSource = remember(item.uri) {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                                indication = null,
                                onClick = onImageTap
                            )
                    )
}
