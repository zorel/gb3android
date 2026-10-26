package org.zorel.gb3android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageParserTest {
    @Test
    fun parsesFormattingAndNorloges() {
        val parsed = MessageParser.parse("<b>hello</b> 12:34:56@trib and 12:34")
        assertTrue(parsed.tokens.first() is MessageToken.Text)
        val bold = parsed.tokens.first() as MessageToken.Text
        assertEquals("hello", bold.text)
        assertTrue(bold.format.bold)
        assertEquals(2, parsed.norloges.size)
        assertEquals("12:34:56", parsed.norloges[0].time)
        assertEquals("trib", parsed.norloges[0].targetTribune)
        assertEquals("12:34", parsed.norloges[1].time)
        assertNull(parsed.norloges[1].targetTribune)
    }

    @Test
    fun parsesFullDateNorloge() {
        val parsed = MessageParser.parse("written 2026-10-26T12:34:56@dlfp")
        val ref = parsed.norloges.single()
        assertEquals("12:34:56", ref.time)
        assertEquals("dlfp", ref.targetTribune)
        assertEquals("2026-10-26 12:34:56", ref.title)
    }

    @Test
    fun parsesTotozUrlAndBigorno() {
        val parsed = MessageParser.parse("[:pikachu] https://example.org/foo dave< hi")
        assertTrue(parsed.tokens.any { it is MessageToken.Totoz && it.name == "pikachu" })
        assertTrue(parsed.tokens.any {
            it is MessageToken.Link && it.href == "https://example.org/foo"
        })
        assertTrue("dave" in parsed.bigornos)
        val text = parsed.tokens.filterIsInstance<MessageToken.Text>().joinToString("") { it.text }
        assertFalse(text.contains("dave<"))
    }

    @Test
    fun parsesAnchorsAsUrls() {
        val parsed = MessageParser.parse("""<a href="https://example.org/page">click</a>""")
        val link = parsed.tokens.filterIsInstance<MessageToken.Link>().single()
        assertEquals("https://example.org/page", link.href)
    }

    @Test
    fun autoClosesTagsLikeGc2() {
        val parsed = MessageParser.parse("<b>bold <i>both</b> plain")
        val first = parsed.tokens.filterIsInstance<MessageToken.Text>()
        assertTrue(first[0].format.bold)
        assertTrue(first[1].format.bold)
        assertTrue(first[1].format.italic)
        assertFalse(first[2].format.bold)
        assertFalse(first[2].format.italic)
    }

    @Test
    fun countsAndMarksSpoilers() {
        val parsed = MessageParser.parse("before <spoiler>hidden</spoiler> after")
        assertEquals(1, parsed.spoilerCount)
        val hidden = parsed.tokens.filterIsInstance<MessageToken.Text>()
            .first { it.text == "hidden" }
        assertEquals(0, hidden.spoiler)
    }

    @Test
    fun decodesEntitiesButDoesNotInterpretThemAsMarkup() {
        val parsed = MessageParser.parse("&lt;b&gt;not bold&lt;/b&gt; &amp; ok")
        val text = parsed.tokens.filterIsInstance<MessageToken.Text>().joinToString("") { it.text }
        assertEquals("<b>not bold</b> & ok", text)
        assertFalse(parsed.tokens.filterIsInstance<MessageToken.Text>().first().format.bold)
    }

    @Test
    fun rewritesBouchotSuffixOnTribuneSwitch() {
        val message = "reply 12:34:56 old 12:34:56@new 12:34:56@other"
        val rewritten = MessageParser.rewriteBouchotSuffix(message, "old", "new")
        assertEquals("reply 12:34:56@old old 12:34:56 12:34:56@other", rewritten)
    }

    @Test
    fun shortNorlogeDoesNotSwallowSeconds() {
        val parsed = MessageParser.parse("12:34:56")
        assertEquals("12:34:56", parsed.norloges.single().time)
    }

    @Test
    fun keepsBalancedParenthesesInUrls() {
        val parsed = MessageParser.parse("https://en.wikipedia.org/wiki/Foo_(bar)")
        val link = parsed.tokens.filterIsInstance<MessageToken.Link>().single()
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", link.href)
    }

    @Test
    fun stripsUnbalancedTrailingPunctuationOnly() {
        val parsed = MessageParser.parse("see https://example.org/foo).")
        val link = parsed.tokens.filterIsInstance<MessageToken.Link>().single()
        assertEquals("https://example.org/foo", link.href)
        assertTrue(link.label.endsWith(")."))
    }

    @Test
    fun compactBackendTimeIsExposedAsClockAndDate() {
        val post = org.zorel.gb3android.data.Post(
            id = 1,
            time = "20261026123456",
            info = "dave",
            login = "",
            message = "hello",
            tribune = "trib",
        )
        assertEquals("12:34:56", post.clock)
        assertEquals("123456", post.clockKey)
        assertEquals("2026-10-26", post.fullDate)
    }
}
