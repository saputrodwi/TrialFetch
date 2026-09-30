package com.trialfetch.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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

    private companion object {
        const val TAG = "StorageWriter"
    }

    private val resolver = context.contentResolver

    /**>true kalau aplikasi boleh menulis ke Download publik. */
    fun canWrite(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) true
        else hasLegacyPermission()

    private fun hasLegacyPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Membangun RELATIVE_PATH lengkap untuk MediaStore / folder publik.
     *
     * WAJIB menyertakan "Download/TrialFetch" dan diakhiri garis miring —
     * MediaStore memakai nilai ini persis sebagai lokasi folder, jadi bila
     * hanya berisi "judul/chapter" maka file akan mendarat di
     * "Download/judul/chapter", bukan di dalam TrialFetch. Lackanya garis
     * miring di akhir juga membuat sebagian perangkat menolaknya.
     */
    private fun fullRelativePath(subPath: String): String {
        val cleaned = subPath.trim('/')
        val base = "${Environment.DIRECTORY_DOWNLOADS}/${OutputPaths.ROOT}"
        return if (cleaned.isEmpty()) "$base/" else "$base/$cleaned/"
    }

    /**>Lokasi folder yang bisa ditampilkan ke pengguna, mis. "Download/TrialFetch/...". */
    fun displayPath(subPath: String): String =
        "Download/${OutputPaths.ROOT}/" + subPath.trim('/')

    /**
     * Menghapus semua file yang ada di dalam satu folder sebelum chapter
     * diunduh ulang.
     *
     * MediaStore.insert() selalu membuat entri baru; kalau nama file sama
     * sudah ada tapi tidak terlihat oleh aplikasi (mis. sisa unduhan lama
     * setelah aplikasi dihapus lalu dipasang ulang), file itu tak bisa
     * ditimpa dan MediaStore membuat "nama(1).ext". Membersihkan folder
     * lebih dulu menghindari itu.
     *
     * Mengembalikan jumlah file yang benar-benar terhapus.
     */
    fun clearFolder(folderPath: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = fullRelativePath(folderPath)
        return runCatching {
            val ids = mutableListOf<Long>()
            resolver.query(
                collection, arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf(relative), null
            )?.use { c ->
                while (c.moveToNext()) ids.add(c.getLong(0))
            }
            var deleted = 0
            for (id in ids) {
                val uri = Uri.withAppendedPath(collection, id.toString())
                if (runCatching { resolver.delete(uri, null, null) }.getOrDefault(0) > 0) deleted++
            }
            Log.i(TAG, "bersihkan $relative: menghapus $deleted dari ${ids.size} file")
            deleted
        }.getOrDefault(0)
    }

    /** Berapa file yang masih ada di folder, untuk dipakai sebagai peringatan. */
    fun countFiles(folderPath: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = fullRelativePath(folderPath)
        return runCatching {
            var n = 0
            resolver.query(
                collection, arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf(relative), null
            )?.use { c -> while (c.moveToNext()) n++ }
            n
        }.getOrDefault(0)
    }

    // ---------------------------------------------------------------- folder

    /**
     * Menulis file, menimpa kalau nama yang sama sudah ada.
     *
     * Penting: MediaStore.insert() SELALU membuat baris baru. Kalau nama
     * sama sudah ada, sistem menamai otomatis menjadi "0001(1).jpg" dan isi
     * file lama tetap ada. Itu yang bikin hasil unduhan kelihatan dobel saat
     * chapter yang sama diunduh ulang. Jadi sebelum insert, file yang cocok
     * dicari lebih dulu lalu ditimpa.
     *
     * Mengembalikan true kalau benar-benar tertulis.
     */
    fun writeFile(folderPath: String, fileName: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return writeLegacy(folderPath, fileName, bytes) != null
        }
        val relative = fullRelativePath(folderPath)
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        findExisting(collection, fileName, relative)?.let { existing ->
            val ok = runCatching {
                resolver.openOutputStream(existing, "wt")?.use { it.write(bytes) }
                true
            }.getOrDefault(false)
            Log.i(TAG, "timpa $fileName di $relative -> $ok")
            return ok
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(fileName))
            put(MediaStore.Downloads.RELATIVE_PATH, relative)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
        // Insert tanpa menemukan yang lama = nama akan menjadi "nama(1).ext".
        Log.w(
            TAG,
            "insert $fileName ke $relative (tidak menemukan yang lama) -> " +
                if (uri == null) "GAGAL" else "ok, uri=$uri"
        )
        if (uri == null) return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    /** Cari entri MediaStore dengan DISPLAY_NAME dan RELATIVE_PATH yang sama. */
    private fun findExisting(collection: Uri, fileName: String, relative: String): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection =
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val args = arrayOf(fileName, relative)
        return runCatching {
            resolver.query(collection, projection, selection, args, null)
                ?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val id = c.getLong(0)
                    Uri.withAppendedPath(collection, id.toString())
                }
        }.getOrNull()
    }

    private fun writeLegacy(folderPath: String, fileName: String, bytes: ByteArray): Uri? =
        runCatching {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(root, "${OutputPaths.ROOT}/${folderPath.trim('/')}")
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
        if (!writeFile(zipFolderPath, zipName, bytes.toByteArray())) return null
        return findByName(zipFolderPath, zipName)
    }

    fun writeText(folderPath: String, fileName: String, text: String): Uri? {
        if (!writeFile(folderPath, fileName, text.toByteArray(Charsets.UTF_8))) return null
        return findByName(folderPath, fileName)
    }

    /** Ambil Uri item yang baru ditulis, untuk dilaporkan ke pengguna. */
    private fun findByName(folderPath: String, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return findExisting(collection, fileName, fullRelativePath(folderPath))
    }

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
