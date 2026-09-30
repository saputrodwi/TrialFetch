package com.trialfetch.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Jjabtoon.
 *
 * Situs BEDA dari jjaptoon (bukan varian domain). Arsitekturnya REST JSON
 * bersih, jadi tidak perlu scrape HTML sama sekali. Diport dari Trial Fetch
 * web (worker.js: searchJjabtoon + handler episode).
 *
 *  - Cari:    GET {origin}/api/webtoons?search={q}
 *  - Series:  GET {origin}/api/webtoons/{id}  (metadata)
 *             GET {origin}/api/webtoons/{id}/episodes  (daftar episode)
 *  - Chapter: GET {origin}/api/episodes/{episodeId}  (images + sortOrder)
 *
 * Domain bernomor (jjabtoon001.com, dst) dan bisa berganti. Origin utama
 * jjabtoon001.com; kalau mati, URL asal user dicoba dulu bila ada.
 */
class JjabtoonSource(private val http: HttpClient) : ComicSource {

    override val source = Source.JJABTOON

    private companion object {
        const val DEFAULT_ORIGIN = "https://jjabtoon001.com"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

        fun apiHeaders(origin: String): Map<String, String> = mapOf(
            "Accept" to "application/json, text/plain, */*",
            "User-Agent" to DESKTOP_UA,
            "Referer" to "$origin/"
        )

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    private suspend fun getJson(url: String, origin: String): String {
        return try {
            http.getHtmlWithHeaders(url, apiHeaders(origin))
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi Jjabtoon: ${e.message}", e)
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        val body = getJson(
            "$DEFAULT_ORIGIN/api/webtoons?search=${query.urlEncode()}",
            DEFAULT_ORIGIN
        )
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw SourceException("Respons pencarian Jjabtoon tidak dikenali")
        if (root["success"]?.jsonPrimitive?.content != "true") {
            throw SourceException("Pencarian Jjabtoon ditolak server")
        }
        val data = root["data"]?.jsonArray ?: return emptyList()

        val out = ArrayList<SearchResult>(30)
        for (el in data.take(30)) {
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: continue
            val title = o["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: "Webtoon $id"
            out += SearchResult(
                source = source,
                comicId = id,
                title = cleanText(title),
                author = cleanText(o["authorName"]?.jsonPrimitive?.content.orEmpty()),
                coverUrl = o["thumbnailUrl"]?.jsonPrimitive?.content.orEmpty(),
                seriesUrl = "$DEFAULT_ORIGIN/webtoons/$id"
            )
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val origin = DEFAULT_ORIGIN
        val metaBody = getJson("$origin/api/webtoons/$comicId", origin)
        val meta = runCatching {
            json.parseToJsonElement(metaBody).jsonObject["data"]?.jsonObject
        }.getOrNull() ?: throw SourceException("Series Jjabtoon tidak ditemukan")

        val title = meta["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: "Webtoon $comicId"

        val epBody = getJson("$origin/api/webtoons/$comicId/episodes", origin)
        val eps = runCatching {
            json.parseToJsonElement(epBody).jsonObject["data"]?.jsonArray
        }.getOrNull() ?: throw SourceException("Daftar episode Jjabtoon kosong")

        val chapters = ArrayList<Chapter>(eps.size)
        val seen = HashSet<String>()
        for (el in eps) {
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: continue
            if (!seen.add(id)) continue
            val no = o["episodeNo"]?.jsonPrimitive?.intOrNull
            val epTitle = o["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: no?.let { "$it 화" } ?: "Episode $id"
            chapters += Chapter(
                chapterId = id,
                title = cleanText(epTitle),
                chapterNumber = no,
                url = "$origin/episodes/$id"
            )
        }
        if (chapters.isEmpty()) {
            throw SourceException("Daftar episode Jjabtoon kosong.")
        }

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = cleanText(title),
            author = cleanText(meta["authorName"]?.jsonPrimitive?.content.orEmpty()),
            coverUrl = meta["thumbnailUrl"]?.jsonPrimitive?.content.orEmpty(),
            latestChapterTitle = chapters.lastOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val episodeId = Regex("""/episodes/(\d+)""").find(url)?.groupValues?.get(1)
            ?: throw SourceException("URL episode Jjabtoon tidak dikenali")
        val origin = Regex("""^(https?://[^/]+)""").find(url)?.groupValues?.get(1)
            ?.takeIf { "jjabtoon" in it } ?: DEFAULT_ORIGIN

        // Origin dari URL dicoba dulu (paling mungkin domain aktif),
        // baru fallback ke default — sama seperti web.
        val candidates = listOf("$origin/api/episodes/$episodeId").let {
            if (origin == DEFAULT_ORIGIN) it
            else it + "$DEFAULT_ORIGIN/api/episodes/$episodeId"
        }

        var lastError: Exception? = null
        for (apiUrl in candidates) {
            val body = try {
                http.getHtmlWithHeaders(apiUrl, apiHeaders(origin))
            } catch (e: Exception) {
                lastError = e
                continue
            }
            val data = runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                if (root["success"]?.jsonPrimitive?.content != "true") null
                else root["data"]?.jsonObject
            }.getOrNull()
            if (data == null) {
                lastError = SourceException("Respons episode Jjabtoon tidak dikenali")
                continue
            }
            val imgs = data["images"]?.jsonArray
            if (imgs == null) {
                lastError = SourceException("Episode Jjabtoon tidak berisi gambar")
                continue
            }
            if (imgs.isEmpty()) {
                lastError = SourceException("Episode Jjabtoon tidak berisi gambar")
                continue
            }

            val sorted = imgs.mapIndexed { i, el ->
                val o = el.jsonObject
                val u = o["url"]?.jsonPrimitive?.content ?: return@mapIndexed null
                val order = o["sortOrder"]?.jsonPrimitive?.intOrNull ?: i
                order to u
            }.filterNotNull().sortedBy { it.first }

            val images = sorted.mapIndexed { i, (_, u) -> ImageRef(u, i + 1) }
            val chTitle = data["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: data["episodeNo"]?.jsonPrimitive?.content?.let { "$it 화" }
                ?: "Episode $episodeId"
            return ChapterPage(
                source = source,
                comicTitle = "",
                chapterTitle = cleanText(chTitle),
                images = images
            )
        }
        throw lastError ?: SourceException("Episode Jjabtoon tidak ditemukan")
    }
}
