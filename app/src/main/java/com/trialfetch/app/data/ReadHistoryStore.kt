package com.trialfetch.app.data

import android.content.Context
import com.trialfetch.app.core.Source
import org.json.JSONArray
import org.json.JSONObject

/**
 * Satu posisi baca terakhir pada sebuah chapter.
 *
 * Satu entri per (source, comicId, chapterId); dibuka lagi = update posisi,
 * bukan tambah baris. Maks 100 entri terbaru, yang lama dibuang.
 */
data class HistoryEntry(
    val source: Source,
    val comicId: String,
    val title: String,
    val coverUrl: String = "",
    val chapterId: String,
    val chapterTitle: String,
    val page: Int = 0,
    val totalPages: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun key(): String = "${source.id}::$comicId::$chapterId"

    /** 0..1, atau null bila total tidak diketahui. */
    val fraction: Float?
        get() = if (totalPages > 0) (page.coerceIn(0, totalPages).toFloat() / totalPages) else null

    fun toJson(): JSONObject = JSONObject()
        .put("source", source.id)
        .put("comicId", comicId)
        .put("title", title)
        .put("coverUrl", coverUrl)
        .put("chapterId", chapterId)
        .put("chapterTitle", chapterTitle)
        .put("page", page)
        .put("totalPages", totalPages)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(o: JSONObject): HistoryEntry? {
            val source = Source.from(o.optString("source")) ?: return null
            val comicId = o.optString("comicId").takeIf { it.isNotBlank() } ?: return null
            val chapterId = o.optString("chapterId").takeIf { it.isNotBlank() } ?: return null
            return HistoryEntry(
                source = source,
                comicId = comicId,
                title = o.optString("title", comicId),
                coverUrl = o.optString("coverUrl", ""),
                chapterId = chapterId,
                chapterTitle = o.optString("chapterTitle", chapterId),
                page = o.optInt("page", 0),
                totalPages = o.optInt("totalPages", 0),
                updatedAt = o.optLong("updatedAt", 0L)
            )
        }
    }
}

class ReadHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("trialfetch_history", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> HistoryEntry.fromJson(arr.getJSONObject(i)) }
                .filterNotNull()
                .sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    private fun saveAll(items: List<HistoryEntry>) {
        val arr = JSONArray()
        for (it in items.take(MAX)) arr.put(it.toJson())
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    /** Catat/refresh posisi baca; pindahkan ke paling atas. */
    fun record(entry: HistoryEntry) {
        val cur = load().filterNot { it.key() == entry.key() }.toMutableList()
        cur.add(0, entry.copy(updatedAt = System.currentTimeMillis()))
        saveAll(cur)
    }

    fun remove(key: String) {
        saveAll(load().filterNot { it.key() == key })
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val KEY_HISTORY = "read_history"
        const val MAX = 100
    }
}
