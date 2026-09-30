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
