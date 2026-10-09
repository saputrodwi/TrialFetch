package com.trialfetch.app.core

import android.webkit.CookieManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Dilempar saat Cloudflare memblokir request scraping (403/503 dengan
 * body challenge, atau 200 yang ternyata halaman challenge).
 *
 * Membawa [url] asal supaya UI bisa membuka WebView di domain yang sama.
 */
class CloudflareChallengeException(val url: String) :
    Exception("Cloudflare memblokir akses — verifikasi diperlukan")

/**
 * Penyimpan cookie hasil verifikasi WebView.
 *
 * Cookie `cf_clearance` hanya berlaku untuk IP + User-Agent yang sama.
 * WebView dan OkHttp memakai UA yang disamakan (lihat [HttpClient.mobileUa]),
 * jadi cookie yang diambil dari WebView langsung bisa dipakai OkHttp.
 */
object CloudflareCookieStore {
    private val headers = ConcurrentHashMap<String, String>()

    /** Nilai header Cookie untuk host tertentu, atau null kalau belum ada. */
    fun cookieHeader(host: String): String? = headers[host]

    /**
     * Salin cookie dari WebView (CookieManager Android) ke penyimpanan
     * in-memory. Dipanggil di main thread setelah user menyelesaikan
     * challenge.
     */
    fun syncFromWebView(url: String) {
        try {
            val raw = CookieManager.getInstance().getCookie(url) ?: return
            val host = java.net.URI(url).host ?: return
            headers[host] = raw
        } catch (_: Exception) {
            // CookieManager tidak tersedia (mis. di test) — abaikan.
        }
    }
}

/** Penanda halaman challenge Cloudflare. */
object Cloudflare {
    private val BODY_MARKERS = listOf(
        "Just a moment",
        "cf-browser-verification",
        "cf_chl_opt",
        "/cdn-cgi/challenge-platform",
        "challenge-platform",
        "Attention Required"
    )

    private val TITLE_MARKERS = listOf(
        "just a moment",
        "attention required",
        "checking your browser",
        "checking your access"
    )

    /**
     * Deteksi apakah respons HTTP adalah challenge Cloudflare.
     *
     * Status 403/503 dengan body yang mengandung penanda CF = challenge.
     * Status 200 juga bisa berisi challenge (CF menyajikan challenge
     * sebagai halaman normal).
     */
    fun isChallenge(code: Int, body: String): Boolean {
        if (code == 403 || code == 503) {
            return BODY_MARKERS.any { body.contains(it, ignoreCase = true) }
        }
        return body.contains("cf-browser-verification", ignoreCase = true) ||
            body.contains("/cdn-cgi/challenge-platform", ignoreCase = true)
    }

    /** Judul halaman yang menandai challenge masih aktif di WebView. */
    fun isChallengeTitle(title: String): Boolean {
        val t = title.lowercase()
        return TITLE_MARKERS.any { t.contains(it) }
    }
}
