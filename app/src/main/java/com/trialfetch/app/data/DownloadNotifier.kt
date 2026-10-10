package com.trialfetch.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.trialfetch.app.ui.MainActivity

/**
 * Notifikasi progres unduhan.
 *
 * Unduhan chapter bisa berjalan belasan menit, jadi tanpa notifikasi
 * pengguna tidak tahu prosesnya masih hidup — dan saat aplikasi
 * ditutup, coroutine-nya ikut mati.
 */
class DownloadNotifier(private val context: Context) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "downloads"
        const val CHANNEL_UPDATES = "updates"
        const val NOTIFICATION_ID = 1001
        const val NOTIF_UPDATE_ID = 1002

        const val EXTRA_OPEN = "trialfetch.open"
        const val OPEN_DOWNLOADS = "downloads"
        const val OPEN_SAVED = "saved"
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Unduhan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progres mengunduh chapter komik"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                "Update chapter",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Chapter baru dari series tersimpan"
                setShowBadge(true)
            }
        )
    }

    /** Intent buka aplikasi langsung ke layar tertentu (Unduhan/Simpan). */
    private fun openIntent(target: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, target)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Muat bitmap kecil untuk BigPicture; null bila gagal (fallback teks). */
    private fun loadBitmap(url: String?): android.graphics.Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
            conn.connect()
            if (conn.responseCode !in 200..299) return null
            val raw = conn.inputStream.use { it.readBytes() }
            val bounds = android.graphics.BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            val w = bounds.outWidth
            val sample = if (w > 512) (w / 512) else 1
            val opts = android.graphics.BitmapFactory.Options()
            opts.inSampleSize = sample
            android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun base(channel: String, icon: Int, target: String, requestCode: Int) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentIntent(openIntent(target, requestCode))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)

    fun showRunning(title: String, done: Int, total: Int) {
        ensureChannel()
        val percent = if (total > 0) (done * 100 / total) else 0
        val text = if (total > 0) "$done dari $total halaman ($percent%)" else "Menyiapkan…"
        val n = base(
            CHANNEL_ID, com.trialfetch.app.R.drawable.ic_notif_download,
            OPEN_DOWNLOADS, 1
        )
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_view, "Lihat",
                openIntent(OPEN_DOWNLOADS, 2)
            )
            .build()
        manager.notify(NOTIFICATION_ID, n)
    }

    fun showDone(title: String, done: Int, path: String?, coverUrl: String? = null) {
        ensureChannel()
        val text = buildString {
            append("$done halaman tersimpan")
            if (path != null) {
                append(" · ")
                append(path)
            }
        }
        // JANGAN cancel di sini: notifikasi selesai harus tetap terlihat
        // sampai user menutupnya sendiri, supaya path hasil unduhan sempat
        // dibaca. Notifikasi berjalan (progress) ditimpa otomatis oleh
        // notify berikutnya dengan ID yang sama.
        val cover = loadBitmap(coverUrl)
        val n = base(
            CHANNEL_ID, com.trialfetch.app.R.drawable.ic_notif_done,
            OPEN_DOWNLOADS, 3
        )
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                if (cover != null) NotificationCompat.BigPictureStyle()
                    .bigPicture(cover)
                    .setSummaryText(text)
                else NotificationCompat.BigTextStyle().bigText(text)
            )
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_view, "Lihat",
                openIntent(OPEN_DOWNLOADS, 4)
            )
            .build()
        manager.notify(NOTIFICATION_ID, n)
    }

    fun showFailed(title: String, reason: String) {
        ensureChannel()
        val n = base(
            CHANNEL_ID, com.trialfetch.app.R.drawable.ic_notif_error,
            OPEN_DOWNLOADS, 5
        )
            .setContentTitle("Gagal: $title")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_view, "Lihat",
                openIntent(OPEN_DOWNLOADS, 6)
            )
            .build()
        manager.notify(NOTIFICATION_ID, n)
    }

    /** Notifikasi sistem saat cek update menemukan chapter baru. */
    fun showBookmarkUpdates(count: Int, titles: List<String>) {
        ensureChannel()
        val headline = "Chapter baru tersedia ($count)"
        val n = base(
            CHANNEL_UPDATES, com.trialfetch.app.R.drawable.ic_notif_update,
            OPEN_SAVED, 7
        )
            .setContentTitle(headline)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .apply {
                if (titles.size <= 1) {
                    val text = titles.firstOrNull() ?: "$count update"
                    setContentText(text)
                    setStyle(NotificationCompat.BigTextStyle().bigText(text))
                } else {
                    setContentText("${titles.first()} (+${titles.size - 1} lagi)")
                    setStyle(
                        NotificationCompat.InboxStyle()
                            .setBigContentTitle(headline)
                            .also { style -> titles.take(5).forEach { style.addLine(it) } }
                            .setSummaryText("+${titles.size - 5} lagi".takeIf { titles.size > 5 })
                    )
                }
            }
            .addAction(
                android.R.drawable.ic_menu_view, "Lihat simpanan",
                openIntent(OPEN_SAVED, 8)
            )
            .build()
        manager.notify(NOTIF_UPDATE_ID, n)
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)
}
