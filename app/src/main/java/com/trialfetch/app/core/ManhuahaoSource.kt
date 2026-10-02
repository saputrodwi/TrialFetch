package com.trialfetch.app.core

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * manhuahao.com (漫画号) — satu keluarga CMS dengan Koudaimh
 * (token `__searchtoken__` + blob `params` AES-128-CBC dengan kunci
 * dan format IV yang SAMA: 16 byte pertama = IV).
 *
 *  - Cari:    GET {base}/search (ambil token), lalu
 *             GET {base}/search?q={q}&__searchtoken__={token};
 *             hasil `<article class="manga-card">`.
 *  - Series:  GET {base}/{slug}-{kode}; cover
 *             `https://img.manhuahao.com/cover/{slug}.webp`;
 *             chapter `<a href="/{slug}/{n}.html">第N话 …</a>`.
 *  - Chapter: GET {base}/{slug}/{n}.html → dekripsi `params`
 *             menjadi JSON { chapter_images[] } berisi URL penuh
 *             bertanda tangan.
 */
class ManhuahaoSource(private val http: HttpClient) : ComicSource {

    override val source = Source.MANHUAHAO
    private val base = "https://m.manhuahao.com"

    /** CDN shimolife: tiru referrerpolicy no-referrer halaman aslinya. */
    override val noImageReferer: Boolean get() = true

    private val tokenRe = Regex("""name="__searchtoken__"\s+value="([^"]+)"""")
    private val searchRe = Regex(
        """<article class="manga-card">[\s\S]*?<a class="manga-cover" href="(/([a-z0-9\-]+))"[^>]*title="([^"]+)"""",
        RegexOption.IGNORE_CASE
    )
    private val searchCoverRe = Regex(
        """<img src="(https://img\.manhuahao\.com/cover/[^"]+)"[^>]*alt="([^"]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val chapterTitleRe = Regex("""第\d+\s*[话話章]""")

    override suspend fun search(query: String): List<SearchResult> {
        val landing = try {
            http.getHtml("$base/search", referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi Manhuahao: ${e.message}", e)
        }
        val token = tokenRe.find(landing)?.groupValues?.get(1)
            ?: throw SourceException("Token pencarian tidak ditemukan (struktur berubah?)")

        val html = try {
            http.getHtml(
                "$base/search?q=${query.urlEncode()}&__searchtoken__=$token",
                referer = "$base/search"
            )
        } catch (e: Exception) {
            throw SourceException("Gagal mencari di Manhuahao: ${e.message}", e)
        }

        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (m in searchRe.findAll(html)) {
            if (out.size >= 30) break
            val path = m.groupValues[1]
            val slug = m.groupValues[2]
            if (!seen.add(path)) continue
            val block = html.substring(
                m.range.first,
                (m.range.last + 800).coerceAtMost(html.length)
            )
            val cover = searchCoverRe.find(block)?.groupValues?.get(1).orEmpty()
            out += SearchResult(
                source = source,
                comicId = slug,
                title = cleanText(m.groupValues[3]),
                coverUrl = cover,
                seriesUrl = base + path
            )
        }

        if (out.isEmpty()) {
            throw SourceException("Tidak ada hasil untuk \"$query\"")
        }
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val html = try {
            http.getHtml("$base/$comicId", referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }

        val linkRe = Regex(
            """<a[^>]+href="/""" + Regex.escape(comicId) +
                """/(\d+)\.html"[^>]*>\s*([\s\S]*?)\s*</a>""",
            RegexOption.IGNORE_CASE
        )
        val chapters = linkedMapOf<String, Chapter>()
        linkRe.findAll(html).forEach { m ->
            val chId = m.groupValues[1]
            val title = cleanText(m.groupValues[2].replace(Regex("<[^>]+>"), " "))
                .ifBlank { "Chapter $chId" }
            val prev = chapters[chId]
            // Satu chapter bisa tercantum dua kali ("Mulai baca" + judul
            // aslinya): menangkan yang judulnya mirip nomor chapter.
            if (prev == null ||
                (!chapterTitleRe.containsMatchIn(prev.title) &&
                    chapterTitleRe.containsMatchIn(title))
            ) {
                chapters[chId] = Chapter(
                    chapterId = chId,
                    title = title,
                    chapterNumber = chId.toIntOrNull(),
                    url = "$base/$comicId/$chId.html"
                )
            }
        }

        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter kosong — struktur berubah?")
        }
        val sorted = chapters.values.sortedBy { it.chapterNumber ?: Int.MAX_VALUE }

        val title = Regex("""<h1[^>]*>\s*([\s\S]*?)\s*</h1>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText)
            ?.takeIf { it.isNotBlank() }
            ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.let(::cleanText)
                ?.substringBefore("漫画")?.trim()
            ?: comicId
        val author = Regex("""作者[:：]\s*([^<\n]+)""").find(html)
            ?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val status = Regex("""状态[:：]\s*([^<\n]+)""").find(html)
            ?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val latest = Regex("""最新[:：]\s*<a[^>]*>([^<]+)</a>""").find(html)
            ?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        // Cover dinamai persis {slug}.webp; fallback img cover pertama.
        val cover = Regex(
            """<img[^>]+src="(https://img\.manhuahao\.com/cover/""" +
                Regex.escape(comicId) + """\.webp[^"]*)"""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.get(1)
            ?: Regex(
                """<img[^>]+src="(https://img\.manhuahao\.com/cover/[^"]+)"[^>]*>""",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.get(1)
            .orEmpty()

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            synopsis = extractMetaDescription(html),
            author = author,
            coverUrl = cover,
            status = status,
            latestChapterTitle = latest.ifBlank { sorted.lastOrNull()?.title.orEmpty() },
            chapters = sorted
        )
    }

    /** Slug series terbaca langsung dari URL chapter /{slug}/{n}.html. */
    override suspend fun seriesIdFromChapterUrl(chapterUrl: String): String? {
        return Regex("""/([a-z0-9\-]+)/\d+\.html""", RegexOption.IGNORE_CASE)
            .find(chapterUrl)?.groupValues?.get(1)
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = if (url.startsWith("http")) url else "$base$url"
        val html = try {
            http.getHtml(absolute, referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil chapter: ${e.message}", e)
        }

        val blob = Regex("""params\s*=\s*['"]([^'"]+)""").find(html)?.groupValues?.get(1)
            ?: throw SourceException("Blob params tidak ditemukan — struktur berubah?")

        val key: Key = SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "AES")
        val raw = try {
            val b64 = blob.trim().replace("-", "+").replace("_", "/")
            Base64.decode(b64 + "=".repeat((4 - (b64.length % 4)) % 4), Base64.DEFAULT)
        } catch (e: Exception) {
            throw SourceException("Blob params bukan base64 valid", e)
        }
        if (raw.size <= 16) throw SourceException("Blob params terlalu pendek")

        val plaintext = try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                IvParameterSpec(raw.copyOfRange(0, 16))
            )
            cipher.doFinal(raw.copyOfRange(16, raw.size))
        } catch (e: Exception) {
            throw SourceException("Gagal dekripsi params: ${e.message}", e)
        }

        val json = try {
            Json.parseToJsonElement(String(plaintext, Charsets.UTF_8)).jsonObject
        } catch (e: Exception) {
            throw SourceException("JSON params rusak: ${e.message}", e)
        }

        val images = listOf("chapter_images", "chapterImages", "images", "image_list")
            .firstNotNullOfOrNull { k -> json[k]?.jsonArray }
            .orEmpty()
            .mapNotNull { el ->
                when (el) {
                    is kotlinx.serialization.json.JsonPrimitive -> el.content
                    is kotlinx.serialization.json.JsonObject ->
                        el["url"]?.jsonPrimitive?.content
                            ?: el["src"]?.jsonPrimitive?.content
                            ?: el["image_url"]?.jsonPrimitive?.content
                    else -> null
                }
            }
            .map { it.replace("\\/", "/").replace("&amp;", "&") }
            .filter { it.startsWith("http") }

        if (images.isEmpty()) throw SourceException("Chapter ini tidak berisi gambar.")

        return ChapterPage(
            source = source,
            comicTitle = cleanText(json["comic_name"]?.jsonPrimitive?.content.orEmpty()),
            chapterTitle = cleanText(json["chapter_title"]?.jsonPrimitive?.content.orEmpty()),
            images = images.mapIndexed { i, u -> ImageRef(u, i + 1) }
        )
    }

    private companion object {
        /** Kunci sama dengan Koudaimh (satu keluarga CMS), terverifikasi manual. */
        const val KEY = "5V&RoR%Jf@pJPydF"
    }
}
