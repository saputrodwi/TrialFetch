package com.trialfetch.app.core

/** Sumber komik yang didukung. */
enum class Source(val id: String, val displayName: String) {
    BAOZIMH("baozimh", "Baozimh"),
    MANWANG("manwang", "Manwang"),
    WMANHUA("wmanhua", "Wmanhua"),
    KOUDAIMH("koudaimh", "Koudaimh"),
    JJABTOON("jjabtoon", "Jjabtoon"),
    JJAPTOON("jjaptoon", "Jjaptoon"),
    GOODTOON("goodtoon", "Goodtoon"),
    RUMAN("rumanhua", "Rumanhua"),
    MANHUAHAO("manhuahao", "Manhuahao");

    companion object {
        fun from(id: String): Source? = entries.firstOrNull { it.id == id }
    }
}

data class SearchResult(
    val source: Source,
    val comicId: String,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val seriesUrl: String = ""
)

data class Chapter(
    val chapterId: String,
    val title: String,
    val chapterNumber: Int? = null,
    val url: String
)

data class SeriesInfo(
    val source: Source,
    val comicId: String,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val status: String = "",
    val synopsis: String = "",
    val latestChapterTitle: String = "",
    val chapters: List<Chapter>
)

data class ChapterPage(
    val source: Source,
    val comicTitle: String,
    val chapterTitle: String,
    /** URL gambar mentah. Untuk sumber terenkripsi, ini URL ciphertext. */
    val images: List<ImageRef>
)

/**
 * Referensi satu gambar. [needsDecrypt] menandai sumber yang mengbalas
 * AES-128-CBC terenkripsi (Manwa) sehingga file harus didekripsi dulu
 * sebelum bisa disimpan sebagai gambar.
 */
data class ImageRef(
    val url: String,
    val page: Int,
    val needsDecrypt: Boolean = false
)

/**
 * Satu halaman untuk reader: bisa file lokal (file://, content://)
 * atau URL remote (http...). [headers] hanya dipakai untuk remote
 * (User-Agent + Referer penangkal hotlink), dikosongkan untuk lokal.
 */
data class ReaderPage(
    val uri: String,
    val headers: Map<String, String> = emptyMap()
)
