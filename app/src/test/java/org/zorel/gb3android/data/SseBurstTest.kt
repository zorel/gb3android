package org.zorel.gb3android.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseBurstTest {
    @Test
    fun keepsEveryPostDuringInitialHistoryBurst() = runBlocking {
        val total = 500
        val body = buildString {
            for (index in 1..total) {
                append("""data: {"id":$index,"time":"20261026120000","info":"dave","login":"","message":"post $index","tribune":"trib"}""")
                append("\n\n")
            }
        }
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            val received = ArrayList<Long>(total)
            withTimeoutOrNull(15_000) {
                Gb3Api().poll(base, listOf("trib"))
                    .filterIsInstance<PollEvent.NewPost>()
                    .take(total)
                    .collect { event ->
                        received += event.post.id
                        // Simulate a consumer that is slower than the network.
                        delay(1)
                    }
            }
            assertEquals(total, received.size)
            assertEquals((1L..total).toList(), received)
        } finally {
            server.shutdown()
        }
    }
}
