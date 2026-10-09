package com.trialfetch.app.data

import com.trialfetch.app.core.DohProvider

/**
 * Cara hasil unduhan disimpan.
 */
enum class OutputMode(val label: String) {
    FOLDER("Folder"),
    ZIP("ZIP")
}

/**
 * Aturan penamaan file gambar.
 *
 * Default-nya urutan sederhana: 0001, 0002, 0003, … sesuai nomor halaman,
 * supaya urutan baca di file manager sudah benar tanpa perlu sorting.
 * [pattern] menerima satu placeholder {n} yang diganti nomor halaman, dan
 * satu {i} yang diganti indeks (mulai 0). Contoh:
 *   - "{n}"            → 0001
 *   - "page_{n}"       → page_0001
 *   - "{n}_img"        → 0001_img
 */
data class NamingRule(
    val pattern: String = "{n}",
    val padDigits: Int = 4
) {
    fun fileName(page: Int, extension: String): String {
        val base = sanitizedPattern()
            .replace("{n}", page.toString().padStart(padDigits.coerceIn(1, 8), '0'))
            .replace("{i}", (page - 1).toString().padStart(padDigits.coerceIn(1, 8), '0'))
        return "$base.$extension"
    }

    /** Pola setelah dibersihkan (lihat [sanitizePattern]). */
    fun sanitizedPattern(): String = sanitizePattern(pattern)

    companion object {
        /**
         * Pola nama file dipercaya begitu saja oleh sistem file, padahal bisa
         * berisi karakter yang dilarang (`/`, `:`, `*`, `?`, `"`, `<`, `>`, `|`
         * dan karakter kontrol) sehingga `File.createNewFile` gagal. Pola juga
         * wajib punya placeholder `{n}`/`{i}` — tanpa itu semua halaman
         * menulis nama yang sama dan saling menimpa.
         *
         * [SettingsStore] memakai fungsi ini saat membaca dan menyimpan, dan
         * [fileName] memakainya lagi sebagai jaring pengaman terakhir.
         */
        fun sanitizePattern(raw: String): String {
            val cleaned = ILLEGAL.replace(raw, "_").trim()
            return if (cleaned.contains("{n}") || cleaned.contains("{i}")) cleaned else "{n}"
        }

        private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

        val PRESETS = listOf(
            NamingRule("{n}", 4) to "0001 (default)",
            NamingRule("{n}", 3) to "001",
            NamingRule("{n}", 2) to "01",
            NamingRule("page_{n}", 4) to "page_0001",
            NamingRule("{n}_img", 4) to "0001_img",
            NamingRule("img_{i}", 3) to "img_000"
        )
    }
}

data class DownloadSettings(
    val outputMode: OutputMode = OutputMode.FOLDER,
    val naming: NamingRule = NamingRule(),
    val cropBanner: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dohEnabled: Boolean = false,
    val dohProvider: DohProvider = DohProvider.CLOUDFLARE,
    val readerMode: ReaderMode = ReaderMode.PAGED
)

/** Mode baca: geser per halaman, atau gulir vertikal ala webtoon. */
enum class ReaderMode(val label: String) {
    PAGED("Halaman"),
    WEBTOON("Webtoon");

    companion object {
        fun fromName(value: String?): ReaderMode =
            entries.firstOrNull { it.name == value } ?: PAGED
    }
}

/**
 * Pilihan tema tampilan.
 *
 * [SYSTEM] mengikuti setelan sistem operasi, jadi tema berubah sendiri sesuai
 * mode terang atau gelap yang aktif di HP.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap");

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Lokasi penulisan yang diminta pengguna. */
object OutputPaths {
    const val ROOT = "TrialFetch"
}
