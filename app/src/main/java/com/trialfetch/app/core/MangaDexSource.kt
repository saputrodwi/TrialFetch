package com.trialfetch.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * MangaDex (mangadex.org) — komunitas manga/manhwa/manhua terbesar.
 * API JSON bersih tanpa Cloudflare, tanpa token, tanpa JavaScript.
 *
 *  - Search:    GET /manga?title={q}&limit=30&includes[0]=cover_art
 *  - Series:    GET /manga/{id}?includes[0]=cover_art&includes[1]=author
 *  - Chapter:   GET /manga/{id}/feed?limit=500&order[chapter]=desc
 *               GET /at-home/server/{chapterId} → { baseUrl, chapter.hash, data[] }
 *               Gambar: {baseUrl}/{hash}/{path}
 */
class MangaDexSource(private val http: HttpClient) : ComicSource {

    override val source = Source.MANGADEX
    private val apiBase = "https://api.mangadex.org"

    override val noImageReferer: Boolean get() = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun getJson(url: String): String =
        http.getHtml(url, mobile = false)

    override suspend fun search(query: String): List<SearchResult> {
        val url = "$apiBase/manga?title=${query.urlEncode()}&limit=30&includes[0]=cover_art"
        val body = try { getJson(url) } catch (e: Exception) {
            throw SourceException("Gagal menghubungi MangaDex: ${e.message}", e)
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"]?.jsonArray ?: return emptyList()
        val out = ArrayList<SearchResult>(30)
        for (el in data) {
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: continue
            val attrs = obj["attributes"]?.jsonObject ?: continue
            val titleObj = attrs["title"]?.jsonObject
            val title = titleObj?.get("en")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: titleObj?.values?.firstOrNull()?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: ""
            if (title.isBlank()) continue
            val coverRel = obj["relationships"]?.jsonArray
                ?.firstOrNull { r -> r.jsonObject["type"]?.jsonPrimitive?.content == "cover_art" }
            val coverFile = coverRel?.jsonObject
                ?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
            val coverUrl = coverFile?.let { "$apiBase/cover/$id/$it" }.orEmpty()
            out += SearchResult(
                source = source,
                comicId = id,
                title = title,
                coverUrl = coverUrl,
                seriesUrl = "https://mangadex.org/manga/$id/"
            )
            if (out.size >= 30) break
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val url = "$apiBase/manga/$comicId?includes[0]=cover_art&includes[1]=author"
        val body = try { getJson(url) } catch (e: Exception) {
            throw SourceException("Gagal mengambil series: ${e.message}", e)
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw SourceException("Respon MangaDex tidak valid")
        val obj = root["data"]?.jsonObject ?: throw SourceException("Series tidak ditemukan")
        val attrs = obj["attributes"]?.jsonObject ?: throw SourceException("Metadata series kosong")

        val titleObj = attrs["title"]?.jsonObject
        val title = titleObj?.get("en")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: titleObj?.values?.firstOrNull()?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: comicId
        val desc = attrs["description"]?.jsonObject?.get("en")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() } ?: attrs["description"]?.jsonPrimitive?.content?.let { it } ?: ""
        val status = attrs["status"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: ""

        val authors = obj["relationships"]?.jsonArray
            ?.filter { r -> r.jsonObject["type"]?.jsonPrimitive?.content == "author" }
            ?.mapNotNull { r ->
                r.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
            }?.joinToString(", ").orEmpty()

        val coverRel = obj["relationships"]?.jsonArray
            ?.firstOrNull { r -> r.jsonObject["type"]?.jsonPrimitive?.content == "cover_art" }
        val coverFile = coverRel?.jsonObject
            ?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
        val coverUrl = coverFile?.let { "$apiBase/cover/$comicId/$it" }.orEmpty()

        // Chapter feed: coba bahasa 'en' dulu, fallback semua bahasa.
        val chapters = fetchChapters(comicId, lang = "en")
            .takeIf { it.isNotEmpty() }
            ?: fetchChapters(comicId, lang = null)

        if (chapters.isEmpty()) throw SourceException("Daftar chapter MangaDex kosong.")

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            author = authors,
            coverUrl = coverUrl,
            synopsis = desc,
            status = status,
            latestChapterTitle = chapters.firstOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val chapterId = extractChapterId(url)
            ?: throw SourceException("ID chapter tidak ditemukan dari URL")
        val serverBody = try { getJson("$apiBase/at-home/server/$chapterId") } catch (e: Exception) {
            throw SourceException("Gagal mengambil server chapter: ${e.message}", e)
        }
        val server = runCatching { json.parseToJsonElement(serverBody).jsonObject }.getOrNull()
            ?: throw SourceException("Respon server chapter tidak valid")
        val chapterObj = server["chapter"]?.jsonObject
            ?: throw SourceException("Data chapter kosong dari server")
        val hash = chapterObj["hash"]?.jsonPrimitive?.content
            ?: throw SourceException("Hash chapter tidak ada")
        val baseUrl = server["baseUrl"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: "https://cdn.mangadex.org"
        val dataList = chapterObj["data"]?.jsonArray
            ?: throw SourceException("Daftar gambar chapter kosong")
        val images = dataList.mapIndexed { i, el ->
            val path = el.jsonPrimitive?.content
                ?: throw SourceException("Path gambar tidak valid")
            ImageRef("$baseUrl/$hash/$path", i + 1)
        }
        if (images.isEmpty()) throw SourceException("Tidak ada gambar di chapter ini.")
        return ChapterPage(
            source = source,
            comicTitle = "",
            chapterTitle = chapterObj["title"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?: "Chapter ${chapterObj["chapter"]?.jsonPrimitive?.content ?: "?"}",
            images = images
        )
    }

    override suspend fun seriesIdFromChapterUrl(chapterUrl: String): String? {
        return Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""",
            RegexOption.IGNORE_CASE).find(chapterUrl)?.value
    }

    private suspend fun fetchChapters(comicId: String, lang: String?): List<Chapter> {
        val params = mutableListOf("limit=500", "order[chapter]=desc")
        if (lang != null) params += "translatedLanguage[]=$lang"
        val url = "$apiBase/manga/$comicId/feed?${params.joinToString("&")}"
        val body = try { getJson(url) } catch (e: Exception) { return emptyList() }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val a = el.jsonObject["attributes"]?.jsonObject ?: return@mapNotNull null
            val chNum = a["chapter"]?.jsonPrimitive?.content?.toIntOrNull()
            Chapter(
                chapterId = el.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                title = a["title"]?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() }
                    ?: "Chapter ${chNum ?: "?"}",
                chapterNumber = chNum,
                url = "https://mangadex.org/chapter/${el.jsonObject["id"]?.jsonPrimitive?.content}"
            )
        }
    }

    private fun extractChapterId(raw: String): String? {
        val clean = raw.trim()
        if (Regex("""^[0-9a-f-]{36}$""").matches(clean)) return clean
        return Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""",
            RegexOption.IGNORE_CASE).find(clean)?.value
    }
}
