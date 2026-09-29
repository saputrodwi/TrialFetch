package com.trialfetch.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Izin penyimpanan.
 *
 * Android 10+ (API 29) menulis ke folder publik lewat MediaStore dan
 * TIDAK butuh izin. Android 9 ke bawah (API 24-28) masih memakai jalur
 * file biasa, jadi WRITE_EXTERNAL_STORAGE wajib diminta saat runtime —
 * hanya声明 di manifest saja tidak cukup sejak Android 6.
 */
object StoragePermission {

    /**>true kalau aplikasi sudah boleh menulis ke folder Download. */
    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Izin yang perlu diminta, atau null kalau memang tidak perlu
     * (Android 10+).
     */
    fun required(): Array<String>? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            null
        }

    /** Izin notifikasi, hanya relevan di Android 13+. */
    fun notificationRequired(): Array<String>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }
}
