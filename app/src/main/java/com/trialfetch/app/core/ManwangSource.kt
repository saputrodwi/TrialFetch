package com.trialfetch.app.core

/**
 * manwang.net — situs kembar rumanhua.org (backend & kunci AES identik).
 *
 * CATATAN PENTING: situs ini memblokir IP egress Cloudflare Workers dengan
 * 403/520. Di aplikasi native request keluar dari IP perangkat, sehingga
 * sumber ini tetap bisa dipakai — itulah alasan utama Trial Fetch dibangun
 * sebagai aplikasi native, bukan proxied lewat worker.
 */
class ManwangSource(private val http: HttpClient) : ComicSource {

    override val source = Source.MANWANG
    private val base = "https://manwang.net"

    /** Host gambar (dmw.*) menolak Referer selain manwang.net (403). */
    override val imageReferer: String get() = "$base/"

    private val searchRe = Regex(
        """<a href="(/book/(\d+))">\s*<img src="([^"]+)"[^>]*>[\s\S]{0,160}?</a>\s*""" +
            """<span class="booktitle">([^<]+)</span>\s*""" +
            """<p class="commandDes">([^<]*)</p>""",
        RegexOption.IGNORE_CASE
    )
    // several template presentations; both are live depending on which
    // front-end the site serves, so both are parsed and merged.
    private val searchAltRe = Regex(
        """<a href="(/book/(\d+))" class="comic-item">[\s\S]*?data-src="([^"]+)"[\s\S]*?""" +
            """<h2 class="ui-nowrap">([^<]+)</h2>""",
        RegexOption.IGNORE_CASE
    )
    private val chapterListNewRe = Regex(
        """<li[^>]*data-chapter="(\d+)"[^>]*>[\s\S]*?<a[^>]*title="([^"]+)"[^>]*""" +
            """href="/chapter/(\d+)-(\d+)"""",
        RegexOption.IGNORE_CASE
    )
    private val chapterListOldRe = Regex(
        """href="/chapter/(\d+)-(\d+)"[^>]*>[\s\S]*?<div class="w50">([^<]+)""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun search(query: String): List<SearchResult> {
        val html = try {
            http.getHtml(
                "$base/index.php/search?key=${query.urlEncode()}",
                referer = "$base/"
            )
        } catch (e: Exception) {
            throw SourceException("Gagal mencari di Manwang: ${e.message}", e)
        }

        // Manwang menolak pencarian dari sebagian IP/jaringan dengan pesan
        // "搜索繁忙" (server sibuk). Bukan halaman hasil, jadi regex tidak
        // akan menemukan apa pun. Deteksi eksplisit supaya pesannya jelas
        // dan mengarahkan ke tempel URL langsung (yang tetap didukung),
        // bukan "tidak ada hasil" yang membingungkan.
        if ("搜索繁忙" in html || "\"code\":-1" in html.replace(" ", "")) {
            throw SourceException(
                "Manwang sedang membatasi pencarian dari jaringan ini " +
                    "(server membalas sibuk). Tempel URL series Manwang " +
                    "langsung lewat Buka dari URL — itu tetap bisa dibuka."
            )
        }

        val byPath = linkedMapOf<String, SearchResult>()
        fun put(path: String, id: String, title: String, cover: String, author: String) {
            if (byPath.containsKey(path)) return
            byPath[path] = SearchResult(
                source = source,
                comicId = id,
                title = cleanText(title),
                author = cleanText(author),
                coverUrl = cover,
                seriesUrl = "$base$path"
            )
        }

        searchRe.findAll(html).forEach { m ->
            put(m.groupValues[1], m.groupValues[2], m.groupValues[4], m.groupValues[3], m.groupValues[5])
        }
        searchAltRe.findAll(html).forEach { m ->
            put(m.groupValues[1], m.groupValues[2], m.groupValues[4], m.groupValues[3], "")
        }

        if (byPath.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return byPath.values.take(30)
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val html = try {
            http.getHtml("$base/book/$comicId", referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }

        val chapters = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()

        chapterListNewRe.findAll(html).forEach { m ->
            val chSlot = m.groupValues[1]
            val book = m.groupValues[4]
            val id = "$book-$chSlot"
            if (!seen.add(id)) return@forEach
            chapters += Chapter(
                chapterId = id,
                title = cleanText(m.groupValues[2]),
                chapterNumber = chSlot.toIntOrNull(),
                url = "$base/chapter/$id"
            )
        }
        chapterListOldRe.findAll(html).forEach { m ->
            val id = "${m.groupValues[1]}-${m.groupValues[2]}"
            if (!seen.add(id)) return@forEach
            chapters += Chapter(
                chapterId = id,
                title = cleanText(m.groupValues[3]),
                chapterNumber = m.groupValues[2].toIntOrNull(),
                url = "$base/chapter/$id"
            )
        }

        if (chapters.isEmpty()) {
            throw SourceException(
                "Daftar chapter kosong — situs mungkin sedang memakai template baru."
            )
        }
        chapters.sortByDescending { it.chapterNumber ?: 0 }

        val title = Regex("""<h1 class="detail-title[^"]*"[^>]*>([^<]+)</h1>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.substringBefore('_')
            ?: comicId
        // Sampul: data-src (desktop) atau background-image (mobile);
        // safety net URL ecombdimg mentah bila markup berubah lagi.
        val cover = Regex(
            """data-src="(https://[^"]*ecombdimg[^"]+)"|background-image:\s*url\((https://[^)]*ecombdimg[^)]+)\)""",
            RegexOption.IGNORE_CASE
        ).find(html)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }
            .orEmpty()
            .ifBlank {
                Regex("""https://[^\s"'()<>]+ecombdimg[^\s"'()<>]+""")
                    .find(html)?.value.orEmpty()
            }
        val author = Regex("""<p class="author">([^<]+)</p>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1).orEmpty()
        val latest = Regex("""<span>更新至:(.+?)</span>""").find(html)?.groupValues?.get(1).orEmpty()

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = cleanText(title),
            author = cleanText(author),
            coverUrl = cover,
            synopsis = extractMetaDescription(html),
            latestChapterTitle = cleanText(latest),
            chapters = chapters
        )
    }

    /** ID buku terbaca langsung dari URL chapter /chapter/{book}-{ch}. */
    override suspend fun seriesIdFromChapterUrl(chapterUrl: String): String? {
        return Regex("""/chapter/(\d+)-\d+""").find(chapterUrl)?.groupValues?.get(1)
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = if (url.startsWith("http")) url else "$base$url"
        val html = try {
            http.getHtml(absolute, referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil chapter: ${e.message}", e)
        }

        val decoded = ParamsDecryptor.fromHtml(html)
            ?: throw SourceException("Tidak ada blob params di halaman chapter.")

        if (decoded.images.isEmpty()) {
            throw SourceException("Chapter ini tidak berisi gambar.")
        }

        val pageTitle = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        // Judul: "{komik}-{chapter}免费阅读-{situs}"
        val parts = Regex("""^(.*?)-(第.*?)免费阅读""").find(pageTitle)
        val comicTitle = parts?.groupValues?.get(1).orEmpty()
        val chapterTitle = parts?.groupValues?.get(2) ?: pageTitle

        return ChapterPage(
            source = source,
            comicTitle = comicTitle,
            chapterTitle = chapterTitle,
            images = decoded.images.mapIndexed { i, u ->
                ImageRef(u, i + 1, needsDecrypt = decoded.imageEncrypted)
            }
        )
    }
}
