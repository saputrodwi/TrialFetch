package com.trialfetch.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.trialfetch.app.ui.theme.Space

/**
 * Beranda: satu kolom cari, satu pilihan sumber, dan dua daftar ringkas.
 * Tidak ada kartu statistik maupun tab bergulir yang membuat isi berantakan.
 */
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
    var sourceMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.url_dialog_title), style = MaterialTheme.typography.titleLarge) },
            text = {
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
            },
            confirmButton = {
                TextButton(onClick = {
                    onOpenUrl()
                    showUrlDialog = false
                }, enabled = !urlLoading && urlInput.isNotBlank()) {
                    Text(stringResource(R.string.action_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val recent = history.take(5)
    val doneDownloads = recentDownloads
        .filter { it.state == QueueItemState.DONE }
        .takeLast(3).asReversed()

    Column(Modifier.fillMaxSize().padding(horizontal = Space.lg)) {
        Spacer(Modifier.height(Space.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            TextButton(onClick = { showUrlDialog = true }) {
                Text(stringResource(R.string.open_url))
            }
        }
        Spacer(Modifier.height(Space.md))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_label)) },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSearch, enabled = query.isNotBlank()) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_label))
                    }
                }
            }
        )
        Spacer(Modifier.height(Space.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.section_sources),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Box {
                TextButton(onClick = { sourceMenu = true }) {
                    Text(if (allSelected) stringResource(R.string.all_sources) else source.displayName)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_sources)) },
                        onClick = { onAllSources(); sourceMenu = false }
                    )
                    sources.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.displayName) },
                            onClick = { onSourceChange(s); sourceMenu = false }
                        )
                    }
                }
            }
        }
        if (query.isNotBlank() || results.isNotEmpty() || error != null) {
            TextButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(Space.xs))
                Text(stringResource(R.string.reset))
            }
        }
        if (!storageGranted) {
            Text(
                stringResource(R.string.storage_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Space.sm))

        if (results.isNotEmpty()) {
            Text(
                stringResource(R.string.result_count, results.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.md)) {
                items(results, key = { it.source.id + it.comicId }) { r -> ResultRow(r) { onPick(r) } }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = Space.md)) {
                if (recent.isEmpty() && doneDownloads.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.empty_home),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Space.xl)
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.section_continue)) }
                    items(recent, key = { it.key() }) { h -> HistoryRow(h) { onOpenHistory(h) } }
                }
                if (doneDownloads.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.section_downloads)) }
                    items(doneDownloads, key = { it.id }) { d -> DownloadRow(d, onOpenDownloads) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = Space.sm).semantics { heading() }
    )
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = Space.touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = entry.coverUrl.ifBlank { null },
            contentDescription = null,
            modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.chapterTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.totalPages > 0) {
                LinearProgressIndicator(
                    progress = { entry.fraction ?: 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = Space.xs)
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun DownloadRow(item: QueueItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = Space.touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.series.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.chapter.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = Space.touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = r.coverUrl.ifBlank { null },
            contentDescription = null,
            modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                r.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("judul komik", r.title))
                    Toast.makeText(ctx, R.string.title_copied, Toast.LENGTH_SHORT).show()
                }
            )
            Text(
                listOf(r.source.displayName, r.author).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
