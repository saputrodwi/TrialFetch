package com.trialfetch.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * wmanhua.com — sumber paling "jujur": tidak ada enkripsi maupun signing.
 * Halaman chapter memblok dua variabel JS polos:
 *   var num  = eval("239")              → jumlah halaman
 *   var pasd = "https://.../{uuid}/"    → folder dasar
 * URL gambar = ${pasd}{i}.webp untuk i = 1..num.
 */
class WmanhuaSource(private val http: HttpClient) : ComicSource {

    override val source = Source.WMANHUA
    private val base = "https://www.wmanhua.com"

    private val cardRe = Regex(
        """<a href="/comic/(\d+)\.html">\s*<article class="card">\s*""" +
            """<img[^>]*data-src="([^"]+)"[^>]*>\s*""" +
            """<footer>\s*<h3 class="cardtitle">([^<]+)</h3>""",
        RegexOption.IGNORE_CASE
    )
    private val numRe = Regex("""var\s+num\s*=\s*eval\(\s*["'](\d+)["']\s*\)""")
    private val pasdRe = Regex("""var\s+pasd\s*=\s*["']([^"']+)["']""")

    override suspend fun search(query: String): List<SearchResult> {
        val html = try {
            http.getHtml("$base/search?query=${query.urlEncode()}", referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mencari di Wmanhua: ${e.message}", e)
        }
        val results = cardRe.findAll(html).map { m ->
            SearchResult(
                source = source,
                comicId = m.groupValues[1],
                title = cleanText(m.groupValues[3]),
                coverUrl = m.groupValues[2],
                seriesUrl = "$base/comic/${m.groupValues[1]}.html"
            )
        }.distinctBy { it.comicId }.toList()

        if (results.isEmpty()) {
            throw SourceException(
                "Tidak ada hasil untuk \"$query\". " +
                "Situs ini kadang Baldwall devolv untuk kueri tanpa hasil."
            )
        }
        return results.take(30)
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val html = try {
            http.getHtml("$base/comic/$comicId.html", referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }

        val title = Regex("""<h1[^>]*>\s*([^<]+?)\s*</h1>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText) ?: comicId
        val cover = Regex("""<img[^>]*data-src="([^"]+)"[^>]*class="[^"]*cover""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?: Regex("""class="[^"]*cover[^"]*"[^>]*data-src="([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
            ?: ""

        // Halaman seri hanya merender ~24 chapter terbaru; sisanya diambil
        // dari endpoint JSON internal yang mengembalikan SEMUA chapter
        // sekaligus (POST /comic/{id} body {}).
        val chapters = fetchAllChapters(comicId, html)

        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter kosong.")
        }
        // sortedByDescending mengembalikan list BARU — sortByDescending
        // (yang di versi lama terpakai) juga begitu, jadi tidak mengubah
        // `chapters` sama sekali. Karena itu hasilnya harus dipakai
        // eksplisit, kalau tidak urutan chapter tidak akan pernah terurut.
        val sorted: List<Chapter> = chapters.sortedByDescending { chapter ->
            chapter.chapterNumber ?: 0
        }

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            coverUrl = cover,
            latestChapterTitle = sorted.firstOrNull()?.title.orEmpty(),
            chapters = sorted
        )
    }

    private suspend fun fetchAllChapters(comicId: String, fallbackHtml: String): List<Chapter> {
        runCatching {
            val json = http.postJson("$base/comic/$comicId", "{}", referer = "$base/comic/$comicId.html")
            val root = Json.parseToJsonElement(json).jsonObject
            if (root["code"]?.jsonPrimitive?.content == "0") {
                val list = root["data"]?.jsonObject?.get("chapters")
                    ?.let { Json.parseToJsonElement(it.toString()).jsonArray }
                    .orEmpty()
                return list.mapNotNull { el ->
                    val o = el.jsonObject
                    val contentId = o["contentId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = o["chapterName"]?.jsonPrimitive?.content.orEmpty()
                    Chapter(
                        chapterId = id,
                        title = cleanText(name).ifBlank { "Chapter $id" },
                        chapterNumber = id.toIntOrNull(),
                        url = "$base/chapter/$contentId-$id.html"
                    )
                }
            }
        }
        // Fallback: HTMLHOJA
        val re = Regex("""<a[^>]+href="/chapter/(\d+)-(\d+)\.html"[^>]*>([\s\S]{0,120}?)</a>""")
        return re.findAll(fallbackHtml).map { m ->
            Chapter(
                chapterId = m.groupValues[2],
                title = cleanText(m.groupValues[3].replace(Regex("<[^>]+>"), " "))
                    .ifBlank { "Chapter ${m.groupValues[2]}" },
                chapterNumber = m.groupValues[2].toIntOrNull(),
                url = "$base/chapter/${m.groupValues[1]}-${m.groupValues[2]}.html"
            )
        }.toList()
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = if (url.startsWith("http")) url else "$base$url"
        val html = try {
            http.getHtml(absolute, referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil chapter: ${e.message}", e)
        }

        val num = numRe.find(html)?.groupValues?.get(1)?.toIntOrNull()
        val pasd = pasdRe.find(html)?.groupValues?.get(1)
        if (num == null || pasd == null) {
            throw SourceException(
                "Variabel num/pasd tidak ditemukan — struktur situs berubah?"
            )
        }
        if (num <= 0) throw SourceException("Jumlah halaman tidak valid: $num")

        val folder = if (pasd.endsWith("/")) pasd else "$pasd/"
        val pageTitle = Regex("""<title>\s*([\s\S]*?)\s*</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.replace(Regex("""\s*\|\s*W漫画\s*$"""), "")
            ?.let(::cleanText).orEmpty()
        // "{chapter} {komik} - ..."
        val first = pageTitle.substringBefore(" - ").trim()
        val spaceIdx = first.indexOf(' ')
        val chapterTitle = if (spaceIdx > -1) first.take(spaceIdx) else first
        val comicTitle = if (spaceIdx > -1) first.drop(spaceIdx + 1).trim() else ""

        val images = (1..num).map { i -> ImageRef("$folder$i.webp", i) }

        return ChapterPage(
            source = source,
            comicTitle = comicTitle,
            chapterTitle = chapterTitle,
            images = images
        )
    }
}
