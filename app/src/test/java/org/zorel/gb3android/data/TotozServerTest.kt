package org.zorel.gb3android.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotozServerTest {
    @Test
    fun searchesDirectTotozServerAndBuildsImageUrls() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """<?xml version="1.0" encoding="UTF-8"?>
                    <totozes>
                      <totoz><name>plop</name></totoz>
                      <totoz><name>moulin</name></totoz>
                    </totozes>""",
            ),
        )
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            val results = Gb3Api().searchTotozDirect(base, "plop")

            assertEquals(listOf("plop", "moulin"), results.map { it.name })
            assertTrue(results[0].image.startsWith("$base/img/"))
            assertTrue(results[1].image.startsWith("$base/img/"))
            assertEquals("/search.xml?terms=plop", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun buildsUrlsFromACustomBasePath() {
        assertEquals(
            "https://totoz.example/base/search.xml?terms=hello%20world",
            TotozServer.searchUrl("https://totoz.example/base/", "hello world").toString(),
        )
        assertEquals(
            "https://totoz.example/base/img/plop",
            TotozServer.imageUrl("https://totoz.example/base", "plop"),
        )
        assertEquals(
            "https://totoz.example/search.xml?terms=plop",
            TotozServer.searchUrl("https://totoz.example/search.xml", "plop").toString(),
        )
    }

    @Test
    fun rejectsInvalidTotozServerUrls() {
        assertEquals(null, TotozServer.searchUrl("", "plop"))
        assertEquals(null, TotozServer.searchUrl("not a url", "plop"))
        assertEquals(null, TotozServer.imageUrl("ftp://example.com", "plop"))
    }
}
