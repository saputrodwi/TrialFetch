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
        val ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        if (!ok) Log.w(TAG, "OpenCV gagal dimuat; pemotong banner dilewati")
    }

    private companion object {
        const val TAG = "TrialFetch"
    }
}
