package com.trialfetch.app.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.trialfetch.app.BuildConfig
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Diagnosa crash tanpa PC, TIGA jalur redundan supaya tidak ada satu
 * mekanisme yang bisa diam-diam gagal:
 *
 *  1. FILE  — laporan crash + marker ditulis ke folder Download lewat
 *             MediaStore (bisa dibuka dari aplikasi Files).
 *  2. CLIPBOARD — handler crash meng-copy laporan ke clipboard sistem;
 *             teksnya disimpan system_server, jadi tetap ada meski proses
 *             aplikasi mati detik itu juga. Toast memberi tahu pemakai.
 *  3. BEACON — setiap penahap startup dikirim (fire-and-forget, thread
 *             daemon) ke endpoint webhook, supaya mati yang terjadi
 *             SEBELUM MediaStore sempat menulis pun tetap terlihat:
 *             beacon nyala = Application benar-benar jalan; tidak nyala
 *             (meski instal & buka sukses) = mati Pra-Application.
 */
object CrashLog {

    // Endpoint inspeksi (URL webhook.site) — diubah/dihapus begitu kasus selesai.
    private const val BEACON_URL =
        "https://webhook.site/3692a326-bd52-47ef-9a5d-651894484914"

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val stampFmt = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    private val seq = AtomicInteger(0)

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
     * Catat satu penanda startup. Ditulis sinkron ke disk (murah: 2-3 baris
     * per jalanan) dan diteruskan ke beacon — dua-duanya kebal race proses
     * yang mati mendadak.
     */
    fun mark(event: String) {
        val line = "${fmt.format(Date())}  $event"
        synchronized(lock) { buf.add(line) }
        runCatching { writeText("session", snapshot()) }
        beacon("mark")
    }

    private fun snapshot(): String = synchronized(lock) {
        buf.joinToString("\n") + "\n"
    }

    private fun reportCrashSync(thread: Thread, e: Throwable) {
        // SINKRON di thread yang crash: media lain tidak dijamin sempat
        // menulis sebelum proses dimatikan runtime.
        runCatching {
            val body = buildString {
                append("=== Startup sebelum crash ===\n")
                synchronized(lock) { buf.forEach { append(it).append('\n') } }
                append("\n=== Crash ===\nthread: ${thread.name}\n")
                append(Log.getStackTraceString(e))
                append("\n\nbuild: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}),")
                append(" ${Build.MODEL} API ${Build.VERSION.SDK_INT}\n")
            }
            writeText("crash", body)
            copyToClipboard(body)
            beacon("crash")
        }
    }

    /** Copy laporan ke clipboard — teks selamat walau proses mati seketika. */
    private fun copyToClipboard(text: String) {
        // ClipboardManager butuh Looper: di thread UI crash biasa ada;
        // di thread lain biarkan gagal (file + beacon sudah menyelamatkan).
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

    /**
     * Tulis ke Download (MediaStore, API 29+) supaya terlihat di aplikasi
     * Files tanpa izin penyimpanan, atau ke folder privat aplikasi di
     * Android lama. Tidak pernah melempar — dipanggil dari handler crash.
     */
    private fun writeText(kind: String, body: String) {
        val name = "TrialFetch-$kind-${stampFmt.format(Date())}-${seq.incrementAndGet()}.txt"
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = appCtx.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return
                resolver.openOutputStream(uri)?.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }
            } else {
                val dir = appCtx.getExternalFilesDir(null) ?: return
                File(dir, name).writeText(body)
            }
        } catch (t: Throwable) {
            // Diamkan: handler crash tidak boleh melempar apa pun.
        }
    }
}
