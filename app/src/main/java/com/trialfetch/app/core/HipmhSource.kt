package com.trialfetch.app.core

/**
 * HipMH (m.hipmh.com) — 嬉皮漫畫 (HipManga), komik Cina
 * (多語言: zh / en / ja / ko).
 *
 *  - Search:    GET {base}/search?keyword={q} → cari link <a href=".../works/...">.
 *  - Series:    GET {base}/works/{id}-{slug}/ (meta: h1/og:image/og:description).
 *  - Chapter:   /chapter/go?hid={...} (Astro SPA yang redirect lewat JS ke
 *                reader host, dengan potensi Cloudflare challenge).
 *
 * Catatan: chapter flow memakai Astro SPA + redirect JS + Cloudflare sehingga
 * pola scraping untuk chapter perlu validasi lebih lanjut lewat reader host.
 */
class HipmhSource(private val http: HttpClient) : ComicSource {

    override val source = Source.HIPMH

    private val base = "https://m.hipmh.com"

    override val noImageReferer: Boolean get() = false

    // --- Regex pencarian ---
    private val worksLinkRe = Regex(
        """href="([^"]*)/works/([a-zA-Z0-9\-]+)""",
        RegexOption.IGNORE_CASE
    )
    private val h1Re = Regex(
        """<h1[^>]*>([\s\S]*?)</h1>""",
        RegexOption.IGNORE_CASE
    )
    private val ogImageRe = Regex(
        """<meta[^>]+property="og:image"[^>]+content="([^"]+)[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val ogDescRe = Regex(
        """<meta[^>]+property="og:description"[^>]+content="([^"]+)[^>]*>""",
        RegexOption.IGNORE_CASE
    )

    // --- Regex daftar chapter ---
    private val chapterLinkRe = Regex(
        """href="([^"]*chapter/[^"]*)""",
        RegexOption.IGNORE_CASE
    )
    private val hidRe = Regex(
        """hid=([^&]+)""",
        RegexOption.IGNORE_CASE
    )

    private fun abs(url: String): String =
        if (url.startsWith("http")) url
        else base + (if (url.startsWith("/")) "" else "/") + url

    override suspend fun search(query: String): List<SearchResult> {
        val html = try {
            http.getHtml("$base/search?keyword=${query.urlEncode()}", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi HipMH: ${e.message}", e)
        }
        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (m in worksLinkRe.findAll(html)) {
            if (out.size >= 30) break
            val href = m.groupValues?.get(1)?.trim().orEmpty()
            val slug = m.groupValues?.get(2)?.trim() ?: continue
            if (slug.isEmpty() || !seen.add(slug)) continue
            out += SearchResult(
                source = source,
                comicId = slug,
                title = h1Re.find(html)?.groupValues?.get(1)?.let(::cleanText)?.trim().orEmpty()
                    .ifBlank { slug.replace("-", " ") },
                coverUrl = ogImageRe.find(html)?.groupValues?.get(1)?.trim().orEmpty(),
                seriesUrl = "$base/works/$slug/"
            )
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val seriesHtml = try {
            http.getHtml("$base/works/$comicId/", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil metadata series: ${e.message}", e)
        }
        val title = h1Re.find(seriesHtml)?.groupValues?.get(1)?.let(::cleanText)?.trim().takeIf { it.isNotBlank() }
            ?: comicId
        val coverUrl = ogImageRe.find(seriesHtml)?.groupValues?.get(1)?.trim().orEmpty()
        val synopsis = ogDescRe.find(seriesHtml)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty()
            .ifBlank { extractMetaDescription(seriesHtml) }

        val chaptersHtml = try {
            http.getHtml("$base/works/$comicId/", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }
        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        for (m in chapterLinkRe.findAll(chaptersHtml)) {
            val href = m.groupValues?.get(1)?.trim().orEmpty()
            if (href.isEmpty()) continue
            val absUrl = abs(href)
            val hid = hidRe.find(absUrl)?.groupValues?.get(1)?.trim()
            if (hid.isNullOrEmpty()) continue
            if (!seen.add(absUrl)) continue
            val chapterNum = Regex("""(\d+)""").find(hid)?.groupValues?.get(1)?.toIntOrNull()
            chapters += Chapter(
                chapterId = hid,
                title = "Chapter ${chapterNum ?: ""}".trim(),
                chapterNumber = chapterNum,
                url = absUrl
            )
        }
        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter HipMH kosong — struktur situs mungkin berubah.")
        }
        chapters.sortByDescending { it.chapterNumber ?: 0 }

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            author = "",
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
        val comicTitle = h1Re.find(html)?.groupValues?.get(1)?.let(::cleanText)?.trim().orEmpty()
        val pageTitle = Regex(
            """<title>([^<]*)</title>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val chapterTitle = Regex(
            """<h2[^>]*>([^<]+)</h2>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty().ifBlank { pageTitle.trim() }

        val images = ArrayList<ImageRef>()
        val imgTagRe = Regex(
            """<img\b[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        for (m in imgTagRe.findAll(html)) {
            val tag = m.value
            val src = Regex(
                """src=["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(tag)?.groupValues?.get(1)?.trim().orEmpty()
            if (src.isEmpty() || !src.startsWith("http")) continue
            if (src.startsWith("data:")) continue
            if (src.contains("static") || src.contains("loading") || src.contains("avatar") ||
                src.contains("logo") || src.contains("footer") || src.contains("credits") || src.contains("svg")
            ) continue
            if (images.any { it.url == src }) continue
            val pageNum = Regex(
                """id=["']image-(\d+)["']""",
                RegexOption.IGNORE_CASE
            ).find(tag)?.groupValues?.get(1)?.toIntOrNull()
            images += ImageRef(src, pageNum ?: images.size + 1, false)
        }
        if (images.isEmpty()) {
            throw SourceException("Tidak ada gambar di halaman chapter HipMH.")
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
        val works = Regex(
            """/works/([a-zA-Z0-9\-]+)""",
            RegexOption.IGNORE_CASE
        ).find(absolute)?.groupValues?.get(1)
        if (works != null) return works
        val mangaId = Regex(
            """[?&]m=(\d+)""",
            RegexOption.IGNORE_CASE
        ).find(absolute)?.groupValues?.get(1)
        return mangaId
    }
}
