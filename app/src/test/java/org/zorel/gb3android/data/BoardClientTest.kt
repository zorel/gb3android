package org.zorel.gb3android.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardClientTest {
    @Test
    fun fetchesAndFiltersMalformedBackendRows() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                "1\t20261027120000\tinfo\talice\thello\n" +
                    "broken row\n" +
                    "2\t20261027120001\tinfo\tbob\tworld\n",
            ),
        )
        server.start()
        try {
            val board = Board(
                name = "custom",
                backendUrl = server.url("/backend.tsv").toString(),
                postUrl = server.url("/post").toString(),
            )
            val posts = BoardClient().fetch(board)
            assertEquals(listOf(1L, 2L), posts.map { it.id })
            assertEquals("custom", posts.first().tribune)
            assertEquals("alice", posts.first().login)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun postsWithConfiguredFieldAndIdentityInUserAgent() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val board = Board(
                name = "custom",
                backendUrl = server.url("/backend.tsv").toString(),
                postUrl = server.url("/post").toString(),
                postField = "content",
            )
            BoardClient().post(board, info = "dave", message = "hello")
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/post", request.path)
            assertEquals("dave", request.getHeader("User-Agent"))
            assertEquals("content=hello", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun postsInfoFieldAndBearerTokenForOauthBoard() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val board = Board(
                name = "dlfp",
                backendUrl = server.url("/backend.tsv").toString(),
                postUrl = server.url("/board").toString(),
                infoField = "info",
                auth = BoardAuth.OAuth2,
            )
            BoardClient().post(board, info = "dave", message = "hello", accessToken = "tok")
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertTrue(body.contains("message=hello"))
            assertTrue(body.contains("info=dave"))
            assertEquals("Bearer tok", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
