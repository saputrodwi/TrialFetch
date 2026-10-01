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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trialfetch.app.data.DownloadProgress
import com.trialfetch.app.data.QueueItem
import com.trialfetch.app.data.QueueItemState
import com.trialfetch.app.ui.theme.BrutalCard
import com.trialfetch.app.ui.theme.LocalExtraColors

/**
 * Layar Unduhan: progres chapter aktif + seluruh antrian di satu tempat.
 * Dipisah dari layar series supaya terlihat dari mana pun (via bottom bar),
 * bukan terkubur di bawah daftar chapter.
 */
@Composable
fun DownloadsScreen(
    progress: DownloadProgress,
    onDismissProgress: () -> Unit,
    onCancelDownload: () -> Unit,
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
        if (progress.state != DownloadProgress.State.IDLE) {
            item {
                DownloadPanel(progress, LocalExtraColors.current, onDismissProgress, onCancelDownload)
            }
        }
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
        if (progress.state == DownloadProgress.State.IDLE && queueItems.isEmpty()) {
            item {
                BoxEmpty("Belum ada unduhan.\nPilih chapter dari series untuk mulai.")
            }
        }
    }
}

@Composable
private fun BoxEmpty(text: String) {
    androidx.compose.foundation.layout.Box(
        // fillParentMaxSize (bukan fillMaxSize) agar benar-benar
        // tengah layar di dalam LazyColumn.
        Modifier.fillParentMaxSize(),
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
internal fun DownloadPanel(
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
                    // Kuning terang di kedua mode, jadi teks gagal pun
                    // memakai tinta gelap (onErrorContainer terang akan
                    // hilang di atas kuning pada mode terang).
                    color = ink,
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
