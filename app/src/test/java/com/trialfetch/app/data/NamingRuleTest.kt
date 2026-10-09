package com.trialfetch.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NamingRuleTest {

    @Test fun default0001() {
        val r = NamingRule()
        assertEquals("0001.jpg", r.fileName(1, "jpg"))
        assertEquals("0012.jpg", r.fileName(12, "jpg"))
        assertEquals("0121.jpg", r.fileName(121, "jpg"))
    }

    @Test fun preset001() {
        val r = NamingRule("{n}", 3)
        assertEquals("001.jpg", r.fileName(1, "jpg"))
    }

    @Test fun presetPage() {
        val r = NamingRule("page_{n}", 4)
        assertEquals("page_0001.jpg", r.fileName(1, "jpg"))
    }

    @Test fun placeholderIndeks() {
        val r = NamingRule("img_{i}", 3)
        // {i} mulai dari 0
        assertEquals("img_000.jpg", r.fileName(1, "jpg"))
        assertEquals("img_001.jpg", r.fileName(2, "jpg"))
    }

    @Test fun polaIlegalDibersihkan() {
        // "/" membuat nama jadi jalur (atau ditolak sistem file).
        assertEquals("img_0001.jpg", NamingRule("img/{n}", 4).fileName(1, "jpg"))
        assertEquals("{n}", NamingRule.sanitizePattern("""a:b*c?"d<e>f|g"""))
        // Tanpa placeholder semua halaman menulis nama yang sama.
        assertEquals("{n}", NamingRule.sanitizePattern("halaman"))
        assertEquals("halaman_{n}", NamingRule.sanitizePattern("halaman_{n}"))
    }

    @Test fun padTidakMasukAkalTetapAman() {
        // 0 -> 1 digit (padStart(1) tidak menambah nol), 99 -> dibatasi 8
        // supaya tidak bikin nama file raksasa.
        assertEquals("1.jpg", NamingRule("{n}", 0).fileName(1, "jpg"))
        assertEquals("12345678.jpg", NamingRule("{n}", 0).fileName(12345678, "jpg"))
        assertEquals("00000001.jpg", NamingRule("{n}", 99).fileName(1, "jpg"))
        assertEquals("001.jpg", NamingRule("{n}", 3).fileName(1, "jpg"))
    }

    @Test fun semuaPresetValid() {
        for ((rule, _) in NamingRule.PRESETS) {
            // Tidak boleh crash dan harus menghasilkan nama ber-ekstensi.
            // (Isi angka tidak dicek di sini karena preset {i} memang
            // mulai dari 0 sehingga halaman 7 jadi img_006.)
            val name = rule.fileName(7, "webp")
            assertEquals(true, name.endsWith(".webp"))
            assertEquals(true, name.length > ".webp".length)
        }
    }
}
