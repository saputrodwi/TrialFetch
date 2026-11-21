package com.trialfetch.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trialfetch.app.R
import com.trialfetch.app.core.SearchResult
import com.trialfetch.app.core.Source
import com.trialfetch.app.data.HistoryEntry
import com.trialfetch.app.data.QueueItem
import com.trialfetch.app.data.QueueItemState
import com.trialfetch.app.ui.theme.BrutalDashedBox
import com.trialfetch.app.ui.theme.Space

/**
 * Beranda: satu kolom cari, pilihan sumber, dan ringkasan aktivitas.
 * Pencarian lintas sumber adalah perilaku awal; sumber tertentu bisa
 * dipilih lewat pill bila pengguna ingin membatasi hasil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    sources: List<Source>,
    source: Source,
    onSourceChange: (Source) -> Unit,
    allSelected: Boolean = true,
    onAllSources: () -> Unit = {},
    results: List<SearchResult>,
    error: String?,
    urlInput: String,
    urlLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onOpenUrl: () -> Unit,
    history: List<HistoryEntry>,
    onOpenHistory: (HistoryEntry) -> Unit,
    onPick: (SearchResult) -> Unit,
    storageGranted: Boolean,
    onReset: () -> Unit,
    recentDownloads: List<QueueItem> = emptyList(),
    onOpenDownloads: () -> Unit = {}
) {
    var showUrlDialog by remember { mutableStateOf(false) }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.url_dialog_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.url_dialog_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Space.md))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.url_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (!urlLoading && urlInput.isNotBlank()) {
                                onOpenUrl()
                                showUrlDialog = false
                            }
                        })
                    )
                    if (urlLoading) {
                        Spacer(Modifier.height(Space.md))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOpenUrl()
                        showUrlDialog = false
                    },
                    enabled = !urlLoading && urlInput.isNotBlank()
                ) { Text(stringResource(R.string.action_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.lg)
    ) {
        Spacer(Modifier.height(Space.md))

        if (!storageGranted) {
            StatusNote(stringResource(R.string.storage_warning))
            Spacer(Modifier.height(Space.md))
        }

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            searching = searching
        )

        Spacer(Modifier.height(Space.md))
        SectionHeader(stringResource(R.string.section_sources))
        Spacer(Modifier.height(Space.sm))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            SourcePill(
                text = stringResource(R.string.all_sources),
                selected = allSelected,
                onClick = onAllSources
            )
            sources.forEach { s ->
                SourcePill(
                    text = s.displayName,
                    selected = !allSelected && source == s,
                    onClick = { onSourceChange(s) }
                )
            }
        }

        if (!allSelected && (source == Source.MANWANG || source == Source.RUMAN)) {
            Spacer(Modifier.height(Space.sm))
            StatusNote(stringResource(R.string.search_limited, source.displayName))
        }

        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showUrlDialog = true }) {
                Text(stringResource(R.string.open_url))
            }
            Spacer(Modifier.weight(1f))
            if (query.isNotBlank() || results.isNotEmpty() || error != null || urlInput.isNotBlank()) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(Space.xs))
                    Text(stringResource(R.string.reset))
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(Space.sm))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        when {
            searching -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            results.isNotEmpty() -> {
                Spacer(Modifier.height(Space.sm))
                Text(
                    stringResource(R.string.result_count, results.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    items(results, key = { it.source.id + it.comicId }) { r ->
                        ResultRow(r, onClick = { onPick(r) })
                    }
                }
            }

            else -> {
                val recent = history.take(5)
                val done = recentDownloads
                    .filter { it.state == QueueItemState.DONE }
                    .takeLast(3)
                    .asReversed()
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    if (recent.isEmpty() && done.isEmpty()) {
                        item {
                            BrutalDashedBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Space.xl),
                                text = stringResource(R.string.empty_home)
                            )
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.section_continue)) }
                        items(recent, key = { it.key() }) { h ->
                            HistoryRow(entry = h, onClick = { onOpenHistory(h) })
                        }
                    }
                    item {
                        Spacer(Modifier.height(Space.sm))
                        SectionHeader(stringResource(R.string.section_downloads))
                    }
                    if (done.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.empty_downloads),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onOpenDownloads) {
                                Text(stringResource(R.string.see_downloads))
                            }
                        }
                    } else {
                        items(done, key = { it.id }) { item ->
                            DownloadRow(item = item, onClick = onOpenDownloads)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Cari judul komik") },
        singleLine = true,
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            IconButton(onClick = onSearch, enabled = !searching) {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Cari")
                }
            }
        }
    )
}

@Composable
private fun StatusNote(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(Space.md)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Space.xs))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SourcePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .heightIn(min = Space.touch)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(Space.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface
                )
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Space.touch)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.coverUrl.ifBlank { null },
            contentDescription = null,
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(Space.xs))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.xs))
            Text(
                entry.chapterTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.totalPages > 0) {
                Spacer(Modifier.height(Space.sm))
                val pct = entry.fraction?.let { (it * 100).toInt().coerceIn(0, 100) }
                Text(
                    if (pct != null) stringResource(R.string.pages_progress, entry.page, entry.totalPages, pct)
                    else stringResource(R.string.pages_progress_short, entry.page, entry.totalPages),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.xs))
                LinearProgressIndicator(
                    progress = { entry.fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(item: QueueItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Space.touch)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.series.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.xs))
            Text(
                item.chapter.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val copyTitle = remember(r.title) {
        {
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("judul komik", r.title))
            Toast.makeText(ctx, R.string.title_copied, Toast.LENGTH_SHORT).show()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Space.touch)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = r.coverUrl.ifBlank { null },
            contentDescription = null,
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(Space.xs))
        Column(Modifier.weight(1f)) {
            Text(
                r.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { copyTitle() })
                }
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                listOf(r.source.displayName, r.author).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
