package com.trialfetch.app.core

/**
 * Mengenali URL yang ditempel pengguna lalu memetakannya ke sumber +
 * jenis konten (series atau chapter).
 *
 * Dipakai untuk fitur "tempel URL" sehingga pengguna tidak wajib
 * lewat halaman pencarian — cukup menyalin link dari browser.
 */
object UrlParser {

    sealed interface Parsed {
        data class Series(val source: Source, val comicId: String) : Parsed
        data class Chapter(val source: Source, val url: String) : Parsed
        data class Unknown(val reason: String) : Parsed
    }

    private fun host(url: String): String? = runCatching {
        val u = java.net.URI(url.trim())
        if (u.scheme == null || u.host == null) return null
        u.host.lowercase().removePrefix("www.")
    }.getOrNull()

    fun parse(rawUrl: String): Parsed {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return Parsed.Unknown("URL kosong")

        // Toleran terhadap input yang tidak lengkap ("manwang.net/book/1").
        val withScheme = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
        val host = host(withScheme)
            ?: return Parsed.Unknown("URL tidak valid")

        return when {
            // --- Manwang: /book/{id} = series, /chapter/{book}-{ch} = chapter
            host == "manwang.net" || host.endsWith(".manwang.net") -> {
                val book = Regex("""/book/(\d+)""").find(withScheme)?.groupValues?.get(1)
                if (book != null) {
                    Parsed.Series(Source.MANWANG, book)
                } else {
                    val ch = Regex("""/chapter/(\d+-\d+)""").find(withScheme)?.groupValues?.get(1)
                    if (ch != null) {
                        Parsed.Chapter(Source.MANWANG, "https://manwang.net/chapter/$ch")
                    } else {
                        Parsed.Unknown("URL Manwang tidak dikenali")
                    }
                }
            }

            // --- Baozimh / twmanga: /comic/{slug}, chapter via ?/page_direct
            host == "baozimh.com" || host.endsWith(".baozimh.com") ||
                host == "twmanga.com" || host.endsWith(".twmanga.com") -> {
                val slug = Regex("""/comic/([a-z0-9\-]+)""", RegexOption.IGNORE_CASE)
                    .find(withScheme)?.groupValues?.get(1)
                if (slug != null) {
                    Parsed.Series(Source.BAOZIMH, slug)
                } else {
                    Parsed.Unknown("URL Baozimh tidak dikenali")
                }
            }

            // --- Wmanhua: /comic/{id}.html, /chapter/{a}-{b}.html
            host == "wmanhua.com" || host.endsWith(".wmanhua.com") -> {
                val id = Regex("""/comic/(\d+)""").find(withScheme)?.groupValues?.get(1)
                val ch = Regex("""/chapter/(\d+-\d+)""").find(withScheme)?.groupValues?.get(1)
                when {
                    ch != null -> Parsed.Chapter(Source.WMANHUA, "https://www.wmanhua.com/chapter/$ch.html")
                    id != null -> Parsed.Series(Source.WMANHUA, id)
                    else -> Parsed.Unknown("URL Wmanhua tidak dikenali")
                }
            }

            // --- Koudaimh: /manhua/{slug}, chapter /manhua/{slug}/{id}.html
            host == "koudaimh.com" || host.endsWith(".koudaimh.com") -> {
                val m = Regex("""/manhua/([a-z0-9\-]+)(?:/(\d+)\.html)?""", RegexOption.IGNORE_CASE)
                    .find(withScheme)
                if (m == null) {
                    Parsed.Unknown("URL Koudaimh tidak dikenali")
                } else if (m.groupValues[2].isNotEmpty()) {
                    Parsed.Chapter(
                        Source.KOUDAIMH,
                        "https://m.koudaimh.com/manhua/${m.groupValues[1]}/${m.groupValues[2]}.html"
                    )
                } else {
                    Parsed.Series(Source.KOUDAIMH, m.groupValues[1])
                }
            }

            // --- Jjabtoon: /webtoons/{id} = series, /episodes/{id} = chapter.
            // Domain bernomor (jjabtoon001.com, dst) jadi dicocokkan longgar.
            host.contains("jjabtoon") -> {
                val ep = Regex("""/episodes/(\d+)""").find(withScheme)?.groupValues?.get(1)
                if (ep != null) {
                    val base = runCatching {
                        val u = java.net.URI(withScheme)
                        "${u.scheme}://${u.host}"
                    }.getOrDefault("https://jjabtoon001.com")
                    Parsed.Chapter(Source.JJABTOON, "$base/episodes/$ep")
                } else {
                    val id = Regex("""/webtoons/(\d+)""").find(withScheme)?.groupValues?.get(1)
                    if (id != null) Parsed.Series(Source.JJABTOON, id)
                    else Parsed.Unknown("URL Jjabtoon tidak dikenali")
                }
            }

            // --- Jjaptoon: /comics/{id} = series, /chapters/{id} = chapter.
            // Domain bernomor dan TLD bisa berganti, jadi dicocokkan longgar.
            host.contains("jjaptoon") -> {
                val ch = Regex("""/chapters/(\d+)""").find(withScheme)?.groupValues?.get(1)
                if (ch != null) {
                    val base = runCatching {
                        val u = java.net.URI(withScheme)
                        "${u.scheme}://${u.host}"
                    }.getOrDefault("https://www.jjaptoon008.com")
                    Parsed.Chapter(Source.JJAPTOON, "$base/chapters/$ch")
                } else {
                    val id = Regex("""/comics/(\d+)""").find(withScheme)?.groupValues?.get(1)
                    if (id != null) Parsed.Series(Source.JJAPTOON, id)
                    else Parsed.Unknown("URL Jjaptoon tidak dikenali")
                }
            }

            // --- Goodtoon: /manga/{slug}/ = series,
            // /manga/{slug}/{n}/ atau /chapter-{n}/ = chapter.
            host.contains("goodtoon") && !host.startsWith("img.") -> {
                val ch = Regex("""/manga/([a-z0-9\-]+)/(?:chapter-)?(\d+)/""",
                    RegexOption.IGNORE_CASE).find(withScheme)
                if (ch != null) {
                    Parsed.Chapter(Source.GOODTOON, withScheme.substringBefore("?"))
                } else {
                    val slug = Regex("""/manga/([a-z0-9\-]+)""",
                        RegexOption.IGNORE_CASE).find(withScheme)?.groupValues?.get(1)
                    if (slug != null) Parsed.Series(Source.GOODTOON, slug.trimEnd('/'))
                    else Parsed.Unknown("URL Goodtoon tidak dikenali")
                }
            }

            // --- Rumanhua: /news/{id} = series, /show/{kode}.html = chapter.
            host == "rumanhua.org" || host.endsWith(".rumanhua.org") -> {
                val show = Regex("""/show/([^/?#.]+)""").find(withScheme)?.groupValues?.get(1)
                if (show != null) {
                    Parsed.Chapter(Source.RUMAN, "https://www.rumanhua.org/show/$show.html")
                } else {
                    val id = Regex("""/news/(\d+)""").find(withScheme)?.groupValues?.get(1)
                    if (id != null) Parsed.Series(Source.RUMAN, id)
                    else Parsed.Unknown("URL Rumanhua tidak dikenali")
                }
            }

            else -> Parsed.Unknown("Situs \"$host\" belum didukung")
        }
    }
}
