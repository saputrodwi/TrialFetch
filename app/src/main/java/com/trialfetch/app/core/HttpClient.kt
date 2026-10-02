package com.trialfetch.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Pembungkus HTTP untuk semua scraping.
 *
 * Penting: request keluar dari IP perangkat pengguna, bukan dari IP data
 * center. Itu sebabnya sumber yang memblokir Cloudflare Workers (mis.
 * manwang.net, rumanhua.org) tetap bisa diakses dari sini.
 */
class HttpClient(
    private val client: OkHttpClient = defaultClient()
) {
    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    internal val mobileUa =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Origin sebuah URL, mis. "https://s.baozicdn.com/". */
    internal fun originOf(url: String): String = runCatching {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}/"
    }.getOrDefault(url)

    /**
     * Header untuk memuat satu gambar — logika SAMA dengan [getBytes]:
     * UA mobile + Accept image + Referer origin, kecuali [noReferer].
     * Dipakai reader streaming (Coil) agar diperlakukan sama seperti unduhan.
     */
    internal fun imageHeaders(
        url: String,
        accept: String = "image/avif,image/webp,image/*,*/*;q=0.8",
        noReferer: Boolean = false,
        referer: String? = null
    ): Map<String, String> = buildMap {
        put("User-Agent", mobileUa)
        put("Accept", accept)
        if (!noReferer) put("Referer", referer ?: originOf(url))
    }

    companion object {
        /**
         * Client standar. Kalau [dns] diisi (mis. DoH dari [DohConfig]),
         * semua resolve hostname lewat sana; kalau null, DNS sistem.
         */
        fun defaultClient(dns: Dns? = null): OkHttpClient {
            val b = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
            if (dns != null) b.dns(dns)
            return b.build()
        }
    }

    /** Ambil HTML halaman, dengan header yang meniru browser. */
    suspend fun getHtml(
        url: String,
        referer: String? = null,
        mobile: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
            .header(
                "User-Agent",
                if (mobile) mobileUa else desktopUa
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        // Beberapa situs mem-block request yang dianggap bukan navigasi
            // (bot); header ini membuat request terlihat seperti membuka dokumen.
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Upgrade-Insecure-Requests", "1")
        if (referer != null) builder.header("Referer", referer)

        client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) {
                throw HttpException(res.code, url)
            }
            res.body?.string() ?: throw HttpException(-1, url)
        }
    }

    /**
     * Ambil HTML dengan header bebas.
     *
     * Diperlukan oleh API app Baozimh (appgb*.baozimh.com) yang menolak
     * request tanpa header aplikasi. Versi web memakai header yang sama.
     */
    suspend fun getHtmlWithHeaders(
        url: String,
        headers: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        for ((k, v) in headers) builder.header(k, v)
        client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code, url)
            res.body?.string() ?: throw HttpException(-1, url)
        }
    }

    /**
     * POST dengan body kosong dan header bebas.
     *
     * Dipakai endpoint AJAX yang menolak request tanpa header aplikasi
     * (mis. Madara GoodToon yang wajib X-Requested-With: XMLHttpRequest).
     */
    suspend fun postEmpty(
        url: String,
        headers: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
            .post(ByteArray(0).toRequestBody(null))
        for ((k, v) in headers) builder.header(k, v)
        client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code, url)
            res.body?.string() ?: throw HttpException(-1, url)
        }
    }

    /** Ambil file biner (gambar), mengikuti header yang diminta sumber. */
    suspend fun getBytes(
        url: String,
        referer: String? = null,
        accept: String = "image/avif,image/webp,image/*,*/*;q=0.8",
        sendNoReferer: Boolean = false
    ): ByteArray = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
            .header("User-Agent", mobileUa)
            .header("Accept", accept)
        if (!sendNoReferer) {
            builder.header("Referer", referer ?: url)
        }

        client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code, url)
            res.body?.bytes() ?: throw HttpException(-1, url)
        }
    }

    suspend fun postJson(
        url: String,
        json: String,
        referer: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", mobileUa)
            .header("Accept", "application/json, text/plain, */*")
        if (referer != null) builder.header("Referer", referer)
        for ((k, v) in extraHeaders) builder.header(k, v)

        client.newCall(builder.build()).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code, url)
            res.body?.string() ?: throw HttpException(-1, url)
        }
    }
}

class HttpException(val code: Int, val url: String) :
    Exception("HTTP $code dari $url")
