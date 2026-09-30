package com.trialfetch.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanTextTest {

    @Test fun entityDasar() {
        assertEquals("a&b", cleanText("a&amp;b"))
        assertEquals("a<b", cleanText("a&lt;b"))
        assertEquals("a>b", cleanText("a&gt;b"))
        assertEquals("a\"b", cleanText("a&quot;b"))
        assertEquals("a'b", cleanText("a&apos;b"))
    }

    @Test fun entityDesimal() {
        assertEquals("A", cleanText("&#65;"))
    }

    @Test fun entityHex() {
        assertEquals("A", cleanText("&#x41;"))
        assertEquals("A", cleanText("&#X41;"))
    }

    @Test fun whitespaceDirapatkan() {
        assertEquals("a b c", cleanText("  a\n\tb   c  "))
    }

    @Test fun nbsp() {
        assertEquals("a b", cleanText("a&nbsp;b"))
    }
}
