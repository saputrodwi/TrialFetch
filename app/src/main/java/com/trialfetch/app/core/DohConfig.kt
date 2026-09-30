package com.trialfetch.app.core

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * DNS over HTTPS.
 *
 * Sebagian ISP / jaringan memblokir atau membelokkan DNS biasa, sehingga
 * domain sumber komik tidak bisa di-resolve padahal koneksinya baik-baik
 * saja. DoH mengirim query DNS lewat HTTPS terenkripsi ke resolver publik,
 * sehingga tidak bisa diintip atau dimanipulasi di tengah jalan.
 *
 * URL resolver memakai IP literal (bukan hostname) supaya tidak ada masalah
 * ayam-dan-telur: hostname resolver sendiri butuh DNS untuk di-resolve.
 * Sertifikat TLS Cloudflare/Google/Quad9 mencakup SAN IP sehingga verifikasi
 * tetap jalan normal.
 */
enum class DohProvider(
    val label: String,
    val url: String,
    val ips: List<String>
) {
    CLOUDFLARE(
        "Cloudflare (1.1.1.1)",
        "https://1.1.1.1/dns-query",
        listOf("1.1.1.1", "1.0.0.1")
    ),
    GOOGLE(
        "Google (8.8.8.8)",
        "https://8.8.8.8/dns-query",
        listOf("8.8.8.8", "8.8.4.4")
    ),
    QUAD9(
        "Quad9 (9.9.9.9)",
        "https://9.9.9.9/dns-query",
        listOf("9.9.9.9", "149.112.112.112")
    );

    companion object {
        fun fromName(value: String?): DohProvider =
            entries.firstOrNull { it.name == value } ?: CLOUDFLARE
    }
}

object DohConfig {

    /**
     * Membangun [Dns] DoH untuk provider pilihan.
     *
     * [bootstrap] adalah client polos (DNS sistem) yang dipakai hanya untuk
     * query HTTPS ke resolver. Client utama memakai hasil ini untuk semua
     * resolve berikutnya, jadi DNS sistem tidak dipakai lagi setelahnya.
     */
    fun buildDns(provider: DohProvider, bootstrap: OkHttpClient): Dns {
        return DnsOverHttps.Builder()
            .client(bootstrap)
            .url(provider.url.toHttpUrl())
            .bootstrapDnsHosts(
                provider.ips.mapNotNull {
                    runCatching { InetAddress.getByName(it) }.getOrNull()
                }
            )
            .includeIPv6(false)
            .build()
    }
}
