package com.trialfetch.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test parser URL tempel untuk semua sumber.
 *
 * Murni JVM (hanya java.net.URI + Regex), jadi jalan di unit test biasa
 * tanpa Robolectric/emulator.
 */
class UrlParserTest {

    private fun series(url: String): UrlParser.Parsed.Series {
        val p = UrlParser.parse(url)
        assertTrue("diharapkan Series untuk $url, dapat $p", p is UrlParser.Parsed.Series)
        return p as UrlParser.Parsed.Series
    }

    private fun chapter(url: String): UrlParser.Parsed.Chapter {
        val p = UrlParser.parse(url)
        assertTrue("diharapkan Chapter untuk $url, dapat $p", p is UrlParser.Parsed.Chapter)
        return p as UrlParser.Parsed.Chapter
    }

    @Test fun manwangSeries() {
        val r = series("https://manwang.net/book/608121")
        assertEquals(Source.MANWANG, r.source)
        assertEquals("608121", r.comicId)
    }

    @Test fun manwangChapter() {
        val r = chapter("https://manwang.net/chapter/608121-230851")
        assertEquals(Source.MANWANG, r.source)
    }

    @Test fun manwangTanpaSkema() {
        val r = series("manwang.net/book/1")
        assertEquals(Source.MANWANG, r.source)
        assertEquals("1", r.comicId)
    }

    @Test fun baozimhSeries() {
        val r = series("https://www.twmanga.com/comic/aiqingduomaomao-zhuimingaigong")
        assertEquals(Source.BAOZIMH, r.source)
        assertEquals("aiqingduomaomao-zhuimingaigong", r.comicId)
    }

    @Test fun baozimhChapterPageDirect() {
        val r = chapter(
            "https://www.twmanga.com/user/page_direct" +
                "?comic_id=aiqingduomaomao-zhuimingaigong&section_slot=0&chapter_slot=12"
        )
        assertEquals(Source.BAOZIMH, r.source)
        assertTrue("query harus dipertahankan untuk dibaca chapter()", r.url.contains("comic_id="))
    }

    @Test fun baozimhPathApiAppBukanSeriesPalsu() {
        // Path API app mengandung "/comic/chapter/…" — jangan tertangkap
        // sebagai series dengan comicId "chapter".
        val r = series("https://appgb1.baozimh.com/baozimhapp/comic/chapter/abc-def/0_12.html")
        assertEquals(Source.BAOZIMH, r.source)
        assertEquals("abc-def", r.comicId)
    }

    @Test fun wmanhuaSeriesDanChapter() {
        val s = series("https://www.wmanhua.com/comic/12345")
        assertEquals(Source.WMANHUA, s.source)
        assertEquals("12345", s.comicId)
        val c = chapter("https://www.wmanhua.com/chapter/123-456.html")
        assertEquals(Source.WMANHUA, c.source)
    }

    @Test fun koudaimhSeriesDanChapter() {
        val s = series("https://m.koudaimh.com/manhua/abc-def")
        assertEquals(Source.KOUDAIMH, s.source)
        val c = chapter("https://m.koudaimh.com/manhua/abc-def/99.html")
        assertEquals(Source.KOUDAIMH, c.source)
    }

    @Test fun jjabtoonSeriesDanChapter() {
        val s = series("https://jjabtoon001.com/webtoons/1")
        assertEquals(Source.JJABTOON, s.source)
        assertEquals("1", s.comicId)
        val c = chapter("https://jjabtoon001.com/episodes/424481")
        assertEquals(Source.JJABTOON, c.source)
    }

    @Test fun jjaptoonSeriesDanChapter() {
        val s = series("https://www.jjaptoon008.com/comics/14506")
        assertEquals(Source.JJAPTOON, s.source)
        assertEquals("14506", s.comicId)
        val c = chapter("https://www.jjaptoon008.com/chapters/1344805")
        assertEquals(Source.JJAPTOON, c.source)
    }

    @Test fun goodtoonSeriesDanChapter() {
        val s = series("https://www.goodtoon005.com/manga/gt-21404/")
        assertEquals(Source.GOODTOON, s.source)
        assertEquals("gt-21404", s.comicId)
        val c1 = chapter("https://www.goodtoon005.com/manga/gt-21404/56/")
        assertEquals(Source.GOODTOON, c1.source)
        val c2 = chapter("https://www.goodtoon005.com/manga/gt-21404/chapter-56/")
        assertEquals(Source.GOODTOON, c2.source)
    }

    @Test fun rumanhuaSeriesDanChapter() {
        val s = series("https://www.rumanhua.org/news/617919")
        assertEquals(Source.RUMAN, s.source)
        assertEquals("617919", s.comicId)
        val c = chapter("https://www.rumanhua.org/show/lwXwyiDl5y.html")
        assertEquals(Source.RUMAN, c.source)
    }

    @Test fun manhuahaoSeriesDanChapter() {
        val s = series("https://m.manhuahao.com/douluodalu-6dzKG")
        assertEquals(Source.MANHUAHAO, s.source)
        assertEquals("douluodalu-6dzKG", s.comicId)
        val c = chapter("https://m.manhuahao.com/douluodalu-6dzKG/1.html")
        assertEquals(Source.MANHUAHAO, c.source)
        val r = UrlParser.parse("https://m.manhuahao.com/search?q=x")
        assertTrue(r is UrlParser.Parsed.Unknown)
    }

    @Test fun urlAsingUnknown() {
        val p = UrlParser.parse("https://example.com/comic/1")
        assertTrue(p is UrlParser.Parsed.Unknown)
    }

    @Test fun urlKosongUnknown() {
        val p = UrlParser.parse("   ")
        assertTrue(p is UrlParser.Parsed.Unknown)
    }
}
