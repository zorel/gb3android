package org.zorel.gb3android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    private fun post(info: String = "", login: String = "") =
        Post(1, "20261027120000", info, login, "message", "trib")

    @Test
    fun keepsShortIdentities() {
        assertEquals("zorel", post(login = "zorel").displayIdentity)
        assertEquals(
            "Firefox 141 · Linux",
            post(info = "Mozilla/5.0 (X11; Linux x86_64) Firefox/141.0").displayIdentity,
        )
    }

    @Test
    fun trimsLongLogins() {
        val identity = post(login = "a-login-that-is-far-too-long-for-a-post-row").displayIdentity
        assertEquals(24, identity.length)
        assertEquals("a-login-that-is-far-too…", identity)
    }

    @Test
    fun trimsUnknownUserAgents() {
        val identity = post(info = "Mozilla/5.0 (X11; Linux x86_64) Custom/1.0").displayIdentity
        assertEquals("Mozilla/5.0 (X11; Linux…", identity)
    }

    @Test
    fun doesNotSplitSurrogatePairs() {
        val identity = trimIdentity("😀".repeat(30), maxLength = 5)
        assertEquals("😀😀😀😀…", identity)
    }
}
