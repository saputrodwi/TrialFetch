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
 * Menemukan challenge di rantai cause.
 *
 * Sumber membungkus error HTTP dalam `SourceException("Gagal ...", e)`,
 * sehingga `catch (e: CloudflareChallengeException)` di ViewModel tidak
 * pernah kena dan layar verifikasi tidak muncul. Panggil ini di
 * repository sebelum melempar ulang supaya tipenya pulih.
 */
fun Throwable.findCloudflareChallenge(): CloudflareChallengeException? {
    var t: Throwable? = this
    while (t != null) {
        if (t is CloudflareChallengeException) return t
        t = t.cause
    }
    return null
}

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
            val raw = CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() } ?: return
            val host = runCatching { java.net.URI(url).host }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: return
            headers[host] = raw
        } catch (_: Exception) {
            // CookieManager tidak tersedia (mis. di test) — abaikan.
        }
    }
}

/** Penanda halaman challenge Cloudflare. */
object Cloudflare {
    /**
     * Penanda halaman challenge ASLI.
     *
     * SENGAJA tidak memakai "/cdn-cgi/challenge-platform" polos: loader
     * beacon jsd (scripts/jsd/main.js) itu ditempel di SEMUA halaman situs
     * Cloudflare (mis. Wmanhua) — memakainya sebagai penanda membuat tiap
     * request normal dikira challenge. Akibatnya layar verifikasi terbuka
     * padahal halamannya biasa saja, dan alurnya ngeloop selamanya
     * (verifikasi → retry gagal lagi → verifikasi lagi).
     */
    private val STRICT_MARKERS = listOf(
        "cf-browser-verification",
        // window._cf_chl_opt — hanya ada di halaman challenge, tidak ada
        // di beacon jinak window.__CF$cv$params.
        "cf_chl_opt",
        "cf-challenge-running",
        // Skrip orchestrate challenge; beacon jsd (scripts/jsd/main.js)
        // tidak mengandung path ini.
        "challenge-platform/h/b",
        "<title>just a moment",
        "<title>attention required",
        "attention required"
    )

    /**
     * Deteksi apakah respons HTTP adalah challenge Cloudflare.
     *
     * Status 403/503/429 dengan penanda challenge = challenge. Status 200
     * juga bisa berisi challenge (managed challenge disajikan sebagai
     * halaman normal). Tanpa penanda (mis. 403 karena Referer gambar
     * salah) biarkan error HTTP aslinya keluar.
     */
    fun isChallenge(code: Int, body: String): Boolean {
        if (code == 403 || code == 503 || code == 429) {
            return STRICT_MARKERS.any { body.contains(it, ignoreCase = true) }
        }
        return STRICT_MARKERS.any { body.contains(it, ignoreCase = true) }
    }

    /** Judul halaman yang menandai challenge masih aktif di WebView. */
    private val TITLE_MARKERS = listOf(
        "just a moment",
        "attention required",
        "checking your browser",
        "checking your access"
    )

    fun isChallengeTitle(title: String): Boolean {
        val t = title.lowercase()
        return TITLE_MARKERS.any { t.contains(it) }
    }
}
