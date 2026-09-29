package com.trialfetch.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Menulis hasil unduhan ke folder publik Download/TrialFetch.
 *
 * Dua jalur karena Android-split:
 *  - API 29+ memakai MediaStore, karena akses langsung ke folder publik
 *    sudah dibatasi sejak Android 10.
 *  - API 24-28 masih boleh menulis langsung lewat
 *    Environment.DIRECTORY_DOWNLOADS (butuh WRITE_EXTERNAL_STORAGE).
 *
 * Penulisan memakai MediaStore di Android 10+ dan file biasa di bawahnya,
 * karena akses folder publik sudah dibatasi sejak Android 10.
 */
class StorageWriter(private val context: Context) {

    private val resolver = context.contentResolver

    /**>true kalau aplikasi boleh menulis ke Download publik. */
    fun canWrite(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) true
        else hasLegacyPermission()

    private fun hasLegacyPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Judul folder anak, mis. "Judul Komik/Chapter 01". */
    private fun relativePath(vararg segments: String): String =
        (listOf(Environment.DIRECTORY_DOWNLOADS, OutputPaths.ROOT) + segments)
            .joinToString("/")

    // ---------------------------------------------------------------- folder

    fun writeFile(folderPath: String, fileName: String, bytes: ByteArray): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            writeLegacy(folderPath, fileName, bytes)
            return null
        }
        run {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(fileName))
                put(MediaStore.Downloads.RELATIVE_PATH, folderPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: return run { writeLegacy(folderPath, fileName, bytes); null }
            return try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        }
    }

    private fun writeLegacy(folderPath: String, fileName: String, bytes: ByteArray): Uri? =
        runCatching {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(root, folderPath.removePrefix(Environment.DIRECTORY_DOWNLOADS + "/"))
            dir.mkdirs()
            val f = File(dir, fileName)
            f.writeBytes(bytes)
            Uri.fromFile(f)
        }.getOrNull()

    /**
     * Membaca isi file yang sudah tertulis sebelumnya.
     *
     * Dipakai saat mode ZIP: gambar-gambarnya sudah ada di MediaStore,
     * lalu dibungkus jadi satu arsip. Pada Android 10+ file publik tidak
     * punya jalur File biasa, jadi isinya diambil lewat content://.
     */
    fun readFile(folderPath: String, fileName: String): ByteArray? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection =
                "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?"
            val args = arrayOf(folderPath, fileName)
            val id = resolver.query(collection, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            } ?: return null

            // ContentUris.withAppendedId adalah cara baku membuat uri item
            // dari sebuah collection — MediaStore tidak menyediakan
            // buildContentUri.
            val uri = ContentUris.withAppendedId(collection, id)
            return runCatching {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }

        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(root, folderPath.removePrefix(Environment.DIRECTORY_DOWNLOADS + "/"))
        val f = File(dir, fileName)
        return if (f.exists()) f.readBytes() else null
    }

    // ------------------------------------------------------------------- zip

    /**
     * Menggabungkan sekumpulan file menjadi satu arsip zip di folder
     * [zipFolderPath] dengan nama [zipName].
     */
    fun createZip(zipFolderPath: String, zipName: String, entries: List<Pair<String, ByteArray>>): Uri? {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return writeFile(zipFolderPath, zipName, bytes.toByteArray())
    }

    fun writeText(folderPath: String, fileName: String, text: String): Uri? =
        writeFile(folderPath, fileName, text.toByteArray(Charsets.UTF_8))

    private fun mimeOf(name: String): String = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".zip", true) -> "application/zip"
        name.endsWith(".txt", true) -> "text/plain"
        else -> "application/octet-stream"
    }
}
