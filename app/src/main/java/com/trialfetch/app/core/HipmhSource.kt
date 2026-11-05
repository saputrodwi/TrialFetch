package com.trialfetch.app.core

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.math.BigInteger

/**
 * HipMH (m.hipmh.com) — 嬉皮漫畫, platform komik Cina multilingual.
 *
 * PENTING: halaman HTML situs ini MENOLAK client non-browser (OkHttp/curl
 * menerima HTTP 200 dengan body 0 byte), jadi scraping HTML mustahil.
 * SEMUA data diambil lewat API JSON internal yang dipakai SPA-nya sendiri
 * (hipapi1.s3file.top) — pendekatan yang sama dengan ekstensi Mihon
 * "XiPi Manhua":
 *
 *  - Search:   GET /v1/search?q=&page=&page_size=
 *  - Series:   GET /v1/manga?mid={mid}
 *  - Chapters: GET /v1/manga/chapters?mid={mid}&page=&per_page=50&order=desc
 *  - Pages:    GET /v2/chapter?hid={b64url("c:<cid>")-<suffix>}
 *              Balasan `images` adalah string yang di-encode situs dengan
 *              algoritma reader-nya (marker qM9/Vx/pL0/Z7 + pemetaan
 *              alfabet + chunk ke-7 ganjil dibalik) berisi JSON array path
 *              gambar, plus SATU entri decoy yang harus dibuang memakai
 *              order_id/sid (persis matematika BigInt di situs).
 *  - Gambar:   https://hip-tx-1.s3imgs.top{path} (line 9 → hip-tx-s1),
 *              boleh tanpa Referer.
 *
 * mid = base64url("m:<id numerik>"), selalu jadi segmen pertama slug
 * series ("<mid>-<nama>"); hid chapter = base64url("m:<mid>-c:<cid>")-<suffix>.
 */
class HipmhSource(private val http: HttpClient) : ComicSource {

    override val source = Source.HIPMH
    private val base = "https://m.hipmh.com"
    private val api = "https://hipapi1.s3file.top"

    /** Host gambar hip-tx-*.s3imgs.top melayani request tanpa Referer. */
    override val noImageReferer: Boolean get() = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val imgBase = "https://hip-tx-1.s3imgs.top"
    private val imgBaseS1 = "https://hip-tx-s1.s3imgs.top"
    private val coverBase = "https://cover.s3imgs.top"

    // Alfabet reader untuk membuka encode daftar gambar.
    private val encAlphabet = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val decAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Panggil API dan kembalikan objek `data`-nya. */
    private suspend fun apiGet(path: String): JsonObject {
        val body = try {
            http.getHtmlWithHeaders(
                api + path,
                mapOf(
                    "User-Agent" to http.mobileUa,
                    "Accept" to "application/json, text/plain, */*",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to base
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CloudflareChallengeException) {
            throw e
        } catch (e: Exception) {
            throw SourceException("Gagal menghubungi API HipMH: ${e.message}", e)
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw SourceException("Balasan API HipMH bukan JSON")
        return root["data"]?.jsonObject
            ?: throw SourceException("API HipMH tidak mengembalikan data")
    }

    private fun nameList(o: JsonObject, key: String): String =
        (o[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.stringOrNull("name")?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(", ")
            .orEmpty()

    override suspend fun search(query: String): List<SearchResult> {
        val d = apiGet("/v1/search?q=${query.urlEncode()}&page=1&page_size=30")
        val rows = (d["data"] as? JsonArray) ?: JsonArray(emptyList())
        val out = ArrayList<SearchResult>(30)
        val seen = HashSet<String>()
        for (el in rows) {
            val o = el as? JsonObject ?: continue
            val id = o.stringOrNull("id").orEmpty()          // "<mid>-<slug>"
            val title = o.stringOrNull("title")?.trim().orEmpty()
            if (id.isEmpty() || !seen.add(id)) continue
            val cover = o.stringOrNull("vertical_image_url")?.let { coverBase + it }.orEmpty()
            out += SearchResult(
                source = source,
                comicId = id,
                title = title.ifBlank { id },
                coverUrl = cover,
                seriesUrl = "$base/series/$id"
            )
            if (out.size >= 30) break
        }
        if (out.isEmpty()) throw SourceException("Tidak ada hasil untuk \"$query\"")
        return out
    }

    override suspend fun series(comicId: String): SeriesInfo {
        // comicId bisa slug penuh "<mid>-<nama>" atau mid saja; yang
        // mengidentifikasi series hanyalah segmen pertama (mid).
        val mid = comicId.substringBefore('-')

        val info = apiGet("/v1/manga?mid=$mid")
        val title = info.stringOrNull("title")?.trim()?.takeIf { it.isNotBlank() } ?: mid
        val author = nameList(info, "authors")
        val synopsis = info.stringOrNull("description").orEmpty()
        val cover = info.stringOrNull("vertical_image_url")?.let { coverBase + it }.orEmpty()
        val status = when (info.stringOrNull("status")) {
            "ongoing" -> "Ongoing"
            "completed" -> "Completed"
            "hiatus" -> "Hiatus"
            else -> ""
        }

        // Daftar chapter terpaginasi (50/halaman, terbaru dulu).
        val chapters = ArrayList<Chapter>(200)
        val seen = HashSet<String>()
        var page = 1
        var totalPages = 1
        do {
            val d = apiGet("/v1/manga/chapters?mid=$mid&page=$page&per_page=50&order=desc")
            totalPages = d.stringOrNull("total_pages")?.toIntOrNull() ?: 1
            for (el in (d["items"] as? JsonArray) ?: JsonArray(emptyList())) {
                val o = el as? JsonObject ?: continue
                val hid = o.stringOrNull("hid") ?: continue
                if (!seen.add(hid)) continue
                val t = o.stringOrNull("title").orEmpty()
                chapters += Chapter(
                    chapterId = hid,
                    title = t.ifBlank { "Chapter ${chapters.size + 1}" },
                    chapterNumber = Regex("""(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull(),
                    url = hid
                )
            }
            page++
        } while (page <= totalPages && page <= 40) // batas aman: 40 × 50 chapter

        if (chapters.isEmpty()) throw SourceException("Daftar chapter HipMH kosong.")
        chapters.sortByDescending { it.chapterNumber ?: 0 }

        return SeriesInfo(
            source = source,
            comicId = mid, // dinormalkan ke mid agar bookmark konsisten
            title = title,
            author = author,
            coverUrl = cover,
            status = status,
            synopsis = synopsis,
            latestChapterTitle = chapters.firstOrNull()?.title.orEmpty(),
            chapters = chapters
        )
    }

    override suspend fun chapter(url: String): ChapterPage {
        // `url` bisa hid mentah dari daftar chapter API (frontend-hid), atau
        // URL lengkap /chapter/go?hid=... hasil tempel pengguna.
        val hid = if (url.contains("hid=")) {
            Regex("""[?&]hid=([^&]+)""").find(url)?.groupValues?.get(1) ?: url
        } else {
            url
        }
        val apiHid = resolveApiHid(hid)
        val data = apiGet("/v2/chapter?hid=$apiHid")

        val encoded = data.stringOrNull("images")
            ?: throw SourceException("Chapter HipMH tidak berisi gambar.")
        val images = try {
            decodeImages(encoded).toMutableList()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SourceException("Gagal membuka daftar halaman HipMH: ${e.message}", e)
        }

        // Buang satu entri decoy — matematika sama dengan reader situs.
        val orderId = data.stringOrNull("order_id")?.toLongOrNull() ?: 0L
        val sid = data.stringOrNull("sid")?.toLongOrNull() ?: 0L
        decoyIndex(images.size, orderId, sid)?.let { images.removeAt(it) }
        if (images.isEmpty()) throw SourceException("Chapter HipMH tidak berisi gambar.")

        val imgHost = if (data.stringOrNull("line")?.toIntOrNull() == 9) imgBaseS1 else imgBase
        return ChapterPage(
            source = source,
            comicTitle = "",
            chapterTitle = "",
            images = images.mapIndexed { i, p -> ImageRef(imgHost + p, i + 1) }
        )
    }

    /**
     * Telusuri URL chapter/series kembali ke comicId (mid) series-nya.
     * menerima: /series/{slug}, /works/{slug}, /chapter/{hid} (reader),
     *           /chapter/go?hid=...  (m.hipmh)
     */
    override suspend fun seriesIdFromChapterUrl(chapterUrl: String): String? {
        val absolute = if (chapterUrl.startsWith("http")) chapterUrl else "$base$chapterUrl"
        val hid = Regex("""[?&]hid=([^&]+)""").find(absolute)?.groupValues?.get(1)
            ?: Regex("""/chapter/([a-zA-Z0-9_-]+)""").find(absolute)?.groupValues?.get(1)
            ?: Regex("""/series/([a-zA-Z0-9-]+)""").find(absolute)?.groupValues?.get(1)
            ?: Regex("""/works/([a-zA-Z0-9-]+)""").find(absolute)?.groupValues?.get(1)
            ?: return null
        // Segmen pertama: b64url("m:<mid>-c:<cid>") untuk hid chapter frontend
        // atau b64url("<mid>") untuk slug/mid mentah.
        val first = hid.substringBefore('-')
        val decoded = runCatching { b64Decode(first) }.getOrNull()
        return if (decoded != null && decoded.startsWith("m:")) {
            b64Encode(decoded.substringBefore("-c:"))
        } else {
            first
        }
    }

    /**
     * hid chapter FRONTEND = b64url("m:<mid>-c:<cid>")-<suffix>.
     * Bentuk API `/v2/chapter?hid=...` memakai bentuk lain (c:<cid>-<suffix>
     * atau m:<mid>-c:<cid>-<suffix> tergantung versi server), tidak selalu
     * konversi langsung yang benar. API-hid yang benar ada di HTML reader
     * (`data-api-hid`).
     *
     * Kalau URL-nya masih frontend, saya akan fetch HTML-nya dulu dan ambil
     * `data-api-hid`. Kalau sudah API-hid, kembalikan apa adanya.
     */
    private suspend fun resolveApiHid(hid: String): String {
        // sudah API-hid (prefixnya decode-nya tidak dimulai "m:") → pakai apa adanya
        val decoded = runCatching { b64Decode(hid.substringBefore('-')) }.getOrNull()
        if (decoded != null && !decoded.startsWith("m:")) return hid

        // frontend-hid → fetch HTML reader, ambil data-api-hid
        val page = runCatching {
            http.getHtmlWithHeaders(
                "https://reader.hipmh.top/chapter/$hid",
                mapOf(
                    "User-Agent" to http.mobileUa,
                    "Referer" to "https://m.hipmh.com/",
                    "Accept" to "text/html,application/xhtml+xml,*/*;q=0.8"
                )
            )
        }.getOrNull()
        if (page != null) {
            Regex("""data-api-hid="([^"]+)"""").find(page)?.groupValues?.get(1)?.let { return it }
        }
        // fallback: konversi manual (c:<cid>-suffix)
        if (decoded != null && decoded.startsWith("m:")) {
            return b64Encode(decoded.substringAfter("-c:", "")) + "-" + hid.substringAfter('-', "")
        }
        return hid
    }

    /**
     * Membuka `images` ter-encode ala reader situs
     * (/assets/runtime/chapter-decoder.js di host reader):
     * penanda "qM9" kepala, "Vx" tengah, "pL0" ekor, "Z7" penutup; ekor
     * berukuran n/3 dan kepala (n-tail)/2 dari payload n karakter.
     */
    private fun decodeImages(encoded: String): List<String> {
        val body = encoded.removePrefix("qM9").removeSuffix("Z7")
        val tailLen = (body.length - 5) / 3
        val headLen = (body.length - 5 - tailLen) / 2
        val head = body.take(headLen)
        val mid = body.substring(headLen + 2, body.length - 3 - tailLen)
        val tail = body.takeLast(tailLen)
        val base64 = (tail + head + mid)
            .chunked(7)
            .mapIndexed { index, block -> if (index % 2 == 1) block.reversed() else block }
            .joinToString("")
            .map { c -> encAlphabet.indexOf(c).takeIf { it >= 0 }?.let { decAlphabet[it] } ?: c }
            .joinToString("")
        val arr = json.parseToJsonElement(b64Decode(base64)).jsonArray
        return arr.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }

    /** Reader situs membuang satu entri sebelum menampilkan halaman. */
    private fun decoyIndex(size: Int, orderId: Long, sid: Long): Int? {
        if (size <= 0) return null
        val key = (BigInteger.valueOf(sid) * BigInteger.valueOf(0x9E3779B1L))
            .xor(BigInteger.valueOf(size.toLong()) * BigInteger.valueOf(0x85EBCA6BL))
            .mod(BigInteger.valueOf(size.toLong()))
        val idx = BigInteger.valueOf(orderId).xor(key)
        return idx.toInt().takeIf { it in 0 until size }
    }

    private fun b64Decode(s: String): String =
        String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))

    private fun b64Encode(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
