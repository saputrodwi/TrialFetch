package com.trialfetch.app.core

/**
 * Baozimh.
 *
 * Dua jalur, yang pertama dipakai duluan:
 *
 * 1. **API app** (utama, sama seperti versi web). Data chapter diambil dari
 *    appgb{1,2,3}.baozimh.com memakai header aplikasi resmi. Jalur ini
 *    dipakai karena HTML web tidak lengkap: untuk satu chapter yang sama,
 *    HTML hanya memuat sebagian gambar, sedangkan API app memuat semuanya
 *    beserta nomor halaman (data-index) dan ukuran (data-w/data-h).
 *
 * 2. **Scrape HTML www.twmanga.com** (cadangan). Dipakai kalau semua host
 *    app gagal. twmanga dipakai sebagai mirror resmi karena www.baozimh.com
 *    memasang gatekeeper challenge yang menolak request server-side.
 *
 * Catatan: bzmgapp.com sengaja tidak dicoba. Sertifikat host appgb*.bzmgapp.com
 * hanya CN=*.baozimh.com tanpa SAN bzmgapp, sehingga verifikasi TLS selalu gagal
 * (diverifikasi di versi web juga).
 */
class BaozimhSource(private val http: HttpClient) : ComicSource {

    override val source = Source.BAOZIMH

    private val base = "https://www.twmanga.com"

    private companion object {
        /** Host API app, dicoba berurutan sesuai urutan versi web. */
        val APP_HOSTS = listOf(
            "appgb1.baozimh.com",
            "appgb2.baozimh.com",
            "appgb3.baozimh.com"
        )

        /**
         * Header aplikasi Baozimh, sama seperti yang dipakai versi web
         * (worker.js). Semua kredensial dikumpulkan di sini supaya mudah
         * ditukar kalau Baozimh Wonderrotasi nilainya.
         */
        fun appHeaders(): Map<String, String> = mapOf(
            "Referer" to "https://appgb.baozimh.com/",
            "app-id" to "cn.sts.xiaoyun.ordermeals",
            "device-code" to "6ca052067aa9833084daaa6ffeba0913",
            "device-id" to "RKQ1.201217.002",
            "user-agent" to "baozimh_android/1.0.31/gb/adset",
            "app-version" to "1.0.31"
        )

        /** <img class="comic-contain__item" ...> milik API app. */
        val appImgRe = Regex(
            """<img[^>]*class="comic-contain__item"[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        val dataSrcRe = Regex("""data-src="([^"]+)"""", RegexOption.IGNORE_CASE)
        val dataIndexRe = Regex("""data-index="(\d+)"""", RegexOption.IGNORE_CASE)

        /**
         * URL CDN chapter Baozimh dipindah ke static-tw.baozimh.com.
         *
         * Disalin dari Trial Fetch web (index.html, rewriteBzcdnUrl): host itu
         * menyajikan versi lain untuk path yang sama, dan web memakainya
         * dengan sengaja demi hasil gambar yang lebih bersih.
         *
         * Catatan jujur hasil pengukuran: rewrite TIDAK selalu menghapus
         * watermark. Untuk comic 我有无限金色词条 filenya berbeda
         * (471.865 -> 328.067 byte) tapi logo 腾讯动漫 tetap ada; untuk comic
         * lain filenya identik byte demi byte. Jadi ini perbaikan yang
         * sporadis, bukan jaminan bersih. Pita banner 200px yang dihapus
         * BannerCropper adalah hal yang terpisah.
         */
        private val cdnRe = Regex(
            """^https?://[\w-]+\.(?:baozicdn\.com|bzcdn\.net)/(.+)$""",
            RegexOption.IGNORE_CASE
        )

        fun rewriteCdnUrl(url: String): String {
            val m = cdnRe.find(url) ?: return url
            return "https://static-tw.baozimh.com/" + m.groupValues[1]
        }
    }

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
    /**
     * Regex gambar untuk jalur HTML cadangan.
     *
     * CDN yang dipakai API app adalah **baozicdn.com**, sedangkan HTML
     * twmanga menunjuk **bzcdn.net**. Keduanya dicakup supaya jalur
     * cadangan tetap jalan meski tidak ada host app yang hidup.
     */
    private val imageRe = Regex(
        """https://[a-z0-9.\-]*(?:baozicdn\.com|bzcdn\.net)/scomic/[^"'\s]+?\.(?:jpg|jpeg|png|webp)""",
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

    /**
     * Mengambil daftar gambar satu chapter.
     *
     * Jalur utama: API app. Path chapter diturunkan dari query
     * page_direct menjadi {section}_{chapter}.html, sama seperti versi web.
     * Kalau semua host app gagal, jatuh ke scrape HTML sebagai cadangan.
     */
    override suspend fun chapter(url: String): ChapterPage {
        appChapter(url)?.let { return it }
        return htmlChapter(url)
    }

    /** Jalur API app: host dicoba berurutan, yang pertama hidup dipakai. */
    private suspend fun appChapter(url: String): ChapterPage? {
        val path = appChapterPath(url) ?: return null
        val headers = appHeaders()
        var lastError: Exception? = null

        for (host in APP_HOSTS) {
            val apiUrl = "https://$host/baozimhapp/comic/chapter$path"
            val html = try {
                http.getHtmlWithHeaders(apiUrl, headers)
            } catch (e: Exception) {
                lastError = e
                continue
            }
            val page = parseAppChapter(html)
            if (page == null) {
                lastError = SourceException("Respons API app kosong")
                continue
            }
            return page
        }
        if (lastError != null) {
            // Tidak melempar: jalur HTML masih mungkin berhasil.
            android.util.Log.w("BaozimhSource", "API app gagal, pakai HTML: ${lastError.message}")
        }
        return null
    }

    /**
     * Mengubah page_direct/comic/chapter menjadi path {section}_{chapter}.html
     * milik API app.
     */
    private fun appChapterPath(url: String): String? {
        // Sudah dalam bentuk API app: /comic/chapter/{slug}/{x}_{y}.html
        Regex("""/(?:comic|baozimhapp/comic)/chapter/([a-z0-9\-]+)/([0-9]+_[0-9]+)\.html""",
            RegexOption.IGNORE_CASE)
            .find(url)?.let {
                return "/${it.groupValues[1]}/${it.groupValues[2]}.html"
            }

        val q = url.substringAfter('?', "")
        if (q.isBlank()) return null
        val params = q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).lowercase() to it.substring(i + 1)
        }.toMap()

        val slug = params["comic_id"] ?: return null
        val section = params["section_slot"] ?: "0"
        val chapter = params["chapter_slot"] ?: return null
        return "/$slug/${section}_$chapter.html"
    }

    /** Memparsing <img class="comic-contain__item"> dari respons API app. */
    private fun parseAppChapter(html: String): ChapterPage? {
        val images = appImgRe.findAll(html).mapNotNull { m ->
            val tag = m.value
            val url = dataSrcRe.find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            val idx = dataIndexRe.find(tag)?.groupValues?.get(1)?.toIntOrNull()
            ImageRef(rewriteCdnUrl(url.replace("&amp;", "&")), (idx ?: 0) + 1)
        }.sortedBy { it.page }
            .distinctBy { it.url }
            .toList()

        if (images.isEmpty()) return null

        val pageTitle = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val parts = pageTitle.split(" - ").map(::cleanText)

        return ChapterPage(
            source = source,
            comicTitle = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: pageTitle,
            chapterTitle = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Chapter",
            images = images
        )
    }

    /** Jalur cadangan: scrape HTML www.twmanga.com. */
    private suspend fun htmlChapter(url: String): ChapterPage {
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
