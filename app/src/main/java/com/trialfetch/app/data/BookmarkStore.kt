package com.trialfetch.app.data

import android.content.Context
import com.trialfetch.app.core.Source
import org.json.JSONArray
import org.json.JSONObject

/**
 * Series yang disimpan pengguna.
 *
 * Disimpan di SharedPreferences sebagai array JSON. org.json dipakai
 * (bawaan Android) supaya tidak menambah dependency hanya untuk ini.
 */
data class SavedSeries(
    val source: Source,
    val comicId: String,
    val title: String,
    val author: String = "",
    val coverUrl: String = "",
    val savedAt: Long = System.currentTimeMillis(),
    /** Chapter terbaru yang sudah diketahui; dipakai cek "ada chapter baru". */
    val latestChapterTitle: String = ""
) {
    fun key(): String = "${source.id}::$comicId"

    fun toJson(): JSONObject = JSONObject()
        .put("source", source.id)
        .put("comicId", comicId)
        .put("title", title)
        .put("author", author)
        .put("coverUrl", coverUrl)
        .put("savedAt", savedAt)
        .put("latestChapterTitle", latestChapterTitle)

    companion object {
        fun fromJson(o: JSONObject): SavedSeries? {
            val source = Source.from(o.optString("source")) ?: return null
            val comicId = o.optString("comicId").takeIf { it.isNotBlank() } ?: return null
            return SavedSeries(
                source = source,
                comicId = comicId,
                title = o.optString("title", comicId),
                author = o.optString("author", ""),
                coverUrl = o.optString("coverUrl", ""),
                savedAt = o.optLong("savedAt", 0L),
                latestChapterTitle = o.optString("latestChapterTitle", "")
            )
        }
    }
}

class BookmarkStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("trialfetch_bookmarks", Context.MODE_PRIVATE)

    fun load(): List<SavedSeries> {
        val raw = prefs.getString(KEY_SAVED, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> SavedSeries.fromJson(arr.getJSONObject(i)) }
                .filterNotNull()
                .sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())
    }

    fun saveAll(items: List<SavedSeries>) {
        val arr = JSONArray()
        for (it in items) arr.put(it.toJson())
        prefs.edit().putString(KEY_SAVED, arr.toString()).apply()
    }

    private companion object {
        const val KEY_SAVED = "saved_series"
    }
}
