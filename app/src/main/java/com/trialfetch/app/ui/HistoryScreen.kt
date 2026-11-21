package com.trialfetch.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.trialfetch.app.data.HistoryEntry
import com.trialfetch.app.ui.theme.Space

/** Riwayat baca: daftar polos, ketuk untuk lanjut dari halaman terakhir. */
@Composable
fun HistoryScreen(
    items: List<HistoryEntry>,
    onOpen: (HistoryEntry) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = Space.lg)) {
        Spacer(Modifier.height(Space.md))
        Text(
            stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.history_count, items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.history_clear)) }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.xxl)
                    )
                }
            }
            items(items, key = { it.key() }) { entry ->
                HistoryRow(
                    entry = entry,
                    onClick = { onOpen(entry) },
                    onRemove = { onRemove(entry.key()) }
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch + Space.lg)
            .clickable(onClick = onClick)
            .padding(vertical = Space.md),
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
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                entry.chapterTitle,
                style = MaterialTheme.typography.bodyMedium,
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
        Spacer(Modifier.width(Space.sm))
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_remove))
        }
    }
}
