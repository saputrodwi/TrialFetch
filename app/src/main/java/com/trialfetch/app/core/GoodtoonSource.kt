package com.trialfetch.app.core

/**
 * GoodToon.
 *
 * Tema WordPress Madara, server-side render. Diport dari Trial Fetch web
 * (worker.js: searchGoodtoon + handler /manga/{slug}/{n}/ + series AJAX).
 *
 *  - Cari:    GET {base}/?s={q} lewat redirector goodtoon.top (selalu 301
 *             ke domain aktif), fallback www.goodtoon005.com. Kartu hasil
 *             <a class="card">, judul dari alt cover atau div.subject.
 *  - Series:  metadata dari halaman /manga/{slug}/, daftar chapter dari
 *             POST {origin}/manga/{slug}/ajax/chapters/?t=1 yang WAJIB
 *             header X-Requested-With: XMLHttpRequest (kalau tidak, Madara
 *             balas halaman penuh atau reject). URL chapter tidak konsisten
 *             (/{nomor}/ atau /chapter-{nomor}/) jadi dipakai apa adanya.
 *  - Chapter: GET halaman chapter, gambar di <img class="wp-manga-
 *             chapter-img" data-src="..."> (lazyload, bukan src).
 */
class GoodtoonSource(private val http: HttpClient) : ComicSource {

    override val source = Source.GOODTOON

    private companion object {
        const val REDIRECTOR = "https://goodtoon.top"
        const val FALLBACK_BASE = "https://www.goodtoon005.com"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }

    private val cardRe = Regex(
        """<a href="(https?://[^/]*goodtoon[^/]*/manga/[^"?#]+/?)"[^>]*class="card">([\s\S]*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val cardCoverRe = Regex(
        """<img src="(https://img\.goodtoon[^"]+)" alt="([^"]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val cardSubjectRe = Regex(
        """<div class="subject">([^<]+)</div>""",
        RegexOption.IGNORE_CASE
    )
    private val chapterLinkRe = Regex(
        """<a href="([^"]+)">\s*(?:<span class="up-badge-inline">UP</span>)?([^<]*)</a>"""
    )
    private val chapterIdRe = Regex("""/(?:chapter-)?(\d+)/?(?:[?#].*)?$""")
    private val imgTagRe = Regex(
        """<img\b[^>]*class="[^"]*\bwp-manga-chapter-img\b[^"]*"[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val dataSrcRe = Regex("""\bdata-src="([^"]+)"""", RegexOption.IGNORE_CASE)

    private fun pageHeaders(origin: String): Map<String, String> = mapOf(
        "User-Agent" to DESKTOP_UA,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer" to "$origin/"
    )

    override suspend fun search(query: String): List<SearchResult> {
        val bases = listOf(REDIRECTOR, FALLBACK_BASE)
        var html: String? = null
        var lastError: Exception? = null
        for (base in bases) {
            html = try {
                http.getHtmlWithHeaders(
                    "$base/?s=${query.urlEncode()}",
                    pageHeaders(base) - "Accept-Language" + ("Accept-Language" to "ko-KR,ko;q=0.9")
                )
            } catch (e: Exception) {
                lastError = e
                continue
            }
            break
        }
        val page = html ?: throw SourceException(
            "Pencarian GoodToon gagal: ${lastError?.message}"
        )

        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (m in cardRe.findAll(page)) {
            if (out.size >= 30) break
            val pageUrl = m.groupValues[1]
            if (!seen.add(pageUrl)) continue
            val inner = m.groupValues[2]
            val cover = cardCoverRe.find(inner)
            val subject = cardSubjectRe.find(inner)?.groupValues?.get(1)?.trim().orEmpty()
            var title = cover?.groupValues?.get(2)?.trim().orEmpty()
            if (title.isBlank()) title = subject
            if (title.isBlank()) continue
            val slug = Regex("""/manga/([^/?#]+)""").find(pageUrl)?.groupValues?.get(1)
                ?: continue
            out += SearchResult(
                source = source,
                comicId = slug.trimEnd('/'),
                title = cleanText(title),
                coverUrl = cover?.groupValues?.get(1).orEmpty(),
                seriesUrl = pageUrl
            )
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        // Domain aktif bisa berganti (004 -> 005, dst); web memakai
        // redirector goodtoon.top untuk pencarian, tapi endpoint AJAX harus
        // satu origin dengan series-nya. Dipakai fallback yang terakhir
        // terverifikasi hidup; URL chapter dari hasil pencarian/ajax sudah
        // membawa origin aktif masing-masing sehingga chapter() tetap jalan
        // walau domain pindah.
        val origin = FALLBACK_BASE
        val ajaxUrl = "$origin/manga/$comicId/ajax/chapters/?t=1"

        val chaptersHtml = try {
            http.postEmpty(
                ajaxUrl,
                mapOf(
                    "Accept" to "*/*",
                    "X-Requested-With" to "XMLHttpRequest",
                    "User-Agent" to DESKTOP_UA,
                    "Referer" to "$origin/manga/$comicId/"
                )
            )
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }

        val chapters = ArrayList<Chapter>()
        val seen = HashSet<String>()
        for (m in chapterLinkRe.findAll(chaptersHtml)) {
            val href = m.groupValues[1]
            val id = chapterIdRe.find(href)?.groupValues?.get(1) ?: continue
            val abs = if (href.startsWith("http")) href else "$origin$href"
            if (!seen.add(abs)) continue
            chapters += Chapter(
                chapterId = id,
                title = cleanText(m.groupValues[2].ifBlank { "Chapter $id" }),
                chapterNumber = id.toIntOrNull(),
                url = abs
            )
        }
        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter GoodToon kosong — struktur situs mungkin berubah.")
        }

        // Metadata dari halaman utama (opsional; gagal tidak menggagalkan).
        var title = comicId
        var cover = ""
        runCatching {
            val pageHtml = http.getHtmlWithHeaders("$origin/manga/$comicId/", pageHeaders(origin))
            Regex("""<h1 class="summary-title">([^<]+)</h1>""", RegexOption.IGNORE_CASE)
                .find(pageHtml)?.groupValues?.get(1)?.let { title = cleanText(it) }
            Regex(
                """<div class="manga-summary-cover">[\s\S]*?<img src="([^"]+)"""",
                RegexOption.IGNORE_CASE
            ).find(pageHtml)?.groupValues?.get(1)?.let { cover = it }
        }

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            coverUrl = cover,
            latestChapterTitle = chapters.firstOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val origin = Regex("""^(https?://[^/]*goodtoon[^/]*)""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.get(1) ?: FALLBACK_BASE
        val html = try {
            http.getHtmlWithHeaders(
                url,
                pageHeaders(origin) + ("Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            )
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil chapter: ${e.message}", e)
        }

        val pageTitle = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val parts = pageTitle.split(" - ").map(::cleanText)

        val images = ArrayList<ImageRef>()
        for (tag in imgTagRe.findAll(html).map { it.value }) {
            val src = dataSrcRe.find(tag)?.groupValues?.get(1) ?: continue
            images += ImageRef(src, images.size + 1)
        }
        if (images.isEmpty()) {
            throw SourceException(
                "Tidak ada gambar di halaman chapter GoodToon — class atau atribut lazyload mungkin berubah."
            )
        }
        return ChapterPage(
            source = source,
            comicTitle = parts.getOrNull(0).orEmpty(),
            chapterTitle = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: pageTitle,
            images = images
        )
    }
}
