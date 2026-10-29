package com.trialfetch.app.core

/**
 * MangaGeko (www.mgeko.cc) — baca manga / manhwa / manhua bahasa Inggris.
 *
 *  - Cari:    GET {base}/search/?search={q} → daftar <li class="novel-item">.
 *             Setiap item: <a href="/manga/{slug}/" title>, cover dari <img
 *             data-src="/media/manga_covers/...">, judul <h4 class="novel-title">,
 *             penulis <h6>Author(S): ..., chapter terakhir <strong> Chapters N-...</strong>.
 *  - Series:  GET {base}/manga/{slug}/ (meta: h1/og:title/og:image/og:description),
 *             GET {base}/manga/{slug}/all-chapters/ (daftar <li data-chapterno> dengan
 *             <a href="/reader/en/{slug}-chapter-{n}-..."> dan <strong class="chapter-title">).
 *  - Chapter: GET /reader/en/{slug}-chapter-{n}-{suffix}/ → <img class="mgr-page">.
 *
 * Catatan: chapter nomor seperti "336-eng-li", "129-1-eng-li", "143-s2-start-eng-li"
 * ditangkap dari bagian angka depan teks ("336", "129", "143").
 */
class MangaGekoSource(private val http: HttpClient) : ComicSource {

    override val source = Source.MANGAGO

    private val base = "https://www.mgeko.cc"

    override val noImageReferer: Boolean get() = false

    // --- Regex pencarian ---
    private val searchItemRe = Regex(
        """<li class="novel-item">[\s\S]*?</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val searchLinkRe = Regex(
        """<a[^>]*href="([^"]+)[^"]*>""",
        RegexOption.IGNORE_CASE
    )
    private val searchCoverRe = Regex(
        """<img[^>]*data-src=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val searchTitleRe = Regex(
        """<h4[^>]*class="[^"]*novel-title[^"]*"[^>]*>([\s\S]*?)</h4>""",
        RegexOption.IGNORE_CASE
    )
    private val searchAuthorRe = Regex(
        """Author\(S\):\s*([^<\r\n]+)""",
        RegexOption.IGNORE_CASE
    )

    // --- Regex daftar chapter ---
    private val chapterItemRe = Regex(
        """<li[^>]*data-chapterno[^>]*>[\s\S]*?</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val chapterLinkRe = Regex(
        """<a[^>]*href="([^"]+)[^"]*>""",
        RegexOption.IGNORE_CASE
    )
    private val chapterStrongTitleRe = Regex(
        """<strong[^>]*>([\s\S]*?)</strong>""",
        RegexOption.IGNORE_CASE
    )

    private fun abs(url: String): String =
        if (url.startsWith("http")) url
        else base + (if (url.startsWith("/")) "" else "/") + url

    override suspend fun search(query: String): List<SearchResult> {
        val html = try {
            http.getHtml("$base/search/?search=${query.urlEncode()}", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi MangaGeko: ${e.message}", e)
        }
        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (m in searchItemRe.findAll(html)) {
            if (out.size >= 30) break
            val item = m.value
            val href = searchLinkRe.find(item)?.groupValues?.get(1)?.trim().orEmpty()
            val slug = Regex("""/manga/([^/?#]+)""").find(href)?.groupValues?.get(1)?.trim() ?: continue
            if (slug.isEmpty() || !seen.add(slug)) continue
            val title = searchTitleRe.find(item)?.groupValues?.get(1)?.let(::cleanText)?.trim().orEmpty()
            if (title.isBlank()) continue
            val cover = searchCoverRe.find(item)?.groupValues?.get(1)?.trim().orEmpty()
            out += SearchResult(
                source = source,
                comicId = slug,
                title = title,
                author = searchAuthorRe.find(item)?.groupValues?.get(1)?.trim().orEmpty(),
                coverUrl = if (cover.isBlank()) "" else if (cover.startsWith("http")) cover else abs(cover),
                seriesUrl = "$base/manga/$slug/"
            )
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val seriesHtml = try {
            http.getHtml("$base/manga/$comicId/", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil metadata series: ${e.message}", e)
        }
        val title = Regex(
            """<h1[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>"""
        ).find(seriesHtml)?.groupValues?.get(1)?.let(::cleanText)?.trim().takeIf { it.isNotBlank() }
            ?: comicId
        val coverUrl = Regex(
            """<meta[^>]+property="og:image"[^>]+content="([^"]+)[^>]*>"""
        ).find(seriesHtml)?.groupValues?.get(1)?.trim().orEmpty()
        val author = Regex(
            """Author\(S\):\s*([^<\r\n]+)""",
            RegexOption.IGNORE_CASE
        ).find(seriesHtml)?.groupValues?.get(1)?.trim().orEmpty()
        val synopsis = Regex(
            """<meta[^>]+property="og:description"[^>]+content="([^"]+)[^>]*>"""
        ).find(seriesHtml)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty()
            .ifBlank { extractMetaDescription(seriesHtml) }

        // Daftar chapter lengkap lewat halaman all-chapters.
        val chaptersHtml = try {
            http.getHtml("$base/manga/$comicId/all-chapters/", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }
        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        for (m in chapterItemRe.findAll(chaptersHtml)) {
            val item = m.value
            val href = chapterLinkRe.find(item)?.groupValues?.get(1)?.trim().orEmpty()
            if (href.isEmpty()) continue
            val absUrl = abs(href)
            val titleRaw = chapterStrongTitleRe.find(item)?.groupValues?.get(1)?.trim().orEmpty()
            val chapterNum = Regex("""(\d+)""").find(titleRaw)?.groupValues?.get(1)?.toIntOrNull()
            if (chapterNum == null) continue
            if (!seen.add(absUrl)) continue
            chapters += Chapter(
                chapterId = chapterNum.toString(),
                title = cleanText(titleRaw).ifBlank { "Chapter $chapterNum" },
                chapterNumber = chapterNum,
                url = absUrl
            )
        }
        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter MangaGeko kosong — struktur situs mungkin berubah.")
        }
        // Sortir eksplisit (terbaru di atas) — jangan andalkan urutan halaman.
        chapters.sortByDescending { it.chapterNumber ?: 0 }

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            author = author,
            coverUrl = coverUrl,
            synopsis = synopsis,
            latestChapterTitle = chapters.firstOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = abs(url)
        val html = try {
            http.getHtml(absolute, referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil chapter: ${e.message}", e)
        }

        val comicTitle = Regex(
            """<h1[^>]*>[\s\S]*?<a[^>]*>([^<]+)</a>"""
        ).find(html)?.groupValues?.get(1)?.let(::cleanText)?.trim().orEmpty()
        val pageTitle = Regex(
            """<title>([^<]*)</title>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val chapterTitle = Regex(
            """<h2[^>]*>([^<]+)</h2>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty().ifBlank { pageTitle.trim() }

        val images = ArrayList<ImageRef>()
        val imgRe = Regex(
            """<img[^>]*\bmgr-page\b[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        for (m in imgRe.findAll(html)) {
            val tag = m.value
            val src = Regex(
                """src=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(tag)?.groupValues?.get(1)?.trim().orEmpty()
            if (src.isEmpty() || !src.startsWith("http")) continue
            if (images.any { it.url == src }) continue
            val pageNum = Regex(
                """id=["']image-(\d+)["']""",
                RegexOption.IGNORE_CASE
            ).find(tag)?.groupValues?.get(1)?.toIntOrNull()
            images += ImageRef(src, pageNum ?: images.size + 1, false)
        }
        if (images.isEmpty()) {
            throw SourceException("Tidak ada gambar di halaman chapter MangaGeko.")
        }

        return ChapterPage(
            source = source,
            comicTitle = comicTitle,
            chapterTitle = chapterTitle,
            images = images
        )
    }

    override suspend fun seriesIdFromChapterUrl(chapterUrl: String): String? {
        val absolute = if (chapterUrl.startsWith("http")) chapterUrl
        else "$base${if (chapterUrl.startsWith("/")) "" else "/"}${chapterUrl}"
        val slug = Regex(
            """(?:/manga/|/reader/en/)([a-zA-Z0-9\-]+)"""
        ).find(absolute)?.groupValues?.get(1) ?: return null
        // "/reader/en/nano-machine-mg12-chapter-336-eng-li/" → "nano-machine-mg12"
        val mangaId = slug.replace(Regex("""-chapter-[\d\-_a-z]*$"""), "")
        return mangaId.takeIf { it.isNotBlank() }
    }
}
