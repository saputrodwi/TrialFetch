package com.trialfetch.app

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class TrialFetchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // OpenCV dipakai mesin pemotong banner Baozimh. Inisialisasi gagal
        // tidak boleh menjatuhkan aplikasi: pemotong banner nanti akan
        // mengembalikan gambar apa adanya kalau library belum siap.
        //
        // initLocal() membaca dan memuat puluhan MB .so native, jadi tidak
        // boleh jalan di thread UI saat startup. BannerCropper juga memuatnya
        // sendiri secara lazy (di thread unduhan) — dua pemanggilan ini aman
        // karena System.loadLibrary disinkronkan VM.
        Thread {
            val ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
            if (!ok) Log.w(TAG, "OpenCV gagal dimuat; pemotong banner dilewati")
        }.start()
    }

    private companion object {
        const val TAG = "TrialFetch"
    }
}
