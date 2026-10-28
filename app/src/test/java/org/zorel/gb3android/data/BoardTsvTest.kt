package org.zorel.gb3android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTsvTest {
    private val time = "20260926120000"

    @Test
    fun parsesValidRowsAndSkipsMalformedOnes() {
        val backend = "1\t$time\tinfo\t\tmessage one\n" +
            "2\t$time\tinfo\tlogin\tmessage two\n" +
            "not-a-number\t$time\tinfo\t\tignored\n" +
            "0\t$time\tinfo\t\tignored\n" +
            "-3\t$time\tinfo\t\tignored\n" +
            "3\t$time\tinfo\n" +
            "4\t$time\t\t\t\n" +
            "5\t$time\tinfo\t\tmessage five\n"

        val posts = BoardTsv.parse(backend, "moules")

        assertEquals(listOf(1L, 2L, 5L), posts.map { it.id })
        assertEquals("message one", posts[0].message)
        assertEquals("login", posts[1].login)
        assertEquals("moules", posts[0].tribune)
        assertEquals("20260926120000", posts[0].time)
    }

    @Test
    fun handlesCrLfAndDropsExtraColumnsLikeGb2c() {
        val backend = "7\t$time\tinfo\tlogin\tmessage\twith\ttabs\r\n" +
            "8\t$time\tinfo\t\tsecond\r\n"

        val posts = BoardTsv.parse(backend, "trib")

        assertEquals(listOf(7L, 8L), posts.map { it.id })
        assertEquals("message", posts[0].message)
        assertEquals("second", posts[1].message)
    }

    @Test
    fun rejectsHtmlAndEmptyBodies() {
        assertTrue(BoardTsv.parse("<html><body>bad gateway</body></html>", "trib").isEmpty())
        assertTrue(BoardTsv.parse("", "trib").isEmpty())
        assertTrue(BoardTsv.parse("1\t$time\tinfo\t\tmessage", " ").isEmpty())
    }

    @Test
    fun removesControlCharactersAndBoundsMessageLength() {
        val huge = "x".repeat(BoardTsv.MAX_MESSAGE_LENGTH + 25)
        val backend = "9\t$time\tinfo\tlogin\tbad\u0000message\n" +
            "10\t$time\tinfo\t\t$huge\n"

        val posts = BoardTsv.parse(backend, "trib")

        assertEquals("badmessage", posts[0].message)
        assertEquals(BoardTsv.MAX_MESSAGE_LENGTH, posts[1].message.length)
    }

    @Test
    fun keepsTheLatestIdsWhenCapped() {
        val backend = (1..5).joinToString("\n") { "$it\t$time\tinfo\t\tmessage $it" }

        val posts = BoardTsv.parse(backend, "trib", maxPosts = 2)

        assertEquals(listOf(4L, 5L), posts.map { it.id })
    }

    @Test
    fun validatesBoardUrlsAndNames() {
        val board = Board(
            name = "custom",
            backendUrl = "https://example.com/backend.tsv",
            postUrl = "https://example.com/post",
        )
        assertNull(board.validationError())
        assertNotNull(board.copy(name = "bad name").validationError())
        assertNotNull(board.copy(name = "bad,name").validationError())
        assertNotNull(board.copy(backendUrl = "ftp://example.com/backend").validationError())
        assertNotNull(board.copy(postUrl = "nope").validationError())
        assertNotNull(board.copy(postField = " ").validationError())
    }

    @Test
    fun boardCodecRoundTripsCustomBoardsAndRejectsInvalidEntries() {
        val custom = Board(
            name = "custom",
            backendUrl = "https://example.com/backend.tsv",
            postUrl = "https://example.com/post",
            postField = "content",
            infoField = "info",
            auth = BoardAuth.OAuth2,
        )

        val encoded = BoardCodec.encode(listOf(custom))
        val decoded = BoardCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals(custom.normalized(), decoded.single())
        assertTrue(BoardCodec.decode("not json").isEmpty())
        assertTrue(BoardCodec.decode("""[{"name":"","backendUrl":"x","postUrl":"y"}]""").isEmpty())
    }
}
