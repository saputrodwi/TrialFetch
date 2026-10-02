package com.trialfetch.app.core

/** Antarmuka yang harus diimplementasikan tiap sumber komik. */
interface ComicSource {
    val source: Source

    suspend fun search(query: String): List<SearchResult>

    suspend fun series(comicId: String): SeriesInfo

    suspend fun chapter(url: String): ChapterPage

    /**
     * Selesaikan URL chapter menjadi comicId series-nya, bila polanya
     * memungkinkan. Default null (tidak didukung) — override di sumber
     * yang ID bukunya terbaca dari URL atau halaman chapter-nya.
     */
    suspend fun seriesIdFromChapterUrl(chapterUrl: String): String? = null
}

/** Error yang aman ditampilkan ke pengguna (bukan exception mentah). */
class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun fail(message: String): Nothing = throw SourceException(message)

/** Bersihkan teks HTML: entity di-decode dan whitespace dirapatkan. */
internal fun cleanText(raw: String): String = raw
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace(Regex("""&#(\d+);""")) { m ->
        m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
    }
    .replace(Regex("""&#x([0-9a-fA-F]+);""", RegexOption.IGNORE_CASE)) { m ->
        m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
    }
    .replace("&apos;", "'")
    .replace("&nbsp;", " ")
    .replace(Regex("\\s+"), " ")
    .trim()

internal fun Regex.firstGroupOrNull(html: String, group: Int = 1): String? =
    this.find(html)?.groupValues?.getOrNull(group)?.takeIf { it.isNotBlank() }

/**
 * Ambil sinopsis dari meta description (dipakai hampir semua situs
 * sebagai ringkasan halaman series). Mengembalikan string kosong bila
 * tidak ada — pemanggil tidak perlu try/catch khusus.
 */
internal fun extractMetaDescription(html: String): String {
    val m = Regex(
        """<meta[^>]+(?:name|property)="[^"]*(?:description|og:description)"[^>]+content="([^"]+)"""",
        RegexOption.IGNORE_CASE
    ).find(html) ?: Regex(
        """<meta[^>]+content="([^"]+)"[^>]+(?:name|property)="[^"]*(?:description|og:description)"""",
        RegexOption.IGNORE_CASE
    ).find(html)
    return m?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
}
