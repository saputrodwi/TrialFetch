package com.trialfetch.app.ui

import android.webkit.WebSettings
import java.net.URI

/**
 * Hardening WebView untuk dua layar yang memuat konten eksternal:
 * verifikasi Cloudflare dan reader alternatif. Keduanya butuh JavaScript,
 * tetapi tidak boleh mengakses berkas lokal atau berpindah ke host sembarangan.
 */
object WebViewGuard {

    /** Host sumber yang didukung aplikasi + host challenge Cloudflare. */
    private val ALLOWED_HOSTS = setOf(
        "twmanga.com", "baozimh.com",
        "manwang.net", "wmanhua.com", "koudaimh.com",
        "jjaptoon008.com", "jjabtoon001.com",
        "goodtoon009.com", "goodtoon005.com", "goodtoon.top",
        "rumanhua.org", "manhuahao.com", "mgeko.cc",
        "hipmh.com", "reader.hipmh.top", "s3file.top", "s3imgs.top",
        "mangadex.org", "challenges.cloudflare.com"
    )

    /**
     * Terapkan pengaturan aman. JavaScript sengaja tidak diubah di sini:
     * caller menentukan sendiri apakah diperlukan.
     */
    fun harden(settings: WebSettings) {
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
    }

    /** Izinkan hanya HTTPS ke host yang dikenal (atau host awal halaman). */
    fun isAllowed(rawUrl: String, initialHost: String? = null): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        val matches: (String) -> Boolean = { allowed ->
            val a = allowed.lowercase()
            host == a || host.endsWith(".$a")
        }
        return initialHost?.let(matches) == true || ALLOWED_HOSTS.any(matches)
    }

    fun hostOf(rawUrl: String): String? =
        runCatching { URI(rawUrl).host?.lowercase() }.getOrNull()
}
