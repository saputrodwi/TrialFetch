package com.trialfetch.app.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Menelusuri URL chapter kembali ke ID series-nya (fitur "Buka dari URL").
 *
 * Murni ekstraksi string, tidak ada request jaringan, tapi sumbernya
 * dipakai apa adanya karena override-nya memang ada di kelas itu.
 */
class SeriesIdFromChapterUrlTest {

    private val baozimh = BaozimhSource(HttpClient())
    private val goodtoon = GoodtoonSource(HttpClient())

    @Test fun baozimhPageDirect() = runBlocking {
        val url = "https://www.twmanga.com/user/page_direct" +
            "?comic_id=aiqingduomaomao-zhuimingaigong&section_slot=0&chapter_slot=12"
        assertEquals("aiqingduomaomao-zhuimingaigong", baozimh.seriesIdFromChapterUrl(url))
    }

    @Test fun baozimhTanpaQueryNull() = runBlocking {
        assertNull(baozimh.seriesIdFromChapterUrl("https://www.twmanga.com/comic/abc"))
    }

    @Test fun goodtoonChapter() = runBlocking {
        assertEquals(
            "gt-21404",
            goodtoon.seriesIdFromChapterUrl("https://www.goodtoon005.com/manga/gt-21404/56/")
        )
    }

    @Test fun goodtoonChapterBentukLain() = runBlocking {
        assertEquals(
            "gt-21404",
            goodtoon.seriesIdFromChapterUrl("https://www.goodtoon005.com/manga/gt-21404/chapter-56/")
        )
    }

    @Test fun defaultNull() = runBlocking {
        val sumberTanpaOverride = object : ComicSource {
            override val source = Source.BAOZIMH
            override suspend fun search(query: String) = emptyList<SearchResult>()
            override suspend fun series(comicId: String): SeriesInfo = error("tidak dipakai")
            override suspend fun chapter(url: String): ChapterPage = error("tidak dipakai")
        }
        assertNull(sumberTanpaOverride.seriesIdFromChapterUrl("https://x/1"))
    }
}
