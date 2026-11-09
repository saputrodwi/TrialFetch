package com.trialfetch.app.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import com.trialfetch.app.BuildConfig
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnosa crash tanpa PC, dua jalur redundan supaya tidak ada satu
 * mekanisme yang bisa diam-diam gagal:
 *
 *  1. CLIPBOARD — handler crash meng-copy laporan ke clipboard sistem;
 *             teksnya disimpan system_server, jadi tetap ada meski proses
 *             aplikasi mati detik itu juga. Toast memberi tahu pemakai.
 *  2. BEACON — setiap penahap startup dikirim (fire-and-forget, thread
 *             daemon) ke endpoint webhook, supaya mati yang terjadi
 *             SEBELUM clipboard sempat menulis pun tetap terlihat:
 *             beacon nyala = Application benar-benar jalan; tidak nyala
 *             (meski instal & buka sukses) = mati Pra-Application.
 *
 * (Jalur FILE ke Download sudah dimatikan — lewat MediaStore setiap
 * pemanggilan `mark()` menulis satu file, sehingga folder Download
 * pengguna penuh penuh. Unduhan lama yang terlanjur ada tetap bisa
 * dihapus manual dari file manager.)
 */
object CrashLog {

    // Endpoint inspeksi (URL webhook.site) — diubah/dihapus begitu kasus selesai.
    private const val BEACON_URL =
        "https://webhook.site/3692a326-bd52-47ef-9a5d-651894484914"

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val buf = mutableListOf<String>()
    private val lock = Any()

    private lateinit var appCtx: Context

    /** Pasang perekam crash. Dipanggil sekali, dari Application.attachBaseContext. */
    fun install(context: Context) {
        appCtx = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            reportCrashSync(thread, e)
            prev?.uncaughtException(thread, e)
        }
    }

    /**
     * Catat satu penanda startup. Di-ingat di memori dan diteruskan ke
     * beacon — kebal race proses yang mati mendadak.
     * (Sebelumnya juga menulis file ke Download lewat MediaStore, tapi
     * itu membuat folder Download pengguna penuh.)
     */
    fun mark(event: String) {
        val line = "${fmt.format(Date())}  $event"
        synchronized(lock) { buf.add(line) }
        beacon("mark")
    }

    private fun reportCrashSync(thread: Thread, e: Throwable) {
        // SINKRON di thread yang crash: clipboard + beacon jalan cepat.
        runCatching {
            val body = buildString {
                append("=== Startup sebelum crash ===\n")
                synchronized(lock) { buf.forEach { append(it).append('\n') } }
                append("\n=== Crash ===\nthread: ${thread.name}\n")
                append(Log.getStackTraceString(e))
                append("\n\nbuild: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}),")
                append(" ${Build.MODEL} API ${Build.VERSION.SDK_INT}\n")
            }
            copyToClipboard(body)
            beacon("crash")
        }
    }

    /** Copy laporan ke clipboard — teks selamat walau proses mati seketika. */
    private fun copyToClipboard(text: String) {
        // ClipboardManager butuh Looper: di thread UI crash biasa ada;
        // di thread lain biarkan gagal (beacon sudah menyelamatkan).
        if (Looper.myLooper() != Looper.getMainLooper()) return
        val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("laporan crash", text))
    }

    /** Kirim snapshot marker ke webhook — fire-and-forget, cepat mati. */
    private fun beacon(stage: String) {
        // Tidak butuh appCtx sama sekali (送的 ke URL langsung), jadi jalannya
        // bahkan dari attachBaseContext — sebelum install() siap.
        val payload = buildString {
            append("{\"stage\":\"").append(stage).append("\",")
            append("\"build\":\"").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(") \",")
            append("\"device\":\"").append(Build.MODEL).append(" API ")
                .append(Build.VERSION.SDK_INT).append("\",")
            append("\"log\":\"")
            synchronized(lock) {
                buf.forEach { l ->
                    l.replace("\\", "\\\\").replace("\"", "\\\"").let { escaped ->
                        append(escaped).append("\\n")
                    }
                }
            }
            append("\"}")
        }
        Thread({
            try {
                val conn = (URL(BEACON_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 3000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                    it.write(payload)
                }
                conn.responseCode // konfirmasi; nilai tidak dipakai
            } catch (t: Throwable) {
                // Sengaja diam: beacon boleh gagal (jaringan / resolver).
            }
        }, "CrashLog-beacon").apply { isDaemon = true }.start()
    }
}
