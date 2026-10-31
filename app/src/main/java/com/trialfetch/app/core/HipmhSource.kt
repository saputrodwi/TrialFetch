package com.trialfetch.app.core

/**
 * HipMH (m.hipmh.com) — 嬉皮漫畫, platform komik Cina multilingual (zh/en/ja/ko).
 *
 *  - Search:    Halaman search bersifat SPA; fallback ke katalog (/new-releases,
 *               /popularity, /explore) lalu filter berdasarkan keyword.
 *  - Series:    GET {base}/works/{slug}/ (h1, data-cover-url, og:description).
 *  - Chapter:   /chapter/go?hid={hid}&m={mangaId} → redirect ke reader.hipmh.top.
 */
class HipmhSource(private val http: HttpClient) : ComicSource {

    override val source = Source.HIPMH
    private val base = "https://m.hipmh.com"
    override val noImageReferer: Boolean get() = false

    private val worksLinkRe = Regex("""href="(/works/[a-zA-Z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val coverImgRe = Regex("""data-cover-url="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val h1Re = Regex("""<h1[^>]*>([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE)
    private val ogImageRe = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)[^>]*>""", RegexOption.IGNORE_CASE)
    private val ogDescRe = Regex("""<meta[^>]+property="og:description"[^>]+content="([^"]+)[^>]*>""", RegexOption.IGNORE_CASE)
    private val chapterLinkRe = Regex("""href="(/chapter/go\?hid=[^"]+)"[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    private val hidRe = Regex("""[?&]hid=([^&]+)""", RegexOption.IGNORE_CASE)
    private val mangaIdRe = Regex("""[?&]m=([^&]+)""", RegexOption.IGNORE_CASE)

    private fun abs(url: String) = if (url.startsWith("http")) url else base + url.takeIf { it.startsWith("/") } ?: "/$url"

    private suspend fun catalogPages(): List<String> = listOf("/new-releases", "/popularity", "/explore")
        .mapNotNull { path -> runCatching { http.getHtml("$base$path", referer = base, mobile = false) }.getOrNull() }

    override suspend fun search(query: String): List<SearchResult> {
        val pages = catalogPages()
        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (html in pages) {
            for (m in worksLinkRe.findAll(html)) {
                val path = m.groupValues?.get(1)?.trim().orEmpty()
                val slug = Regex("""/works/([^/?#]+)""").find(path)?.groupValues?.get(1)?.trim() ?: continue
                if (slug.isEmpty() || !seen.add(slug)) continue
                if (!slug.contains(query, ignoreCase = true) && !slug.replace("-", " ").contains(query, ignoreCase = true)) continue
                val title = h1Re.find(html)?.groupValues?.get(1)?.let(::cleanText)?.trim().orEmpty()
                    .ifBlank { slug.replace("-", " ") }
                val cover = coverImgRe.find(html)?.groupValues?.get(1)?.trim().orEmpty()
                out += SearchResult(source = source, comicId = slug, title = title, coverUrl = cover, seriesUrl = "$base$path/")
                if (out.size >= 30) break
            }
            if (out.size >= 30) break
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val html = try { http.getHtml("$base/works/$comicId/", referer = base, mobile = false) }
        catch (e: Exception) { throw SourceException("Gagal mengambil series: ${e.message}", e) }
        val title = h1Re.find(html)?.groupValues?.get(1)?.let(::cleanText)?.trim()?.takeIf { it.isNotBlank() } ?: comicId
        val coverUrl = coverImgRe.find(html)?.groupValues?.get(1)?.trim().orEmpty()
            .ifBlank { ogImageRe.find(html)?.groupValues?.get(1)?.trim().orEmpty() }
        val synopsis = ogDescRe.find(html)?.groupValues?.get(1)?.let { cleanText(it) }.orEmpty()
            .ifBlank { extractMetaDescription(html) }
        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        for (m in chapterLinkRe.findAll(html)) {
            val href = m.groupValues?.get(1)?.trim().orEmpty()
            if (href.isEmpty()) continue
            val absUrl = abs(href)
            if (!seen.add(absUrl)) continue
            val hid = hidRe.find(href)?.groupValues?.get(1)?.trim().orEmpty()
            val num = if (hid.isNotEmpty()) Regex("""(\d+)""").find(hid)?.groupValues?.get(1)?.toIntOrNull() else null
            chapters += Chapter(chapterId = hid.ifBlank { "ch-${chapters.size + 1}" }, title = "Chapter ${num ?: chapters.size + 1}".trim(), chapterNumber = num, url = absUrl)
        }
        if (chapters.isEmpty()) throw SourceException("Daftar chapter HipMH kosong.")
        chapters.sortByDescending { it.chapterNumber ?: 0 }
        return SeriesInfo(source = source, comicId = comicId, title = title, author = "", coverUrl = coverUrl, synopsis = synopsis, latestChapterTitle = chapters.firstOrNull()?.title.orEmpty(), chapters = chapters)
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = abs(url)
        val hid = hidRe.find(absolute)?.groupValues?.get(1).orEmpty()
        val mangaId = mangaIdRe.find(absolute)?.groupValues?.get(1).orEmpty()
        val readerUrl = if (hid.isNotEmpty()) "https://reader.hipmh.top/chapter/go?hid=$hid&m=$mangaId" else absolute
        val html = try { http.getHtml(readerUrl, referer = base, mobile = false) }
        catch (e: Exception) { throw SourceException("Gagal mengambil chapter: ${e.message}", e) }
        val comicTitle = h1Re.find(html)?.groupValues?.get(1)?.let(::cleanText)?.trim().orEmpty()
        val pageTitle = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val chapterTitle = Regex("""<h2[^>]*>([^<]+)</h2>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty().ifBlank { pageTitle.trim() }
        val images = ArrayList<ImageRef>()
        for (m in Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)) {
            val tag = m.value
            val src = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.trim().orEmpty()
            if (src.isEmpty() || !src.startsWith("http") || src.startsWith("data:")) continue
            if (src.contains("static") || src.contains("loading") || src.contains("avatar") || src.contains("logo") || src.contains("footer") || src.contains("svg")) continue
            if (images.any { it.url == src }) continue
            val pageNum = Regex("""id=["']image-(\d+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull()
            images += ImageRef(src, pageNum ?: images.size + 1, false)
        }
        if (images.isEmpty()) throw SourceException("Chapter HipMH perlu JavaScript — buka di WebView.")
        return ChapterPage(source = source, comicTitle = comicTitle, chapterTitle = chapterTitle, images = images)
    }

    override suspend fun seriesIdFromChapterUrl(chapterUrl: String): String? {
        val absolute = if (chapterUrl.startsWith("http")) chapterUrl else "$base${chapterUrl.takeIf { it.startsWith("/") } ?: "/$chapterUrl"}"
        return Regex("""/works/([a-zA-Z0-9-]+)""", RegexOption.IGNORE_CASE).find(absolute)?.groupValues?.get(1)
            ?: Regex("""[?&]m=([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(absolute)?.groupValues?.get(1)
    }
}
