package com.trialfetch.app.core

/**
 * Rumanhua.
 *
 * Situs kembar Manwang (backend & kunci AES identik). Diport dari Trial
 * Fetch web (worker.js: searchRumanhua + handler /news/{id} + /show/{kode}).
 *
 *  - Cari:    GET {base}/index.php/search?key={q}  (kartu div.item ib)
 *  - Series:  GET {base}/news/{id}  (link /show/{kode} berjudul 第N话)
 *  - Chapter: GET {base}/show/{kode}.html  (blob params terenkripsi,
 *             didekripsi ParamsDecryptor yang dipakai bersama Manwang;
 *             gambar source_id 12 terenkripsi dan dibuka di downloader
 *             lewat flag needsDecrypt)
 */
class RumanhuaSource(private val http: HttpClient) : ComicSource {

    override val source = Source.RUMAN

    private companion object {
        const val BASE = "https://www.rumanhua.org"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0 Mobile Safari/537.36"
    }

    private val searchRe = Regex(
        """<div class="item ib"[^>]*>[\s\S]*?<a href="(/news/\d+)">[\s\S]*?""" +
            """<img class="cover" src="([^"]+)"[^>]*>[\s\S]*?""" +
            """<p class="title"><a[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE
    )

    private val chapterLinkRe = Regex(
        """<a href="(/show/[^"]+)"[^>]*>([^<]*第\d+[^<]*)</a>"""
    )
    private val showCodeRe = Regex("""/show/([^/?#.]+)""")
    private val coverRe = Regex(
        """<img src="([^"]+)" alt="([^"]+)"""",
        RegexOption.IGNORE_CASE
    )
    private val authorRe = Regex("""是一部由([^创<]+)创作""")

    private suspend fun fetchHtml(url: String): String {
        return try {
            http.getHtmlWithHeaders(
                url,
                mapOf(
                    "User-Agent" to MOBILE_UA,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Referer" to "$BASE/"
                )
            )
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi Rumanhua: ${e.message}", e)
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        val html = fetchHtml("$BASE/index.php/search?key=${query.urlEncode()}")

        if ("搜索繁忙" in html) {
            throw SourceException(
                "Rumanhua sedang membatasi pencarian dari jaringan ini. " +
                    "Tempel URL series langsung lewat Buka dari URL."
            )
        }

        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (m in searchRe.findAll(html)) {
            if (out.size >= 30) break
            val path = m.groupValues[1]
            if (!seen.add(path)) continue
            out += SearchResult(
                source = source,
                comicId = path.substringAfterLast("/"),
                title = cleanText(m.groupValues[3]),
                coverUrl = m.groupValues[2].trim(),
                seriesUrl = "$BASE$path"
            )
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val html = fetchHtml("$BASE/news/$comicId")

        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        for (m in chapterLinkRe.findAll(html)) {
            val path = m.groupValues[1]
            val code = showCodeRe.find(path)?.groupValues?.get(1) ?: continue
            if (!seen.add(code)) continue
            chapters += Chapter(
                chapterId = code,
                title = cleanText(m.groupValues[2]),
                chapterNumber = Regex("""第(\d+)""").find(m.groupValues[2])
                    ?.groupValues?.get(1)?.toIntOrNull(),
                url = "$BASE$path"
            )
        }
        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter Rumanhua kosong — struktur situs mungkin berubah.")
        }

        val title = Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText)
            ?.substringBefore("漫画")?.trim()?.takeIf { it.isNotBlank() }
            ?: "Komik $comicId"
        val cover = coverRe.find(html)?.let {
            if (title.isNotBlank() && it.groupValues[2].contains(title.take(8))) {
                it.groupValues[1]
            } else null
        }.orEmpty()
        val author = authorRe.find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            synopsis = extractMetaDescription(html),
            author = author,
            coverUrl = cover,
            latestChapterTitle = chapters.lastOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = if (url.startsWith("http")) url else "$BASE$url"
        val html = fetchHtml(absolute)

        val decoded = ParamsDecryptor.fromHtml(html)
            ?: throw SourceException(
                "Tidak ada data chapter di halaman Rumanhua — struktur situs mungkin berubah."
            )
        if (decoded.images.isEmpty()) {
            throw SourceException("Chapter ini tidak berisi gambar.")
        }

        val pageTitle = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()

        return ChapterPage(
            source = source,
            comicTitle = pageTitle.substringBefore("漫画").trim().ifBlank { pageTitle },
            chapterTitle = pageTitle,
            images = decoded.images.mapIndexed { i, u ->
                ImageRef(u, i + 1, needsDecrypt = decoded.imageEncrypted)
            }
        )
    }
}
