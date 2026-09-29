package com.trialfetch.app.core

/** Antarmuka yang harus diimplementasikan tiap sumber komik. */
interface ComicSource {
    val source: Source

    suspend fun search(query: String): List<SearchResult>

    suspend fun series(comicId: String): SeriesInfo

    suspend fun chapter(url: String): ChapterPage
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
    .replace("&nbsp;", " ")
    .replace(Regex("\\s+"), " ")
    .trim()

internal fun Regex.firstGroupOrNull(html: String, group: Int = 1): String? =
    this.find(html)?.groupValues?.getOrNull(group)?.takeIf { it.isNotBlank() }
