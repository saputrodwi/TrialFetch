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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trialfetch.app.R
import com.trialfetch.app.data.QueueItem
import com.trialfetch.app.data.QueueItemState
import com.trialfetch.app.ui.theme.Space

/**
 * Unduhan: satu daftar antrean dengan status dan progres per baris.
 * Tidak ada kartu bertumpuk; aksi berupa ikon yang jelas fungsinya.
 */
@Composable
fun DownloadsScreen(
    queueItems: List<QueueItem>,
    queuePaused: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelItem: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onRetryItem: (Long) -> Unit,
    onClearFinished: () -> Unit
) {
    val active = queueItems.count { it.state == QueueItemState.ACTIVE }
    val queued = queueItems.count { it.state == QueueItemState.QUEUED }
    val done = queueItems.count { it.state == QueueItemState.DONE }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.lg)) {
        Spacer(Modifier.height(Space.md))
        Text(
            stringResource(R.string.downloads_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            stringResource(R.string.downloads_summary, active, queued, done),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = if (queuePaused) onResumeAll else onPauseAll) {
                Text(
                    stringResource(
                        if (queuePaused) R.string.downloads_resume else R.string.downloads_pause
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            if (done > 0) {
                TextButton(onClick = onClearFinished) {
                    Text(stringResource(R.string.downloads_clear))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Space.sm))

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            if (queueItems.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.downloads_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.xxl)
                    )
                }
            }
            items(queueItems, key = { it.id }) { item ->
                DownloadRow(
                    item = item,
                    onCancel = { onCancelItem(item.id) },
                    onRemove = { onRemoveItem(item.id) },
                    onRetry = { onRetryItem(item.id) }
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: QueueItem,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    val progress = if (item.total > 0) {
        item.done.toFloat() / item.total.toFloat()
    } else 0f
    val statusText = when (item.state) {
        QueueItemState.ACTIVE -> stringResource(R.string.download_state_active)
        QueueItemState.QUEUED -> stringResource(R.string.download_state_queued)
        QueueItemState.DONE -> stringResource(R.string.download_state_done)
        QueueItemState.CANCELLED -> stringResource(R.string.download_state_cancelled)
        QueueItemState.FAILED -> stringResource(R.string.download_state_failed)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touch + Space.lg)
            .clickable {
                when (item.state) {
                    QueueItemState.FAILED -> onRetry()
                    QueueItemState.DONE -> onRemove()
                    else -> onCancel()
                }
            }
            .padding(vertical = Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when (item.state) {
                QueueItemState.DONE -> Icons.Default.CheckCircle
                QueueItemState.FAILED -> Icons.Default.Error
                else -> null
            }
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (item.state == QueueItemState.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Space.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.series.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Space.sm))
            when (item.state) {
                QueueItemState.ACTIVE -> IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.download_cancel))
                }
                QueueItemState.DONE, QueueItemState.CANCELLED -> IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.download_remove))
                }
                QueueItemState.FAILED -> IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.download_retry))
                }
                QueueItemState.QUEUED -> Unit
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (item.total > 0) {
                Text(
                    "${item.done}/${item.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.state == QueueItemState.ACTIVE || item.state == QueueItemState.QUEUED) {
            Spacer(Modifier.height(Space.xs))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
