package com.trialfetch.app.data

import android.content.Context
import com.trialfetch.app.core.DohProvider

/**
 * Penyimpanan [DownloadSettings] di SharedPreferences.
 *
 * Sebelumnya setelan hanya hidup di memori ViewModel, jadi semua pilihan user
 * hilang begitu aplikasi ditutup. Ini menyimpannya ke disk.
 *
 * SharedPreferences dipakai supaya tidak menambah dependency baru. Untuk
 * jangka panjang DataStore lebih sesuai (dibaca lewat Flow, aman dari
 * request threading), tapi untuk setelan sekecil ini SharedPreferences sudah
 * cukup dan risikonya rendah.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("trialfetch_settings", Context.MODE_PRIVATE)

    fun load(): DownloadSettings {
        val naming = NamingRule(
            // Pola lama bisa saja tersimpan dalam bentuk yang tidak valid
            // (dibersihkan juga di NamingRule.fileName).
            pattern = NamingRule.sanitizePattern(
                prefs.getString(KEY_NAMING_PATTERN, null) ?: "{n}"
            ),
            padDigits = prefs.getInt(KEY_NAMING_PAD, 4).coerceIn(1, 8)
        )
        val output = prefs.getString(KEY_OUTPUT_MODE, null)
            ?.let { name -> OutputMode.entries.firstOrNull { it.name == name } }
            ?: OutputMode.FOLDER

        return DownloadSettings(
            outputMode = output,
            naming = naming,
            cropBanner = prefs.getBoolean(KEY_CROP_BANNER, true),
            themeMode = ThemeMode.fromName(prefs.getString(KEY_THEME, null)),
            dohEnabled = prefs.getBoolean(KEY_DOH_ENABLED, false),
            dohProvider = DohProvider.fromName(prefs.getString(KEY_DOH_PROVIDER, null)),
            readerMode = ReaderMode.fromName(prefs.getString(KEY_READER_MODE, null))
        )
    }

    fun save(settings: DownloadSettings) {
        prefs.edit()
            .putString(KEY_OUTPUT_MODE, settings.outputMode.name)
            .putString(KEY_NAMING_PATTERN, settings.naming.sanitizedPattern())
            .putInt(KEY_NAMING_PAD, settings.naming.padDigits)
            .putBoolean(KEY_CROP_BANNER, settings.cropBanner)
            .putString(KEY_THEME, settings.themeMode.name)
            .putBoolean(KEY_DOH_ENABLED, settings.dohEnabled)
            .putString(KEY_DOH_PROVIDER, settings.dohProvider.name)
            .putString(KEY_READER_MODE, settings.readerMode.name)
            .apply()
    }

    private companion object {
        const val KEY_OUTPUT_MODE = "output_mode"
        const val KEY_NAMING_PATTERN = "naming_pattern"
        const val KEY_NAMING_PAD = "naming_pad"
        const val KEY_CROP_BANNER = "crop_banner"
        const val KEY_THEME = "theme_mode"
        const val KEY_DOH_ENABLED = "doh_enabled"
        const val KEY_DOH_PROVIDER = "doh_provider"
        const val KEY_READER_MODE = "reader_mode"
    }
}
