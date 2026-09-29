package com.trialfetch.app.core

/**
 * Baozimh / TW Manga.
 *
 * Mengambil data dari www.twmanga.com (mirror resmi) karena www.baozimh.com
 * memasang gatekeeper challenge yang menolak request server-side. Struktur
 * keduanya identik.
 *
 * Alur chapter: /user/page_direct?... di-redirect ke
 * /comic/chapter/{slug}/{section}_{chapter-1}.html (chapter 0-indexed di URL).
 */
class BaozimhSource(private val http: HttpClient) : ComicSource {

    override val source = Source.BAOZIMH

    private val base = "https://www.twmanga.com"

    private val searchRe = Regex(
        """<a href="(/comic/([a-z0-9\-]+))" title="([^"]+)"[^>]*class="comics-card__poster""",
        RegexOption.IGNORE_CASE
    )
    private val coverRe = Regex(
        """<amp-img alt="[^"]*"[^>]*src="([^"]+)\"""",
        RegexOption.IGNORE_CASE
    )
    private val authorRe = Regex(
        """class="author[^"]*"[^>]*>\s*<a[^>]*>\s*([^<]+)""",
        RegexOption.IGNORE_CASE
    )
    private val statusRe = Regex(
        """<span class="new-tag">([^<]+)</span>|连载中|完結""",
        RegexOption.IGNORE_CASE
    )
    private val chapterRe = Regex(
        """href="(/user/page_direct\?comic_id=([^&"]+)&amp;section_slot=(\d+)&amp;chapter_slot=(\d+))""" +
            """[^>]*class="comics-chapters__item"[^>]*>([\s\S]{0,200}?)</a>"""
    )
    private val imageRe = Regex(
        """https://[a-z0-9.\-]*bzcdn\.net/scomic/[^"'\s]+?\.(?:jpg|jpeg|png|webp)""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun search(query: String): List<SearchResult> {
        val html = try {
            http.getHtml("$base/search?q=${query.urlEncode()}", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi Baozimh: ${e.message}", e)
        }

        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        searchRe.findAll(html).forEach { m ->
            val path = m.groupValues[1]
            val slug = m.groupValues[2]
            val title = cleanText(m.groupValues[3])
            if (!seen.add(path)) return@forEach
            if (title.isBlank() || slug == "sitemap") return@forEach

            val block = html.substring(
                m.range.first,
                (m.range.last + 600).coerceAtMost(html.length)
            )
            val cover = coverRe.find(block)?.groupValues?.get(1)
                ?.replace("&amp;", "&")
                ?: ""

            results += SearchResult(
                source = source,
                comicId = slug,
                title = title,
                author = authorRe.find(block)?.groupValues?.get(1)?.let(::cleanText).orEmpty(),
                coverUrl = cover,
                seriesUrl = "$base$path"
            )
        }

        if (results.isEmpty()) {
            throw SourceException("Tidak ada hasil untuk \"$query\"")
        }
        return results.take(30)
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val slug = comicId.removePrefix("/comic/").substringBefore('?')
        val html = try {
            http.getHtml("$base/comic/$slug", referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }

        val chapters = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()
        chapterRe.findAll(html).forEach { m ->
            val path = m.groupValues[1].replace("&amp;", "&")
            val chapterSlot = m.groupValues[4]
            if (!seen.add(path)) return@forEach
            val title = cleanText(m.groupValues[5].replace(Regex("<[^>]+>"), " "))
            chapters += Chapter(
                chapterId = path,
                title = title.ifBlank { "Chapter $chapterSlot" },
                chapterNumber = chapterSlot.toIntOrNull(),
                url = "$base$path"
            )
        }

        if (chapters.isEmpty()) {
            throw SourceException(
                "Daftar chapter kosong — struktur situs mungkin berubah."
            )
        }

        // Situs mengurutkan terlama -> terbaru; dibalik supaya terbaru di atas
        // supaya konsisten dengan sumber lain.
        chapters.reverse()

        val title = Regex("""<h1[^>]*>\s*([^<]+?)\s*</h1>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText)
            ?: comicId
        val cover = coverRe.find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")
            ?: ""
        val author = authorRe.find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()

        return SeriesInfo(
            source = source,
            comicId = slug,
            title = title,
            author = author,
            coverUrl = cover,
            latestChapterTitle = chapters.firstOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = if (url.startsWith("http")) url else "$base$url"
        val html = try {
            // page_direct mem-redirect ke halaman chapter; HttpClient
            // mengikuti redirect secara otomatis.
            http.getHtml(absolute, referer = base, mobile = false)
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil chapter: ${e.message}", e)
        }

        val images = imageRe.findAll(html)
            .map { it.value }
            .distinct()
            .toList()

        if (images.isEmpty()) {
            throw SourceException("Tidak ada gambar di halaman chapter.")
        }

        val pageTitle = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        // Judul: "{chapter} - {komik} - 包子漫畫"
        val parts = pageTitle.split(" - ").map(::cleanText)
        val chapterTitle = parts.getOrNull(0).orEmpty()
        val comicTitle = parts.getOrNull(1).orEmpty()

        return ChapterPage(
            source = source,
            comicTitle = comicTitle.ifBlank { pageTitle },
            chapterTitle = chapterTitle,
            images = images.mapIndexed { i, u -> ImageRef(u, i + 1) }
        )
    }
}

internal fun String.urlEncode(): String =
    java.net.URLEncoder.encode(this, "UTF-8")
