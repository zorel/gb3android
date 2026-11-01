package org.zorel.gb3android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {
    @Test
    fun tribuneKeysRoundTripForBothSources() {
        val gb2c = TribuneKey(SourceId.Gb2c("https://gb2c.example"), "moules")
        val direct = TribuneKey(SourceId.Direct("moules"), "moules")

        assertEquals(gb2c, TribuneKey.fromKey(gb2c.key))
        assertEquals(direct, TribuneKey.fromKey(direct.key))
        assertEquals("gb2c:https://gb2c.example|moules", gb2c.key)
        assertEquals("direct:moules|moules", direct.key)
        assertNull(TribuneKey.fromKey("broken"))
    }

    @Test
    fun postKeysIncludeTheSource() {
        val direct = Post(1, "20261027120000", "", "", "hello", "moules", SourceId.Direct("moules"))
        val gb2c = direct.copy(source = SourceId.Gb2c("https://gb2c.example"))

        assertEquals("direct:moules|moules:1", direct.key)
        assertEquals("gb2c:https://gb2c.example|moules:1", gb2c.key)
        assertTrue(direct.key != gb2c.key)
    }

    @Test
    fun serverCodecRoundTripsAndDropsInvalidEntries() {
        val server = Gb2cServer("local", "https://gb3.example/", enabled = true)
        val decoded = Gb2cServerCodec.decode(Gb2cServerCodec.encode(listOf(server)))
        assertEquals(1, decoded.size)
        assertEquals("local", decoded.single().name)
        assertEquals("https://gb3.example", decoded.single().url)
        assertTrue(Gb2cServerCodec.decode("not json").isEmpty())
        assertTrue(
            Gb2cServerCodec.decode("""[{"name":"bad","url":"ftp://example"}]""").isEmpty(),
        )
    }

    @Test
    fun serverNamesDefaultToTheHost() {
        val server = Gb2cServer("", "https://gb3.example/path").normalized()
        assertEquals("gb3.example", server.name)
        assertNull(server.validationError())
    }

    @Test
    fun serverUrlsAcceptHostsAndRejectForeignSchemes() {
        assertEquals("http://localhost:16666", Gb3Api.normalizeServerUrl("localhost:16666"))
        assertEquals("https://gb3.example", Gb3Api.normalizeServerUrl("https://gb3.example/"))
        assertNull(Gb3Api.normalizeServerUrl("ftp://gb3.example"))
    }
}
