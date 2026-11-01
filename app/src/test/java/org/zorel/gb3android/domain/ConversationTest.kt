package org.zorel.gb3android.domain

import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.SourceId
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
        source: SourceId = SourceId.Direct(tribune),
    ) = Post(id, time, info, login, message, tribune, source)

    private fun key(
        id: Long,
        tribune: String = "trib",
        source: SourceId = SourceId.Direct(tribune),
    ) = Post.keyOf(source, tribune, id)

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
        assertTrue(analysis.posts.getValue(key(1)).hidden)
        assertTrue(analysis.posts.getValue(key(2)).hidden)
        assertTrue(analysis.posts.getValue(key(3)).hidden)
        assertFalse(analysis.posts.getValue(key(4)).hidden)
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
        val targets = analysis.posts.getValue(key(3)).refs.map { it.targetKey }
        assertEquals(listOf(key(2)), targets)
        assertTrue(analysis.posts.getValue(key(2)).children.contains(key(3)))
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
        assertTrue(analysis.posts.getValue(key(1)).isMine)
        assertTrue(analysis.posts.getValue(key(2)).isReply)
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
        assertTrue(analysis.posts.getValue(key(1)).isBigorno)
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
        assertEquals(key(1, "other"), analysis.posts.getValue(key(2)).refs.single().targetKey)
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
        assertEquals(key(1), analysis.posts.getValue(key(2)).refs.single().targetKey)
    }

    @Test
    fun exposesRootAndColor() {
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
        val reply = analysis.posts.getValue(key(2))
        assertEquals(key(1), reply.rootKey)
        assertTrue(Conversation.colorIndex(key(1)) in 0 until Conversation.CONVERSATION_COLORS)
    }

    @Test
    fun keepsTheSameTribuneNameSeparatePerSource() {
        val gb2c = SourceId.Gb2c("https://gb2c.example")
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", message = "direct root"),
            post(2, "2026-10-26T10:00:00", message = "gb2c root", source = gb2c),
            post(3, "2026-10-26T10:00:05", message = "reply 10:00:00", source = gb2c),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("dave"),
        )
        val reply = analysis.posts.getValue(key(3, source = gb2c))
        assertEquals(key(2, source = gb2c), reply.refs.single().targetKey)
        assertFalse(analysis.posts.getValue(key(1)).children.contains(key(3, source = gb2c)))
    }

    @Test
    fun crossTribuneReferencesPreferTheSameSource() {
        val gb2c = SourceId.Gb2c("https://gb2c.example")
        val posts = listOf(
            post(1, "2026-10-26T10:00:00", tribune = "other", message = "direct other"),
            post(
                2,
                "2026-10-26T10:00:00",
                tribune = "other",
                message = "gb2c other",
                source = gb2c,
            ),
            post(
                3,
                "2026-10-26T10:00:05",
                message = "reply 10:00:00@other",
                source = gb2c,
            ),
        )
        val analysis = Conversation.analyze(
            posts = posts,
            parsedByKey = emptyMap(),
            blockedIdentities = emptySet(),
            myIdentities = setOf("dave"),
        )
        assertEquals(
            key(2, "other", gb2c),
            analysis.posts.getValue(key(3, source = gb2c)).refs.single().targetKey,
        )
    }
}
