package com.trialfetch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.trialfetch.app.data.SavedSeries
import com.trialfetch.app.ui.theme.Space

/** Tersimpan: grid sampul agar berbeda dari daftar pada layar lain. */
@Composable
fun SavedScreen(
    items: List<SavedSeries>,
    onOpen: (SavedSeries) -> Unit,
    onRemove: (SavedSeries) -> Unit,
    updates: List<String> = emptyList(),
    checking: Boolean = false,
    onCheckUpdates: () -> Unit = {},
    onClearUpdates: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().padding(horizontal = Space.lg)) {
        Spacer(Modifier.height(Space.md))
        Text(
            stringResource(R.string.saved_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.saved_count, items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCheckUpdates, enabled = !checking) {
                if (checking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
                Spacer(Modifier.width(Space.xs))
                Text(stringResource(R.string.saved_check))
            }
        }
        if (updates.isNotEmpty()) {
            Text(
                stringResource(R.string.saved_updates, updates.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onClearUpdates) {
                Text(stringResource(R.string.saved_clear_updates))
            }
        }
        Spacer(Modifier.height(Space.sm))

        if (items.isEmpty()) {
            Text(
                stringResource(R.string.saved_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Space.xxl)
            )
        } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            items(items, key = { it.key() }) { series ->
                SavedTile(
                    series = series,
                    hasUpdate = updates.any { it.contains(series.title, ignoreCase = true) },
                    onClick = { onOpen(series) },
                    onRemove = { onRemove(series) }
                )
            }
        }
        }
    }
}

@Composable
private fun SavedTile(
    series: SavedSeries,
    hasUpdate: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = series.coverUrl.ifBlank { null },
                contentDescription = stringResource(R.string.saved_cover, series.title),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.saved_remove, series.title),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (hasUpdate) {
                Text(
                    stringResource(R.string.saved_new_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = Space.sm, vertical = Space.xs)
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            series.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            series.source.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
