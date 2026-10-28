package org.zorel.gb3android.domain

import org.zorel.gb3android.data.Post

/**
 * Conversation index shared by the post list and the conversation filter.
 *
 * gb3 references another post with its clock (`12:34:56`, optionally suffixed
 * by `@tribune`). When several posts share the same second the latest one wins,
 * as in gb3tui and gc2.
 */
object Conversation {
    data class ResolvedRef(
        val index: Int,
        val ref: MessageToken.Norloge,
        val targetKey: String?,
    )

    data class PostConversation(
        val hidden: Boolean,
        val isMine: Boolean,
        val isBigorno: Boolean,
        val isReply: Boolean,
        val parents: Set<String>,
        val children: Set<String>,
        val rootKey: String?,
        val refs: List<ResolvedRef>,
    )

    data class Analysis(
        val posts: Map<String, PostConversation>,
        val parsed: Map<String, ParsedMessage>,
        val maxIdByTribune: Map<String, Long>,
    )

    fun analyze(
        posts: List<Post>,
        parsedByKey: Map<String, ParsedMessage>,
        blockedIdentities: Set<String>,
        myIdentities: Set<String>,
    ): Analysis {
        val sorted = posts.sortedBy { it.id }
        val parsed = sorted.associate { post ->
            post.key to (parsedByKey[post.key] ?: MessageParser.parse(post.message))
        }
        val byClock = HashMap<String, HashMap<String, Post>>()
        val byMinute = HashMap<String, HashMap<String, Post>>()
        for (post in sorted) {
            if (post.clockKey.length == 6) {
                byClock.getOrPut(post.tribune) { HashMap() }[post.clockKey] = post
                byMinute.getOrPut(post.tribune) { HashMap() }[post.clockKey.take(4)] = post
            }
        }

        val resolvedRefs = HashMap<String, List<ResolvedRef>>(sorted.size)
        val childrenByTarget = HashMap<String, MutableSet<String>>()
        val parentsByChild = HashMap<String, MutableSet<String>>()
        for (post in sorted) {
            val refs = parsed.getValue(post.key).norloges.mapIndexed { index, ref ->
                val targetTribune = ref.targetTribune ?: post.tribune
                val target = if (ref.clockKey.length == 6) {
                    byClock[targetTribune]?.get(ref.clockKey)
                } else {
                    byMinute[targetTribune]?.get(ref.clockKey)
                }
                ResolvedRef(index, ref, target?.key)
            }
            resolvedRefs[post.key] = refs

            // Only full HH:MM:SS references create the reply graph, matching
            // gc2's conversation filter and gb3tui.
            for (resolved in refs) {
                val clockKey = resolved.ref.clockKey
                if (clockKey.length != 6) continue
                val targetKey = resolved.targetKey ?: continue
                if (targetKey == post.key) continue
                childrenByTarget.getOrPut(targetKey) { LinkedHashSet() } += post.key
                parentsByChild.getOrPut(post.key) { LinkedHashSet() } += targetKey
            }
        }

        val blocked = blockedIdentities.filter { it.isNotBlank() }.toSet()
        val mine = myIdentities.filter { it.isNotBlank() }.toSet()
        val myIdentitySet = mine + "moules"
        val isMineByKey = sorted.associate { post ->
            post.key to (post.info in mine || (post.tribune == "dlfp" && post.login in mine))
        }

        val hidden = HashSet<String>()
        val queue = ArrayDeque<String>()
        for (post in sorted) {
            if (post.login in blocked || post.info in blocked) {
                hidden += post.key
                queue.addLast(post.key)
            }
        }
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            for (child in childrenByTarget[parent].orEmpty()) {
                if (hidden.add(child)) queue.addLast(child)
            }
        }

        val rootCache = HashMap<String, String?>()
        fun rootOf(key: String): String? {
            if (!rootCache.containsKey(key)) {
                val seen = HashSet<String>()
                var current = key
                while (true) {
                    val parent = parentsByChild[current]?.firstOrNull()
                    if (parent == null || !seen.add(current)) break
                    current = parent
                }
                rootCache[key] = current
            }
            return rootCache[key]
        }

        val entries = sorted.associate { post ->
            val key = post.key
            val refs = resolvedRefs[key].orEmpty()
            val parents = parentsByChild[key].orEmpty()
            val children = childrenByTarget[key].orEmpty()
            val isReply = refs.any { ref -> ref.targetKey?.let { isMineByKey[it] } == true }
            val isBigorno = parsed.getValue(key).bigornos.any { it in myIdentitySet }
            val hasEdges = parents.isNotEmpty() || children.isNotEmpty()
            key to PostConversation(
                hidden = key in hidden,
                isMine = isMineByKey[key] == true,
                isBigorno = isBigorno,
                isReply = isReply,
                parents = parents,
                children = children,
                rootKey = if (hasEdges) rootOf(key) else null,
                refs = refs,
            )
        }

        val maxIdByTribune = sorted.groupBy { it.tribune }.mapValues { (_, posts) ->
            posts.maxOf { it.id }
        }
        return Analysis(entries, parsed, maxIdByTribune)
    }

    fun colorIndex(rootKey: String): Int {
        var hash = 0x811C9DC5.toInt() // FNV-1a 32-bit offset basis
        for (byte in rootKey.toByteArray()) {
            hash = hash xor (byte.toInt() and 0xff)
            hash *= 0x01000193
        }
        return (hash and 0x7fffffff) % CONVERSATION_COLORS
    }

    const val CONVERSATION_COLORS = 8
}
