package com.trialfetch.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trialfetch.app.R
import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.SeriesInfo
import com.trialfetch.app.data.ComicRepository
import com.trialfetch.app.data.DownloadProgress
import com.trialfetch.app.data.HistoryEntry
import com.trialfetch.app.ui.theme.Space

/**
 * Detail series bergaya editorial: tanpa kartu, nomor chapter besar di
 * kiri, dan aksi utama berupa teks/outline. Ini sengaja berbeda jauh dari
 * tampilan kartu sein sebelumnya.
 */
@Composable
fun SeriesDetailScreen(
    info: SeriesInfo,
    progress: DownloadProgress,
    busyIds: Set<String> = emptySet(),
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onDownload: (Chapter) -> Unit,
    onRead: (Chapter) -> Unit,
    onEnqueue: (List<Chapter>) -> Unit,
    downloadedIds: Set<String>,
    readMap: Map<String, HistoryEntry>,
    onOpenWeb: () -> Unit = {},
    onContinue: (Chapter, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    var newestFirst by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(info.source, info.comicId) {
        selecting = false
        selectedIds = emptySet()
        filter = ""
    }

    val downloadedKey = remember(downloadedIds) { downloadedIds.map { it.lowercase() }.toSet() }
    val shown = remember(info.chapters, newestFirst, filter) {
        val q = filter.trim()
        val asc = info.chapters.mapIndexed { i, c -> Triple(c, c.chapterNumber, i) }
            .sortedWith(compareBy({ it.second ?: Int.MAX_VALUE }, { it.third }))
            .map { it.first }
            .filter {
                q.isBlank() || it.title.contains(q, ignoreCase = true) ||
                    it.chapterNumber?.toString() == q
            }
        if (newestFirst) asc.asReversed() else asc
    }
    val lastRead = remember(readMap) { readMap.values.maxByOrNull { it.updatedAt } }
    val lastChapter = lastRead?.let { e -> info.chapters.firstOrNull { it.chapterId == e.chapterId } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Space.touch).padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.series_back))
            }
            Text(
                info.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenWeb) {
                Icon(Icons.Default.Public, contentDescription = stringResource(R.string.series_open_web))
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(
                        if (isBookmarked) R.string.series_bookmark_remove else R.string.series_bookmark_add
                    )
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            item {
                Column {
                    Row {
                        AsyncImage(
                            model = info.coverUrl.ifBlank { null },
                            contentDescription = stringResource(R.string.series_cover, info.title),
                            modifier = Modifier
                                .size(width = 96.dp, height = 136.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                info.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                listOfNotNull(
                                    info.source.displayName,
                                    info.author.takeIf { it.isNotBlank() },
                                    info.status.takeIf { it.isNotBlank() }
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                stringResource(R.string.series_chapter_count, info.chapters.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (hasProgress(lastRead, lastChapter)) {
                                lastChapter?.let { onContinue(it, lastRead?.page ?: 0) }
                            } else {
                                info.chapters.firstOrNull()?.let(onRead)
                            }
                        }) {
                            Text(
                                stringResource(
                                    if (hasProgress(lastRead, lastChapter)) R.string.series_continue
                                    else R.string.series_start_read
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(Modifier.width(Space.sm))
                        OutlinedButton(onClick = onToggleBookmark) {
                            Text(
                                stringResource(
                                    if (isBookmarked) R.string.series_saved else R.string.series_save
                                )
                            )
                        }
                    }
                    if (info.synopsis.isNotBlank()) {
                        Spacer(Modifier.height(Space.md))
                        Text(
                            info.synopsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (hasProgress(lastRead, lastChapter)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .heightIn(min = Space.touch)
                            .padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.series_continue_reading),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                lastRead?.chapterTitle.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { lastChapter?.let { onContinue(it, lastRead?.page ?: 0) } }) {
                            Text(stringResource(R.string.series_continue))
                        }
                    }
                }
            }
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.series_chapter_count, shown.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        IconButton(onClick = { newestFirst = !newestFirst }) {
                            Icon(
                                if (newestFirst) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = stringResource(
                                    if (newestFirst) R.string.series_sort_old else R.string.series_sort_new
                                )
                            )
                        }
                        TextButton(onClick = {
                            selecting = !selecting
                            selectedIds = emptySet()
                        }) {
                            Text(
                                stringResource(
                                    if (selecting) R.string.series_cancel_select else R.string.series_select
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.series_filter)) },
                        singleLine = true,
                        trailingIcon = {
                            if (filter.isNotBlank()) {
                                IconButton(onClick = { filter = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.series_clear_filter))
                                }
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                        }
                    )
                    if (selecting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selectedIds = if (selectedIds.size == info.chapters.size) {
                                    emptySet()
                                } else info.chapters.map { it.chapterId }.toSet()
                            }) {
                                Text(
                                    stringResource(
                                        if (selectedIds.size == info.chapters.size) R.string.series_none
                                        else R.string.series_all
                                    )
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                val picked = info.chapters.filter { it.chapterId in selectedIds }
                                if (picked.isNotEmpty()) onEnqueue(picked)
                                selecting = false
                                selectedIds = emptySet()
                            }, enabled = selectedIds.isNotEmpty()) {
                                Text(stringResource(R.string.series_download_selected, selectedIds.size))
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.series_no_chapters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.lg)
                    )
                }
            }
            items(shown, key = { it.chapterId }) { chapter ->
                val busy = (progress.state == DownloadProgress.State.RUNNING &&
                    progress.chapterId == chapter.chapterId) || chapter.chapterId in busyIds
                val entry = readMap[chapter.chapterId]
                val folderName = ComicRepository.sanitize(chapter.title).lowercase()
                val chapterToken = ComicRepository.chapterToken(chapter.title)
                    ?.dropWhile { it.isLetter() }?.lowercase()
                val downloaded = folderName in downloadedKey ||
                    (chapterToken != null && downloadedKey.any { it.contains(chapterToken) })
                ChapterLine(
                    title = chapter.title,
                    busy = busy,
                    downloaded = downloaded,
                    readText = entry?.let {
                        if (it.totalPages > 0) stringResource(R.string.series_read_pages, it.page, it.totalPages)
                        else stringResource(R.string.series_read_last, it.chapterTitle)
                    },
                    selecting = selecting,
                    selected = chapter.chapterId in selectedIds,
                    onToggleSelect = {
                        selectedIds = if (chapter.chapterId in selectedIds) {
                            selectedIds - chapter.chapterId
                        } else selectedIds + chapter.chapterId
                    },
                    onRead = { onRead(chapter) },
                    onDownload = { onDownload(chapter) }
                )
            }
        }
    }
}

private fun hasProgress(lastRead: HistoryEntry?, lastChapter: Chapter?): Boolean =
    lastRead != null && lastChapter != null

@Composable
private fun ChapterLine(
    title: String,
    busy: Boolean,
    downloaded: Boolean,
    readText: String?,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onRead: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable { if (selecting) onToggleSelect() else onRead() }
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            Spacer(Modifier.width(Space.xs))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(
                readText,
                if (downloaded && !busy) stringResource(R.string.series_downloaded) else null
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.size(Space.touch), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                downloaded && !selecting -> androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        stringResource(R.string.series_downloaded),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs)
                    )
                }
                !selecting -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.series_download, title))
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
