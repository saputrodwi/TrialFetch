package com.trialfetch.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.trialfetch.app.data.CrashLog

class TrialFetchApp : Application() {

    // attachBaseContext adalah titik kode aplikasi yang PALING AWAL jalan —
    // lebih awal dari onCreate. Marker di sini membuktikan bahwa proses
    // sempat menjalankan kode kita sedikit-demi-sedikitnya di titik ini.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.mark(
            "Application.attachBaseContext @ ${Build.MODEL} API ${Build.VERSION.SDK_INT}"
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Perekam marker + crash (file Download, clipboard, beacon webhook)
        // dipasang PALING AWAL. Lihat data/CrashLog.kt.
        CrashLog.install(this)
        CrashLog.mark("Application.onCreate")

        // OpenCV TIDAK lagi di-init di sini (dulu ada Thread initLocal()).
        // Alasan: pemotong banner sudah memuat OpenCV sendiri secara lazy
        // saat unduhan pertama, dan runCatching di Java TIDAK bisa
        // menangkap crash native (SIGSEGV saat System.load / JNI_OnLoad).
        // Kalau pemuatan .so gagal mematikan proses, crash terjadi tepat
        // pada splash screen dan mustahil dianalisis. Memuat nanti pun tetap
        // terlindungi BannerCropper (method-nya tidak pernah melempar).
    }
}
