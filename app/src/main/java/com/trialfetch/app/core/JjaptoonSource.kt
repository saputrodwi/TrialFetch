package com.trialfetch.app.core

/**
 * Jjaptoon.
 *
 * Scrape HTML biasa (tidak ada JSON API untuk chapter). Diport dari Trial
 * Fetch web (worker.js: searchJjaptoon + handler /comics/{id} + /chapters/{id}).
 *
 *  - Cari:    GET {origin}/search?q={q}  (server-render, regex anchor)
 *  - Series:  GET {origin}/comics/{id}  (link /chapters/{id} + judul di <p>)
 *  - Chapter: GET {origin}/chapters/{id}  (<img> yang alt-nya diakhiri nomor
 *             halaman — ciri konten yang stabil apa pun CDN-nya)
 *
 * Domain bernomor (003, 005, 008, dst, TLD bisa beda) dan sering berganti,
 * jadi origin tidak di-hardcode satu: default www.jjaptoon008.com, fallback
 * jjaptoon008.com, dan origin dari URL user selalu dicoba dulu.
 */
class JjaptoonSource(private val http: HttpClient) : ComicSource {

    override val source = Source.JJAPTOON

    private companion object {
        const val DEFAULT_ORIGIN = "https://www.jjaptoon008.com"
        const val FALLBACK_ORIGIN = "https://jjaptoon008.com"

        fun headers(origin: String): Map<String, String> = mapOf(
            "User-Agent" to
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
            "Referer" to "$origin/"
        )
    }

    private val searchRe = Regex(
        """<a href="(/comics/\d+)" class="group relative block aspect-\[5/8\][\s\S]*?""" +
            """<img[^>]*src="([^"]+)"[^>]*alt="([^"]*)"""",
        RegexOption.IGNORE_CASE
    )

    private val chapterLinkRe = Regex(
        """href="/chapters/(\d+)"[^>]*data-chapter-list-id="\d+""" +
            """[\s\S]*?<p class="truncate text-sm font-black text-zinc-100">([^<]+)</p>"""
    )

    private val imgTagRe = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val srcRe = Regex("""\bsrc="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val altRe = Regex("""\balt="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val trailingNumRe = Regex("""\d+\s*$""")
    private val coverFullRe = Regex("""https://img\.jjaptoon[^"'\s]+/covers/[^"'\s]+""")
    private val coverRe = Regex("""/covers/[^"'\s]+""")

    /** Host gambar dari origin halaman: www.X -> img.X. */
    private fun imgHost(origin: String): String {
        val host = origin.substringAfter("://")
        val img = if (host.startsWith("www.")) "img." + host.removePrefix("www.") else "img.$host"
        return origin.substringBefore("://") + "://" + img
    }

    private suspend fun fetchHtml(url: String, origin: String): String {
        return try {
            http.getHtmlWithHeaders(url, headers(origin))
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi Jjaptoon: ${e.message}", e)
        }
    }

    private fun originOf(url: String): String {
        val m = Regex("""^(https?://[^/]*jjaptoon[^/]*)""", RegexOption.IGNORE_CASE).find(url)
        return m?.groupValues?.get(1) ?: DEFAULT_ORIGIN
    }

    override suspend fun search(query: String): List<SearchResult> {
        val html = fetchHtml(
            "$DEFAULT_ORIGIN/search?q=${query.urlEncode()}",
            DEFAULT_ORIGIN
        )
        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (m in searchRe.findAll(html)) {
            if (out.size >= 30) break
            val path = m.groupValues[1]
            if (!seen.add(path)) continue
            out += SearchResult(
                source = source,
                comicId = path.substringAfterLast("/"),
                title = cleanText(m.groupValues[3].ifBlank { path }),
                author = "",
                coverUrl = m.groupValues[2].trim(),
                seriesUrl = "$DEFAULT_ORIGIN$path"
            )
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val origin = DEFAULT_ORIGIN
        val html = fetchHtml("$origin/comics/$comicId", origin)

        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        for (m in chapterLinkRe.findAll(html)) {
            val id = m.groupValues[1]
            if (!seen.add(id)) continue
            chapters += Chapter(
                chapterId = id,
                title = cleanText(m.groupValues[2]),
                chapterNumber = Regex("""(\d+)\s*화""").find(m.groupValues[2])
                    ?.groupValues?.get(1)?.toIntOrNull(),
                url = "$origin/chapters/$id"
            )
        }
        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter Jjaptoon kosong — struktur situs mungkin berubah.")
        }

        val title = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText)
            ?.substringBefore(" - ")?.takeIf { it.isNotBlank() }
            ?: "Komik $comicId"
        // Utamakan URL lengkap (host img.*), karena path relatif yang
        // ditempel ke origin halaman (www.*) menghasilkan 404 — gambar
        // cover memang dilayani dari host img.*, bukan www.*.
        val cover = coverFullRe.find(html)?.value
            ?: coverRe.find(html)?.value?.let { imgHost(origin) + it }
            ?: ""

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            coverUrl = cover,
            synopsis = extractMetaDescription(html),
            latestChapterTitle = chapters.firstOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val chapterId = Regex("""/chapters/(\d+)""").find(url)?.groupValues?.get(1)
            ?: throw SourceException("URL chapter Jjaptoon tidak dikenali")
        val origin = originOf(url)
        val candidates = listOf(
            "$origin/chapters/$chapterId",
            "$FALLBACK_ORIGIN/chapters/$chapterId"
        ).distinct()

        var lastError: Exception? = null
        for (pageUrl in candidates) {
            val pageOrigin = originOf(pageUrl)
            val html = try {
                http.getHtmlWithHeaders(pageUrl, headers(pageOrigin))
            } catch (e: Exception) {
                lastError = e
                continue
            }
            val page = parseChapterImages(html, chapterId)
            if (page != null) return page
            lastError = SourceException("Tidak ada gambar di halaman chapter Jjaptoon.")
        }
        throw lastError ?: SourceException("Chapter Jjaptoon tidak ditemukan")
    }

    private fun parseChapterImages(html: String, chapterId: String): ChapterPage? {
        val pageTitle = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val parts = pageTitle.split(" - ").map(::cleanText)
        val comicTitle = parts.getOrNull(0).orEmpty()
        val chapterTitle = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Chapter $chapterId"

        val images = ArrayList<ImageRef>()
        for (tag in imgTagRe.findAll(html).map { it.value }) {
            val src = srcRe.find(tag)?.groupValues?.get(1) ?: continue
            val alt = altRe.find(tag)?.groupValues?.get(1) ?: continue
            // Alt konten selalu diakhiri nomor halaman ("... 50화 12").
            // Logo dan ikon UI tidak berpola begini sehingga tersaring.
            if (trailingNumRe.find(alt) == null) continue
            images += ImageRef(src, images.size + 1)
        }
        if (images.isEmpty()) return null
        return ChapterPage(
            source = source,
            comicTitle = comicTitle,
            chapterTitle = chapterTitle,
            images = images
        )
    }
}
