package com.trialfetch.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.trialfetch.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Diagnosa crash tanpa PC: menulis laporan langsung ke folder Download
 * lewat MediaStore, jadi bisa dibuka dari aplikasi Files dan dikirim
 * ke mana saja tanpa kabel adb/logcat.
 *
 * Latar belakang: di Android 11+ aplikasi pihak ketiga tidak lagi
 * diizinkan membaca logcat, dan sebagian pemakai tidak punya PC. Tanpa
 * alat ini, crash yang terjadi di HP mereka mustahil dianalisis.
 *
 * Dua mekanisme:
 *  1. [mark] — penanda ("Application.onCreate", "MainActivity.onCreate")
 *     yang langsung dituliskan ke disk (asinkron). Kalau proses mati di
 *     LEVEL NATIVE (SIGSEGV dari library .so, mis. OpenCV), handler Java
 *     tidak pernah jalan — tapi marker terakhir di disk menunjukkan
 *     sejauh mana aplikasi sempat berjalan sebelum mati.
 *  2. Handler default uncaught exception — kalau crash Java, ditulis
 *     file laporan lengkap (urutan marker + stack trace) secara
 *     SINKRON, lalu diteruskan ke handler sebelumnya agar dialog "aplikasi
 *     berhenti" dan perilaku kill standar tetap berjalan.
 *
 * File di Download: TrialFetch-session-<waktu>.txt dan
 * TrialFetch-crash-<waktu>.txt. Posisi publik itu sengaja supaya bisa
 * dibuka/dikirim dari aplikasi Files. Uncaught handler menulis via
 * MediaStore — dengan atau tanpa izin penyimpanan ekstra.
 */
object CrashLog {

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val stampFmt = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
    private val seq = AtomicInteger(0)

    private val buf = mutableListOf<String>()
    private val lock = Any()

    private lateinit var appCtx: Context

    /** Pasang perekam crash. Dipanggil sekali, dari Application.onCreate. */
    fun install(context: Context) {
        appCtx = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            reportCrashSync(thread, e)
            prev?.uncaughtException(thread, e)
        }
    }

    /**
     * Catat satu peranda startup. Ditulis langsung (sinkron) — pereksipan
     * baru 2-3 file kecil per jalanan, murah, dan kebal race: proses yang
     * mati mendadak (mis. sinyal native) tetap meninggalkan jejak sejauh
     * mana aplikasi sempat jalan.
     */
    fun mark(event: String) {
        val line = "${fmt.format(Date())}  $event"
        synchronized(lock) { buf.add(line) }
        runCatching { writeText("session", snapshot()) }
    }

    private fun snapshot(): String = synchronized(lock) {
        buf.joinToString("\n") + "\n"
    }

    private fun reportCrashSync(thread: Thread, e: Throwable) {
        // SINKRON di thread yang crash: executor tidak dijamin sempat
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
        }
    }

    /**
     * Tulis ke Download (MediaStore, API 29+) supaya terlihat di aplikasi
     * Files tanpa izin penyimpanan, atau ke folder privat aplikasi di
     * Android lama. Tidak pernah melempar — dipanggil dari handler crash.
     */
    private fun writeText(kind: String, body: String) {
        // (kind "session" di-rewrite tiap marker → namanya unik per tulisan)
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
