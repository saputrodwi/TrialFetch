package com.trialfetch.app.ui

import android.net.Uri

/**
 * Route navigasi aplikasi.
 *
 * Series memakai query argumen (bukan path) supaya comicId yang
 * mengandung karakter aneh tetap aman tanpa encode manual.
 */
object Routes {
    const val HOME = "home"
    const val SERIES = "series"
    const val SAVED = "saved"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"

    const val ARG_SOURCE = "source"
    const val ARG_COMIC_ID = "comicId"

    fun series(sourceId: String, comicId: String): String =
        "$SERIES?$ARG_SOURCE=${Uri.encode(sourceId)}&$ARG_COMIC_ID=${Uri.encode(comicId)}"
}
