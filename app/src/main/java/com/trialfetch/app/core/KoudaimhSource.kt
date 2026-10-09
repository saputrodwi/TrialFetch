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
 * koudaimh.com (口袋漫画).
 *
 * Dua hal yang membuat sumber ini agak ribet:
 *  1. Pencarian butuh 2 langkah — ambil token `__searchtoken__` dari
 *     halaman /search dulu, baru kirim ulang kata kuncinya.
 *  2. Halaman chapter tidak memuat URL gambar; semuanya dikemas dalam
 *     blob `params = '...'` (base64 → AES-128-CBC). Berbeda dengan
 *     manwang, di sini IV adalah 16 byte PERTAMA dari blob, sisanya
 *     ciphertext.
 *
 * CATATAN: gambar disajikan dari CDN *.shimolife.com dengan URL bertanda
 * tangan yang punya masa berlaku. Di web sering gagal karena tanda
 * tangannya sudah kedaluwarsa saat lewat proxy. Di native gambar diambil
 * di tempat & waktu yang sama sehingga masih valid — salah satu alasan
 * lagi kenapa aplikasi ini dibangun native.
 */
class KoudaimhSource(private val http: HttpClient) : ComicSource {

    override val source = Source.KOUDAIMH
    private val base = "https://m.koudaimh.com"

    /** CDN shimolife: tiru referrerpolicy no-referrer halaman aslinya. */
    override val noImageReferer: Boolean get() = true

    private val tokenRe = Regex("""name="__searchtoken__"\s+value="([^"]+)"""")
    private val searchRe = Regex(
        """<a href="(/manhua/([^"/]+))" title="([^"]+)" class="block""",
        RegexOption.IGNORE_CASE
    )
    private val latestRe = Regex("""更新至:\s*<span[^>]*>([^<]+)</span>""")

    override suspend fun search(query: String): List<SearchResult> {
        val landing = try {
            http.getHtml("$base/search", referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi Koudaimh: ${e.message}", e)
        }
        val token = tokenRe.find(landing)?.groupValues?.get(1)
            ?: throw SourceException("Token pencarian tidak ditemukan (struktur berubah?)")

        val html = try {
            http.getHtml(
                "$base/search?q=${query.urlEncode()}&__searchtoken__=$token",
                referer = "$base/search"
            )
        } catch (e: Exception) {
            throw SourceException("Gagal mencari di Koudaimh: ${e.message}", e)
        }

        val results = searchRe.findAll(html).map { m ->
            val block = html.substring(
                m.range.first,
                (m.range.last + 500).coerceAtMost(html.length)
            )
            SearchResult(
                source = source,
                comicId = m.groupValues[2],
                title = cleanText(m.groupValues[3]),
                coverUrl = "",
                seriesUrl = base + m.groupValues[1]
            )
        }.distinctBy { it.comicId }.toList()

        if (results.isEmpty()) {
            throw SourceException("Tidak ada hasil untuk \"$query\"")
        }
        return results.take(30)
    }

    override suspend fun series(comicId: String): SeriesInfo {
        val html = try {
            http.getHtml("$base/manhua/$comicId", referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil daftar chapter: ${e.message}", e)
        }

        val linkRe = Regex(
            """<a[^>]+href="(?:https?://(?:www\.|m\.)?koudaimh\.com)?/manhua/""" +
                Regex.escape(comicId) + """/(\d+)\.html"[^>]*>\s*([\s\S]*?)\s*</a>""",
            RegexOption.IGNORE_CASE
        )
        val chapters = mutableListOf<Chapter>()
        val seen = mutableSetOf<String>()
        linkRe.findAll(html).forEach { m ->
            val chId = m.groupValues[1]
            if (!seen.add(chId)) return@forEach
            chapters += Chapter(
                chapterId = chId,
                title = cleanText(m.groupValues[2].replace(Regex("<[^>]+>"), " "))
                    .ifBlank { "Chapter $chId" },
                chapterNumber = chId.toIntOrNull(),
                url = "$base/manhua/$comicId/$chId.html"
            )
        }

        if (chapters.isEmpty()) {
            throw SourceException("Daftar chapter kosong — struktur berubah?")
        }
        // HTML asli terurut lama -> baru; dibalik supaya terbaru di atas.
        chapters.sortByDescending { it.chapterNumber ?: 0 }

        val title = Regex("""<h1[^>]*>\s*([\s\S]*?)\s*</h1>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::cleanText) ?: comicId
        val author = Regex("""作者[:：]\s*([^<\n]+)""").find(html)
            ?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val status = Regex("""状态[:：]\s*([^<\n]+)""").find(html)
            ?.groupValues?.get(1)?.let(::cleanText).orEmpty()

        return SeriesInfo(
            source = source,
            comicId = comicId,
            title = title,
            synopsis = extractMetaDescription(html),
            author = author,
            coverUrl = "",
            status = status,
            latestChapterTitle = latestRe.find(html)?.groupValues?.get(1).orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        val absolute = if (url.startsWith("http")) url else "$base$url"
        val html = try {
            http.getHtml(absolute, referer = "$base/")
        } catch (e: Exception) {
            throw SourceException("Gagal mengambil chapter: ${e.message}", e)
        }

        val blob = Regex("""params\s*=\s*['"]([^'"]+)""").find(html)?.groupValues?.get(1)
            ?: throw SourceException(
                "Blob params tidak ditemukan — situs mungkin sedang pakai " +
                "endpoint baru (versi mobile)."
            )

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
            .firstNotNullOfOrNull { key -> json[key]?.jsonArray }
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
        /** Kunci statis situs (16 byte → AES-128), terverifikasi manual. */
        const val KEY = "5V&RoR%Jf@pJPydF"
    }
}
