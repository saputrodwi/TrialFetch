package com.trialfetch.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trialfetch.app.data.QueueItem
import com.trialfetch.app.data.QueueItemState
import com.trialfetch.app.ui.theme.BrutalCard
import com.trialfetch.app.ui.theme.LocalExtraColors

/**
 * Layar Unduhan: seluruh antrian di satu tempat. Sengaja HANYA antrian
 * (tanpa panel progres legacy): semua unduhan lewat antrian, jadi panel
 * progres + baris AKTIF menampilkan unduhan yang sama dua kali.
 * Dipisah dari layar series supaya terlihat dari mana pun (via bottom
 * bar), bukan terkubur di bawah daftar chapter.
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (queueItems.isNotEmpty()) {
            item {
                QueueCard(
                    items = queueItems,
                    paused = queuePaused,
                    onPauseAll = onPauseAll,
                    onResumeAll = onResumeAll,
                    onCancelItem = onCancelItem,
                    onRemoveItem = onRemoveItem,
                    onRetryItem = onRetryItem,
                    onClearFinished = onClearFinished
                )
            }
        }
        if (queueItems.isEmpty()) {
            item {
                BoxEmpty(
                    "Belum ada unduhan.\nPilih chapter dari series untuk mulai.",
                    Modifier.fillParentMaxSize()
                )
            }
        }
    }
}

@Composable
private fun BoxEmpty(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        // Modifier dari call site (fillParentMaxSize) agar benar-benar
        // tengah layar di dalam LazyColumn. Tidak bisa dipanggil di sini
        // karena hanya tersedia dalam scope item LazyColumn.
        modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
internal fun QueueCard(
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
        Text(
            "Antrian ($doneCount/${items.size} selesai)",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        items.forEachIndexed { index, item ->
            if (index > 0) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                )
            }
            QueueRow(
                item = item,
                onCancel = { onCancelItem(item.id) },
                onRemove = { onRemoveItem(item.id) },
                onRetry = { onRetryItem(item.id) }
            )
        }
    }
}

@Composable
internal fun QueueRow(
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
                val statusColor = when (item.state) {
                    QueueItemState.DONE -> MaterialTheme.colorScheme.primary
                    QueueItemState.FAILED -> MaterialTheme.colorScheme.error
                    QueueItemState.ACTIVE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    when (item.state) {
                        QueueItemState.QUEUED -> "● Menunggu"
                        QueueItemState.ACTIVE ->
                            if (item.total > 0) "● Mengunduh ${item.done}/${item.total}" else "● Mengunduh…"
                        QueueItemState.DONE -> "● Selesai"
                        QueueItemState.FAILED -> "● Gagal${item.error?.let { ": $it" } ?: ""}"
                        QueueItemState.CANCELLED -> "● Dibatalkan"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = if (item.state == QueueItemState.ACTIVE ||
                        item.state == QueueItemState.FAILED
                    ) FontWeight.SemiBold else FontWeight.Normal,
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
