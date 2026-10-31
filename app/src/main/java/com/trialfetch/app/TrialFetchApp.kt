package com.trialfetch.app

import android.app.Application
import android.os.Build
import com.trialfetch.app.data.CrashLog

class TrialFetchApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Perekam marker + crash ke folder Download dipasang PALING AWAL —
        // satu-satunya cara mendiagnosis HP tanpa PC (di Android 11+
        // logcat pihak ketiga terkunci). Lihat data/CrashLog.kt.
        CrashLog.install(this)
        CrashLog.mark("Application.onCreate @ ${Build.MODEL} API ${Build.VERSION.SDK_INT}")

        // OpenCV TIDAK lagi di-init di sini (dulu ada Thread initLocal()).
        // Alasan: pemotong banner sudah memuat OpenCV sendiri secara lazy
        // saat unduhan pertama, dan runCatching di Java TIDAK bisa
        // menangkap crash native (SIGSEGV saat System.load / JNI_OnLoad).
        // Kalau pemuatan .so gagal mematikan proses, crash terjadi tepat
        // pada splash screen dan mustahil dianalisis. Memuat nanti pun tetap
        // terlindungi BannerCropper (method-nya tidak pernah melempar).
    }
}
