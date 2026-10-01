package com.trialfetch.app.data

import com.trialfetch.app.core.Chapter
import com.trialfetch.app.core.SeriesInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Status satu item antrian. */
enum class QueueItemState {
    QUEUED, ACTIVE, DONE, FAILED, CANCELLED
}

/** Satu chapter dalam antrian unduhan. */
data class QueueItem(
    val id: Long,
    val series: SeriesInfo,
    val chapter: Chapter,
    val state: QueueItemState = QueueItemState.QUEUED,
    val done: Int = 0,
    val total: Int = 0,
    val error: String? = null
) {
    /** Kunci dedup: series + chapter yang sama tidak masuk dua kali. */
    fun key(): String = "${series.source.id}::${series.comicId}::${chapter.chapterId}"
}

/**
 * Antrian unduhan: proses chapter satu per satu secara sekuensial.
 *
 * - Jeda bersifat kooperatif: loop repo memeriksa flag tiap batas gambar,
 *   jadi jeda tidak memotong file di tengah unduhan.
 * - Batal per item: job prosesor di-cancel, item aktif ditandai CANCELLED,
 *   prosesor baru langsung jalan untuk sisa antrian.
 * - In-memory saja (tidak persist): antrian hilang saat aplikasi mati.
 *   File yang sudah selesai tetap di Download, jadi tinggal enqueue ulang.
 */
class DownloadQueueManager(
    private val scope: CoroutineScope,
    private val repo: ComicRepository,
    private val settings: () -> DownloadSettings
) {
    private val ids = AtomicLong(1)

    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private var processor: Job? = null

    /** Tambah chapter ke antrian (lewati yang sudah QUEUED/ACTIVE). */
    fun enqueue(series: SeriesInfo, chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        val cur = _items.value.toMutableList()
        var added = false
        for (ch in chapters) {
            val key = "${series.source.id}::${series.comicId}::${ch.chapterId}"
            if (cur.any { it.key() == key && (it.state == QueueItemState.QUEUED || it.state == QueueItemState.ACTIVE) }) continue
            cur += QueueItem(ids.getAndIncrement(), series, ch)
            added = true
        }
        if (added) {
            _items.value = cur
            ensureProcessing()
        }
    }

    fun pauseAll() {
        _paused.value = true
    }

    fun resumeAll() {
        _paused.value = false
        ensureProcessing()
    }

    /** Batalkan satu item. Kalau sedang aktif, prosesor di-restart untuk sisanya. */
    fun cancelItem(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.state == QueueItemState.ACTIVE) {
            update(id) { it.copy(state = QueueItemState.CANCELLED) }
            // Tunggu job lama benar-benar mati dulu (join), karena
            // ensureProcessing menolak jalan selagi isActive masih true.
            // Tanpa join, sisa antrian tidak lanjut.
            val old = processor
            processor = null
            scope.launch {
                try {
                    old?.cancel()
                    old?.join()
                } catch (_: Exception) {
                }
                ensureProcessing()
            }
        } else if (item.state == QueueItemState.QUEUED || item.state == QueueItemState.FAILED) {
            update(id) { it.copy(state = QueueItemState.CANCELLED) }
        }
    }

    /** Batalkan item yang sedang aktif (dipakai tombol Batal panel). */
    fun cancelCurrent() {
        _items.value.firstOrNull { it.state == QueueItemState.ACTIVE }?.let {
            cancelItem(it.id)
        }
    }

    /** Hapus item dari daftar (hanya yang sudah terminal). */
    fun removeItem(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.state == QueueItemState.ACTIVE) {
            cancelItem(id)
            return
        }
        _items.value = _items.value.filterNot { it.id == id }
    }

    /** Masukkan lagi item gagal/dibatalkan ke antrian. */
    fun retryItem(id: Long) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        if (item.state != QueueItemState.FAILED && item.state != QueueItemState.CANCELLED) return
        update(id) { it.copy(state = QueueItemState.QUEUED, done = 0, total = 0, error = null) }
        ensureProcessing()
    }

    /** Batalkan semua yang belum selesai. */
    fun cancelAll() {
        processor?.cancel()
        processor = null
        _items.value = _items.value.map {
            if (it.state == QueueItemState.QUEUED || it.state == QueueItemState.ACTIVE) {
                it.copy(state = QueueItemState.CANCELLED)
            } else it
        }
    }

    /** Buang semua item terminal (DONE/FAILED/CANCELLED). */
    fun clearFinished() {
        _items.value = _items.value.filter {
            it.state == QueueItemState.QUEUED || it.state == QueueItemState.ACTIVE
        }
    }

    /** true bila ada item yang sedang diunduh (untuk guard tutup panel). */
    fun isBusy(): Boolean =
        _items.value.any { it.state == QueueItemState.ACTIVE }

    fun pendingCount(): Int =
        _items.value.count { it.state == QueueItemState.QUEUED || it.state == QueueItemState.ACTIVE }

    private fun update(id: Long, f: (QueueItem) -> QueueItem) {
        _items.value = _items.value.map { if (it.id == id) f(it) else it }
    }

    private fun ensureProcessing() {
        if (processor?.isActive == true) return
        if (_items.value.none { it.state == QueueItemState.QUEUED }) return
        processor = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        try {
            while (true) {
                while (_paused.value) delay(300)
                val item = _items.value.firstOrNull { it.state == QueueItemState.QUEUED }
                    ?: break
                update(item.id) { it.copy(state = QueueItemState.ACTIVE) }
                try {
                    repo.downloadChapter(
                        item.series,
                        item.chapter,
                        settings(),
                        onImage = { d, t -> update(item.id) { it.copy(done = d, total = t) } },
                        isPaused = { _paused.value }
                    )
                    update(item.id) { it.copy(state = QueueItemState.DONE) }
                } catch (e: CancellationException) {
                    // Berhenti di sini; item sudah ditandai CANCELLED oleh
                    // pemanggil cancel, atau loop selesai bila scope mati.
                    // Jangan tandai ulang agar status CANCELLED tidak tertimpa.
                    throw e
                } catch (e: Exception) {
                    update(item.id) { it.copy(state = QueueItemState.FAILED, error = e.message) }
                }
            }
        } finally {
            processor = null
        }
    }
}
