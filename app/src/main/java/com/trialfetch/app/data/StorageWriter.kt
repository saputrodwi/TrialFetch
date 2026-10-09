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

        /**
         * Penanda ekstraksi ZIP selesai, berisi jumlah file gambar yang
         * berhasil diekstrak. Tanpa penanda ini, cache "tidak kosong" di
         * anggap lengkap padahal ekstraksi bisa saja terputus di tengah.
         */
        const val COMPLETE_MARKER = ".complete"
    }

    internal val resolver = context.contentResolver

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 & bawah: hapus manual per-File, tidak ada MediaStore.
            return runCatching {
                val root = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                java.io.File(root, "${OutputPaths.ROOT}/${folderPath.trim('/')}")
                    .listFiles()?.count { it.isFile && it.delete() } ?: 0
            }.getOrDefault(0)
        }
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

    /** true bila file bernama ada di folder (tanpa membaca isinya). */
    fun hasFile(folderPath: String, fileName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val root = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            return java.io.File(root, "${OutputPaths.ROOT}/${folderPath.trim('/')}/$fileName").exists()
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return findExisting(collection, fileName, fullRelativePath(folderPath)) != null
    }

    /**
     * Nama subfolder langsung di dalam folder series (nama chapter untuk
     * mode Folder). Dipakai badge "sudah diunduh" di daftar chapter —
     * satu query untuk seluruh series, bukan satu query per chapter.
     */
    fun listChapterDirs(seriesDir: String): Set<String> {
        // RELATIVE_PATH MediaStore selalu diawali "Download/...".
        val cleaned = fullRelativePath(seriesDir)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching {
                val root = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                java.io.File(root, "${OutputPaths.ROOT}/${seriesDir.trim('/')}/").listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    .orEmpty().toSet()
            }.getOrDefault(emptySet())
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return runCatching {
            val out = HashSet<String>()
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads.RELATIVE_PATH),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("$cleaned%"),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val rel = c.getString(0) ?: continue
                    val rest = rel.removePrefix(cleaned).trim('/')
                    if (rest.isNotEmpty() && !rest.contains('/')) out += rest
                }
            }
            out.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Nama arsip .zip langsung di dalam folder series (tanpa ekstensi).
     * Pasangan [listChapterDirs] untuk mode ZIP.
     */
    fun listZipNames(seriesDir: String): Set<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching {
                val root = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                java.io.File(root, "${OutputPaths.ROOT}/${seriesDir.trim('/')}/").listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".zip", true) }
                    ?.map { it.name.removeSuffix(".zip").removeSuffix(".ZIP") }
                    .orEmpty().toSet()
            }.getOrDefault(emptySet())
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return runCatching {
            val out = HashSet<String>()
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(fullRelativePath(seriesDir)),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    if (name.endsWith(".zip", true)) {
                        out += name.substring(0, name.length - 4)
                    }
                }
            }
            out.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Cari semua folder chapter yang info.txt-nya menyebut series dengan
     * [comicId] dari sumber [sourceId]. Tidak peduli nama folder series
     * bagaimana pun — yang penting isi info.txt cocok.
     *
     * Dipakai sebagai fallback terakhir saat nama folder tidak dikenali
     * (mis. judul series di situs berubah setelah unduhan dibuat).
     *
     * @return map namaFolderChapter → pathSeries relatif terhadap
     *         Download/TrialFetch (mis. "Judul Series" atau
     *         "Baozimh/Judul Series").
     */
    fun findChaptersByInfoTxt(sourceId: String, comicId: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val root = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ),
                OutputPaths.ROOT
            )
            scanInfoTxt(root, sourceId, comicId, result, root, 0)
        } else {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.RELATIVE_PATH),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("info.txt", "${Environment.DIRECTORY_DOWNLOADS}/${OutputPaths.ROOT}/%"),
                null
            )?.use { c ->
                val idIdx = c.getColumnIndex(MediaStore.Downloads._ID)
                val pathIdx = c.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val relPath = c.getString(pathIdx) ?: continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    val content = runCatching {
                        resolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    }.getOrNull() ?: continue
                    if (!content.contains("source=$sourceId") ||
                        !content.contains("comicId=$comicId")
                    ) continue
                    // relPath = "Download/TrialFetch/<...>/<chapter>/"
                    val parts = relPath.trimEnd('/').split("/")
                    // ["Download","TrialFetch", …series…, "chapter"]
                    if (parts.size >= 4) {
                        result[parts.last()] = parts.drop(2).dropLast(1).joinToString("/")
                    }
                }
            }
        }
        return result
    }

    /** Rekursif: cari info.txt yang cocok, catat nama folder chapter-nya. */
    private fun scanInfoTxt(
        dir: java.io.File,
        sourceId: String,
        comicId: String,
        out: MutableMap<String, String>,
        root: java.io.File,
        depth: Int
    ) {
        if (depth > 3) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                scanInfoTxt(child, sourceId, comicId, out, root, depth + 1)
            } else if (child.name == "info.txt") {
                val content = runCatching { child.readText() }.getOrNull() ?: continue
                if (!content.contains("source=$sourceId") ||
                    !content.contains("comicId=$comicId")
                ) continue
                val chapterDir = child.parentFile?.name ?: continue
                val seriesDir = child.parentFile?.parentFile ?: continue
                out[chapterDir] = seriesDir.relativeTo(root).path
            }
        }
    }

    /** Berapa file yang masih ada di folder, untuk dipakai sebagai peringatan. */
    fun countFiles(folderPath: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching {
                val root = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                java.io.File(root, "${OutputPaths.ROOT}/${folderPath.trim('/')}")
                    .listFiles()?.count { it.isFile } ?: 0
            }.getOrDefault(0)
        }
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

    /**
     * Daftar gambar di dalam satu folder chapter, terurut nama file.
     *
     * Dipakai reader bawaan. Hanya file gambar (info.txt dilewati).
     * Mengembalikan Uri MediaStore yang bisa langsung dimuat Coil.
     */
    fun listImages(folderPath: String): List<Uri> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return listImagesLegacy(folderPath)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = fullRelativePath(folderPath)
        return runCatching {
            val out = ArrayList<Pair<String, Uri>>()
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(relative),
                "${MediaStore.Downloads.DISPLAY_NAME} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (!isImageName(name)) continue
                    val uri = Uri.withAppendedPath(collection, c.getLong(0).toString())
                    out += name to uri
                }
            }
            out.sortedBy { it.first }.map { it.second }
        }.getOrDefault(emptyList())
    }

    private fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
            n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp") ||
            n.endsWith(".avif") || n.endsWith(".heic") || n.endsWith(".jxl")
    }

    private fun listImagesLegacy(folderPath: String): List<Uri> {
        return runCatching {
            val root = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val dir = java.io.File(root, "${OutputPaths.ROOT}/${folderPath.trim('/')}")
            (dir.listFiles() ?: emptyArray())
                .filter { it.isFile && isImageName(it.name) }
                .sortedBy { it.name }
                .map { Uri.fromFile(it) }
        }.getOrDefault(emptyList())
    }

    /**
     * Ekstrak arsip ZIP chapter ke cache internal untuk dibaca.
     *
     * Reader butuh file per halaman; ZIP tidak bisa dibaca langsung per
     * halaman tanpa ekstrak. Hasil cache dipakai ulang bila penanda
     * [COMPLETE_MARKER] ada DAN jumlah file cocok dengan isinya — kalau
     * ekstraksi pernah terputus (penyimpanan penuh, app dibunuh), penanda
     * tidak tertulis sehingga sisa setengah jadi dibuang dan diekstrak ulang.
     *
     * @return daftar File gambar terurut, atau kosong bila gagal.
     */
    fun extractZipForRead(seriesDir: String, zipName: String): List<java.io.File> {
        val safeSeries = seriesDir.trim('/')
        val cacheDir = java.io.File(
            context.cacheDir,
            "reader/$safeSeries/${zipName.removeSuffix(".zip").removeSuffix(".ZIP")}"
        )
        val marker = java.io.File(cacheDir, COMPLETE_MARKER)
        val cached = runCatching {
            cacheDir.listFiles()?.filter { it.isFile && isImageName(it.name) }
                ?.sortedBy { it.name }.orEmpty()
        }.getOrDefault(emptyList())
        val expected = runCatching {
            if (marker.isFile) marker.readText().trim().toIntOrNull() else null
        }.getOrNull()
        if (expected != null && expected > 0 && cached.size == expected) return cached

        // Buka stream arsip (bukan seluruh byte-nya): chapter 60 halaman
        // bisa ratusan MB dan menampungnya penuh membuat OOM.
        val input = openZipStream(seriesDir, zipName) ?: return emptyList()
        return runCatching {
            cacheDir.mkdirs()
            // Bersihkan sisa gagal sebelumnya supaya tidak tercampur.
            cacheDir.listFiles()?.forEach { if (it.isFile) it.delete() }
            var extracted = 0
            input.use { stream ->
                java.util.zip.ZipInputStream(java.io.BufferedInputStream(stream)).use { zis ->
                    var e = zis.nextEntry
                    while (e != null) {
                        val entryName = e.name.substringAfterLast("/")
                        if (!e.isDirectory && isImageName(entryName)) {
                            val out = java.io.File(cacheDir, entryName)
                            out.outputStream().use { o -> zis.copyTo(o) }
                            extracted++
                        }
                        zis.closeEntry()
                        e = zis.nextEntry
                    }
                }
            }
            // Ditulis paling akhir: ekstraksi yang melempar exception di
            // tengah tidak meninggalkan penanda, jadi tidak dianggap lengkap.
            if (extracted > 0) marker.writeText(extracted.toString())
            cacheDir.listFiles()?.filter { it.isFile && isImageName(it.name) }
                ?.sortedBy { it.name }.orEmpty()
        }.getOrElse {
            Log.w(TAG, "ekstrak $zipName gagal: ${it.message}")
            emptyList<java.io.File>()
        }
    }

    /**
     * Buka stream isi arsip ZIP di folder Download.
     *
     * Pengganti fungsi lama yang membaca seluruh isi arsip ke RAM.
     * Android 10+ file publik tidak punya jalur File, jadi lewat
     * content://; di bawahnya langsung File biasa.
     */
    private fun openZipStream(seriesDir: String, zipName: String): java.io.InputStream? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching {
                val root = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val f = java.io.File(root, "${OutputPaths.ROOT}/${seriesDir.trim('/')}/$zipName")
                if (f.exists()) java.io.BufferedInputStream(f.inputStream()) else null
            }.getOrNull()
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = findExisting(collection, zipName, fullRelativePath(seriesDir)) ?: return null
        return runCatching { resolver.openInputStream(uri)?.buffered() }.getOrNull()
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

    // ------------------------------------------------------------------- zip

    /**
     * Menulis arsip ZIP streaming tanpa menampung seluruh isi di RAM.
     *
     * Sebelumnya seluruh gambar chapter ditampung sebagai
     * List<Pair<String, ByteArray>> lalu digandakan lagi oleh
     * ByteArrayOutputStream — chapter 60 halaman bisa makan ~100 MB heap
     * dan OOM di perangkat entry-level. Sekarang tiap gambar ditulis
     * sebagai entry segera setelah tiba, jadi memori yang dipakai hanya
     * satu gambar dalam satu waktu.
     *
     * Alurnya: [openZip] dulu, [ZipSink.put] per gambar, lalu
     * [ZipSink.commit] bila sukses atau [ZipSink.abort] bila gagal /
     * dibatalkan (file setengah jadi dihapus, tidak ditinggalkan).
     *
     * Android 10+ menulis lewat MediaStore (entri IS_PENDING), Android 9
     * ke bawah menulis File biasa — dulu cabang kedua ini `return null`
     * sehingga mode ZIP sama sekali tidak bisa dipakai di Android 7–9.
     */
    fun openZip(zipFolderPath: String, zipName: String): ZipSink? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return try {
                @Suppress("DEPRECATION")
                val root = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val dir = File(root, "${OutputPaths.ROOT}/${zipFolderPath.trim('/')}")
                dir.mkdirs()
                val target = File(dir, zipName)
                // FileOutputStream menimpa isi lama, jadi unduhan ulang
                // tidak meninggalkan sisa entri arsip sebelumnya.
                ZipSink(this, uri = null, file = target, out = java.io.FileOutputStream(target))
            } catch (e: Exception) {
                Log.w(TAG, "openZip (legacy) gagal: ${e.message}")
                null
            }
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = fullRelativePath(zipFolderPath)
        // Timpa dulu bila ada sisa unduhan sebelumnya (lihat writeFile).
        findExisting(collection, zipName, relative)?.let { old ->
            runCatching { resolver.delete(old, null, null) }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, zipName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, relative)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            val out = resolver.openOutputStream(uri, "wt") ?: run {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            ZipSink(this, uri, file = null, out = out)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            Log.w(TAG, "openZip gagal: ${e.message}")
            null
        }
    }

    /**
     * Penulis ZIP streaming. WAJIB commit atau abort, tidak boleh dibiarkan.
     *
     * @param uri entri MediaStore (Android 10+), null di jalur legacy.
     * @param file file hasil tulis legacy (Android 9 ke bawah), null di jalur
     *   MediaStore. Dipakai [abort] untuk menghapus hasil setengah jadi.
     */
    class ZipSink(
        private val owner: StorageWriter,
        private val uri: Uri?,
        private val file: java.io.File?,
        out: java.io.OutputStream
    ) {
        private val zos = ZipOutputStream(out)
        private var closed = false

        fun put(entryName: String, data: ByteArray) {
            check(!closed) { "ZipSink sudah ditutup" }
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(data)
            zos.closeEntry()
            zos.flush()
        }

        /**
         * Selesai: tutup stream. Di Android 10+ buka kunci IS_PENDING
         * supaya file terlihat di luar aplikasi.
         *
         * @return true kalau arsip benar-benar selesai ditulis.
         */
        fun commit(): Boolean {
            if (closed) return true
            closed = true
            return runCatching {
                zos.close()
                if (uri != null) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    owner.resolver.update(uri, values, null, null)
                }
                true
            }.getOrDefault(false)
        }

        /** Gagal/dibatalkan: tutup dan hapus file setengah jadi. */
        fun abort() {
            if (closed) return
            closed = true
            runCatching { zos.close() }
            // ?.let, bukan if (x != null) { runCatching { x… } }: pengecekan
            // null di luar lambda tidak dibawa masuk ke dalamnya (lambda
            // menangkap nilai mentah, jadi masih dianggap nullable).
            uri?.let { runCatching { owner.resolver.delete(it, null, null) } }
            file?.let { runCatching { it.delete() } }
            Log.i(TAG, "zip dibatalkan, file setengah jadi dihapus")
        }
    }

    @Deprecated(
        "Menampung semua gambar di RAM (risiko OOM). Pakai openZip.",
        ReplaceWith("openZip(zipFolderPath, zipName)")
    )
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

    /** Baca teks file yang sudah ditulis sebelumnya (pulihkan cadangan). */
    fun readText(folderPath: String, fileName: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = findExisting(collection, fileName, fullRelativePath(folderPath))
            ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
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
        name.endsWith(".bmp", true) -> "image/bmp"
        name.endsWith(".avif", true) -> "image/avif"
        name.endsWith(".heic", true) -> "image/heic"
        name.endsWith(".jxl", true) -> "image/jxl"
        name.endsWith(".zip", true) -> "application/zip"
        name.endsWith(".txt", true) -> "text/plain"
        else -> "application/octet-stream"
    }
}
