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
        const val NOTIFICATION_ID = 1001
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Unduhan",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progres mengunduh chapter komik"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun build(
        title: String,
        text: String,
        progress: Int?,
        ongoing: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress != null) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    fun showRunning(title: String, done: Int, total: Int) {
        ensureChannel()
        val percent = if (total > 0) (done * 100 / total) else 0
        val text = if (total > 0) "$done dari $total halaman ($percent%)" else "Menyiapkan…"
        manager.notify(NOTIFICATION_ID, build(title, text, percent, true))
    }

    fun showDone(title: String, done: Int, path: String?) {
        ensureChannel()
        val text = buildString {
            append("$done halaman tersimpan")
            if (path != null) {
                append(" · ")
                append(path)
            }
        }
        val n = build(title, text, 100, false)
        manager.notify(NOTIFICATION_ID, n)
        // JANGAN cancel di sini: notifikasi selesai harus tetap terlihat
        // sampai user menutupnya sendiri, supaya path hasil unduhan sempat
        // dibaca. (Sebelumnya ada cancel() persis setelah notify sehingga
        // notifikasi hilang dalam milidetik.) Notifikasi berjalan (progress)
        // ditimpa otomatis oleh notify berikutnya dengan ID yang sama.
    }

    fun showFailed(title: String, reason: String) {
        ensureChannel()
        manager.notify(NOTIFICATION_ID, build(title, reason, null, false))
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)
}
