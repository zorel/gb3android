package org.zorel.gb3android.domain

import org.zorel.gb3android.data.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {
    private fun post(
        id: Long,
        time: String,
        tribune: String = "trib",
        info: String = "dave",
        login: String = "",
        message: String,
    ) = Post(id, time, info, login, message, tribune)

    @Test
    fun hidesDirectlyBlockedPostsAndTheirDescendants() {
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", info = "alice", message = "root"),
            post(2, "2026-10-26T10:00:05", message = "reply 10:00:00"),
            post(3, "2026-10-26T10:00:10", message = "nested 10:00:05"),
            post(4, "2026-10-26T10:00:15", message = "unrelated"),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = setOf("alice"),
            myIdentities = setOf("dave"),
        )
        assertTrue(analysis.posts.getValue("trib:1").hidden)
        assertTrue(analysis.posts.getValue("trib:2").hidden)
        assertTrue(analysis.posts.getValue("trib:3").hidden)
        assertFalse(analysis.posts.getValue("trib:4").hidden)
    }

    @Test
    fun latestPostWinsForSharedClock() {
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", message = "first"),
            post(2, "2026-10-26T10:00:00", message = "second"),
            post(3, "2026-10-26T10:00:05", message = "reply 10:00:00"),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("dave"),
        )
        val targets = analysis.posts.getValue("trib:3").refs.map { it.targetKey }
        assertEquals(listOf("trib:2"), targets)
        assertTrue(analysis.posts.getValue("trib:2").children.contains("trib:3"))
    }

    @Test
    fun marksRepliesToMyPosts() {
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", info = "me", message = "mine"),
            post(2, "2026-10-26T10:00:05", info = "dave", message = "reply 10:00:00"),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("me"),
        )
        assertTrue(analysis.posts.getValue("trib:1").isMine)
        assertTrue(analysis.posts.getValue("trib:2").isReply)
    }

    @Test
    fun detectsBigorno() {
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", message = "dave< hello"),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("dave"),
        )
        assertTrue(analysis.posts.getValue("trib:1").isBigorno)
    }

    @Test
    fun resolvesCrossTribuneReferences() {
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", tribune = "other", message = "elsewhere"),
            post(2, "2026-10-26T10:00:05", tribune = "trib", message = "reply 10:00:00@other"),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("dave"),
        )
        assertEquals("other:1", analysis.posts.getValue("trib:2").refs.single().targetKey)
    }

    @Test
    fun resolvesReferencesWithCompactBackendTime() {
        val posts = listOf(
            post(1, "20261026100000", message = "root"),
            post(2, "20261026100005", message = "reply 10:00:00"),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("dave"),
        )
        assertEquals("trib:1", analysis.posts.getValue("trib:2").refs.single().targetKey)
        assertEquals("┗", analysis.posts.getValue("trib:2").marker)
    }

    @Test
    fun exposesRootAndMarkers() {
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", message = "root"),
            post(2, "2026-10-26T10:00:05", message = "reply 10:00:00"),
            post(3, "2026-10-26T10:00:10", info = "alice", message = "unrelated"),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("dave"),
        )
        val root = analysis.posts.getValue("trib:1")
        val reply = analysis.posts.getValue("trib:2")
        assertEquals("┏", root.marker)
        assertEquals("┗", reply.marker)
        assertEquals("trib:1", reply.rootKey)
        assertTrue(Conversation.colorIndex("trib:1") in 0 until Conversation.CONVERSATION_COLORS)
    }
}
