package org.zorel.gb3android.domain

/**
 * Native port of gc2's hand-written message scanner.
 *
 * The wire format is the old bouchot pseudo-HTML: a few formatting tags, raw
 * URLs, `[:totoz]` images, `nick<` bigornos and clock references. The scanner
 * produces a small token list. Tokens are cheap to cache and can be rendered by
 * Compose with links, colors and tap handling.
 */
object MessageParser {
    private val urlRegex = Regex("(?:(?:https?|ftp)://|data:[A-Za-z0-9./:;=]+,)[^< \\t\\r\\n\"]+")
    private val anchorRegex = Regex(
        """<a\s+[^>]*href=["']([^"']+)["'][^>]*>.*?</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val totozRegex = Regex("""\[:([^\]]+)]""")
    private val openTagRegex = Regex(
        "<(spoiler|b|strong|i|em|s|strike|del|u|tt|code)>",
        RegexOption.IGNORE_CASE,
    )
    private val closeTagRegex = Regex(
        "</(spoiler|b|strong|i|em|s|strike|del|u|tt|code)>",
        RegexOption.IGNORE_CASE,
    )
    private val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val entityRegex = Regex("&(?:lt|gt|amp|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);")

    private val isoNorloge = Regex(
        """(\d{4})-([0-1]\d)-([0-3]\d)[T# ]([0-2]\d):([0-5]\d):([0-5]\d)(@[a-z]+)?""",
    )
    private val dateNorloge = Regex(
        """([0-1]\d)/([0-3]\d)#([0-2]\d):([0-5]\d):([0-5]\d)(@[a-z]+)?""",
    )
    private val clockNorloge = Regex("""([0-2]\d):([0-5]\d):([0-5]\d)(@[a-z]+)?""")
    private val shortNorloge = Regex("""([0-2]\d):([0-5]\d)(@[a-z]+)?""")


    fun parse(message: String): ParsedMessage {
        val source = expandAnchors(clean(message))
        val tokens = ArrayList<MessageToken>()
        val norloges = ArrayList<MessageToken.Norloge>()
        val bigornos = LinkedHashSet<String>()
        val stack = ArrayList<TagEntry>()
        var spoilerCount = 0
        var text = StringBuilder()
        var format = InlineFormat()
        var activeSpoiler: Int? = null

        fun recomputeFormat() {
            var next = InlineFormat()
            var spoiler: Int? = null
            for (entry in stack) {
                when (entry.name) {
                    "b" -> next = next.copy(bold = true)
                    "i" -> next = next.copy(italic = true)
                    "u" -> next = next.copy(underline = true)
                    "s" -> next = next.copy(strike = true)
                    "tt", "code" -> next = next.copy(monospace = true)
                    "spoiler" -> if (entry.spoilerId != null) spoiler = entry.spoilerId
                }
            }
            format = next
            activeSpoiler = spoiler
        }

        fun flush() {
            if (text.isEmpty()) return
            tokens += MessageToken.Text(decodeEntities(text.toString()), format, activeSpoiler)
            text = StringBuilder()
        }

        fun pushText(value: String) {
            text.append(value)
        }

        var index = 0
        while (index < source.length) {
            val code = source[index]

            // Bare URLs are recognized wherever gc2 recognized them.
            if (code == 'h' || code == 'f' || code == 'd') {
                val match = urlRegex.matchAtExact(source, index)
                if (match != null) {
                    flush()
                    val (href, trailing) = splitTrailingPunctuation(match.value)
                    tokens += MessageToken.Link(href, urlLabel(href) + trailing)
                    index = match.range.last + 1
                    continue
                }
            }

            if (code == '[') {
                val match = totozRegex.matchAtExact(source, index)
                if (match != null) {
                    flush()
                    tokens += MessageToken.Totoz(match.groupValues[1])
                    index = match.range.last + 1
                    continue
                }
            }

            if (index == 0 || isMessageWhitespace(code)) {
                val match = matchBigorno(source, index)
                if (match != null) {
                    flush()
                    if (match.spaces.isNotEmpty()) pushText(match.spaces)
                    flush()
                    tokens += MessageToken.Bigorno(match.word)
                    bigornos += match.word
                    index = match.end
                    continue
                }
            }

            if (code in '0'..'9') {
                val match = matchNorloge(source, index)
                if (match != null) {
                    flush()
                    val token = MessageToken.Norloge(
                        index = norloges.size,
                        raw = match.raw,
                        time = match.time,
                        targetTribune = match.targetTribune,
                        title = match.title,
                    )
                    norloges += token
                    tokens += token
                    index = match.end
                    continue
                }
            }

            if (code == '<') {
                val br = brRegex.matchAtExact(source, index)
                if (br != null) {
                    flush()
                    pushText(" ")
                    flush()
                    index = br.range.last + 1
                    continue
                }
                val open = openTagRegex.matchAtExact(source, index)
                if (open != null) {
                    flush()
                    val name = normalizeTag(open.groupValues[1])
                    val spoilerId = if (name == "spoiler") spoilerCount++ else null
                    stack += TagEntry(name, spoilerId)
                    recomputeFormat()
                    index = open.range.last + 1
                    continue
                }
                val close = closeTagRegex.matchAtExact(source, index)
                if (close != null) {
                    flush()
                    val name = normalizeTag(close.groupValues[1])
                    val position = stack.indexOfLast { it.name == name }
                    if (position >= 0) {
                        while (stack.size > position) stack.removeAt(stack.lastIndex)
                    }
                    recomputeFormat()
                    index = close.range.last + 1
                    continue
                }
                pushText("<")
                index++
                continue
            }

            if (code == '&') {
                val match = entityRegex.matchAtExact(source, index)
                if (match != null) {
                    pushText(match.value)
                    index = match.range.last + 1
                    continue
                }
            }

            pushText(code.toString())
            index++
        }
        flush()

        return ParsedMessage(
            source = source,
            tokens = tokens,
            norloges = norloges,
            bigornos = bigornos,
            spoilerCount = spoilerCount,
        )
    }

    /**
     * Rewrite the `@tribune` suffix of norloges when the active tribune
     * changes, exactly like gc2 does while typing.
     */
    fun rewriteBouchotSuffix(message: String, oldTribune: String, newTribune: String): String {
        val oldSuffix = if (oldTribune.isBlank()) "" else "@$oldTribune"
        val result = StringBuilder(message.length + 8)
        var index = 0
        while (index < message.length) {
            val code = message[index]
            if (code == 'h' || code == 'f' || code == 'd') {
                val match = urlRegex.matchAtExact(message, index)
                if (match != null) {
                    result.append(match.value)
                    index = match.range.last + 1
                    continue
                }
            }
            if (code == '[') {
                val match = totozRegex.matchAtExact(message, index)
                if (match != null) {
                    result.append(match.value)
                    index = match.range.last + 1
                    continue
                }
            }
            if (code in '0'..'9') {
                val match = matchNorloge(message, index)
                if (match != null) {
                    val suffix = when {
                        match.targetTribune == null -> oldSuffix
                        match.targetTribune == newTribune -> ""
                        else -> "@${match.targetTribune}"
                    }
                    val withoutSuffix = match.raw.removeSuffix(
                        match.targetTribune?.let { "@$it" } ?: "",
                    )
                    result.append(withoutSuffix).append(suffix)
                    index = match.end
                    continue
                }
            }
            result.append(code)
            index++
        }
        return result.toString()
    }

    fun resolveTarget(
        ref: MessageToken.Norloge,
        fromTribune: String,
    ): String = ref.targetTribune ?: fromTribune

    fun refKey(ref: MessageToken.Norloge, fromTribune: String): String =
        "${resolveTarget(ref, fromTribune)}:${ref.clockKey}"

    private fun expandAnchors(message: String): String {
        return anchorRegex.replace(message) { match ->
            val href = match.groupValues[1]
            if (urlRegex.matchAt(href, 0)?.range?.first == 0) href else match.value
        }
    }

    private fun clean(value: String): String {
        val builder = StringBuilder(value.length)
        for (character in value) {
            when {
                character == '\r' -> builder.append(' ')
                character == '\n' || character == '\t' -> builder.append(character)
                character.code < 32 || character.code == 127 -> Unit
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }

    private fun normalizeTag(name: String): String = when (name.lowercase()) {
        "strong" -> "b"
        "em" -> "i"
        "strike", "del" -> "s"
        else -> name.lowercase()
    }

    private fun isMessageWhitespace(character: Char): Boolean =
        character == ' ' || character == '\t' || character == '\n' || character == '\r'

    private data class BigornoMatch(val end: Int, val spaces: String, val word: String)

    private fun matchBigorno(message: String, index: Int): BigornoMatch? {
        val length = message.length
        val atStart = index == 0
        var cursor = if (atStart) index else index + 1
        if (cursor < length && isMessageWhitespace(message[cursor])) cursor++

        var wordEnd = cursor
        while (wordEnd < length && isBigornoChar(message[wordEnd])) wordEnd++
        if (wordEnd == cursor) return null

        for (end in wordEnd downTo cursor + 1) {
            val after = when {
                end < length && message[end] == '<' -> end + 1
                message.startsWith("&lt;", end) -> end + 4
                else -> continue
            }
            if (after >= length || isBigornoFollow(message[after])) {
                return BigornoMatch(
                    end = after,
                    spaces = if (atStart) "" else message[index].toString(),
                    word = message.substring(cursor, end),
                )
            }
        }
        return null
    }

    private fun isBigornoChar(character: Char): Boolean =
        character in 'a'..'z' || character in 'A'..'Z' ||
            character in '0'..'9' || character == '-' || character == '_'

    private fun isBigornoFollow(character: Char): Boolean =
        isMessageWhitespace(character) || character == '<' || character == ';' ||
            character == ':' || character == '[' || character == ','

    private data class NorlogeMatch(
        val raw: String,
        val time: String,
        val targetTribune: String?,
        val title: String,
        val end: Int,
    )

    private fun matchNorloge(message: String, index: Int): NorlogeMatch? {
        isoNorloge.matchAtExact(message, index)?.let { match ->
            val time = "${match.groupValues[4]}:${match.groupValues[5]}:${match.groupValues[6]}"
            return NorlogeMatch(
                raw = match.value,
                time = time,
                targetTribune = match.groupValues[7].removePrefix("@").ifBlank { null },
                title = "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]} $time",
                end = match.range.last + 1,
            )
        }
        dateNorloge.matchAtExact(message, index)?.let { match ->
            val time = "${match.groupValues[3]}:${match.groupValues[4]}:${match.groupValues[5]}"
            return NorlogeMatch(
                raw = match.value,
                time = time,
                targetTribune = match.groupValues[6].removePrefix("@").ifBlank { null },
                title = "${match.groupValues[1]}-${match.groupValues[2]} $time",
                end = match.range.last + 1,
            )
        }
        clockNorloge.matchAtExact(message, index)?.let { match ->
            val time = "${match.groupValues[1]}:${match.groupValues[2]}:${match.groupValues[3]}"
            return NorlogeMatch(
                raw = match.value,
                time = time,
                targetTribune = match.groupValues[4].removePrefix("@").ifBlank { null },
                title = time,
                end = match.range.last + 1,
            )
        }
        shortNorloge.matchAtExact(message, index)?.let { match ->
            val time = "${match.groupValues[1]}:${match.groupValues[2]}"
            return NorlogeMatch(
                raw = match.value,
                time = time,
                targetTribune = match.groupValues[3].removePrefix("@").ifBlank { null },
                title = time,
                end = match.range.last + 1,
            )
        }
        return null
    }

    private fun splitTrailingPunctuation(value: String): Pair<String, String> {
        var end = value.length
        while (end > 0) {
            val character = value[end - 1]
            val shouldTrim = when (character) {
                '.', ',', '!', '?', ';', ':' -> true
                ')' -> value.substring(0, end).count { it == ')' } > value.substring(0, end).count { it == '(' }
                ']' -> value.substring(0, end).count { it == ']' } > value.substring(0, end).count { it == '[' }
                '}' -> value.substring(0, end).count { it == '}' } > value.substring(0, end).count { it == '{' }
                else -> false
            }
            if (!shouldTrim) break
            end--
        }
        return value.substring(0, end) to value.substring(end)
    }

    private fun urlLabel(href: String): String {
        return runCatching {
            val uri = java.net.URI(href)
            val host = uri.host
            if (host.isNullOrBlank()) {
                href.substringBefore(':')
            } else {
                val path = uri.rawPath.orEmpty()
                val label = if (path.isBlank() || path == "/") host else host + path
                if (label.length > 44) label.take(43) + "…" else label
            }
        }.getOrDefault(href)
    }

    private fun decodeEntities(value: String): String {
        if (!value.contains('&')) return value
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '&') {
                val match = entityRegex.matchAtExact(value, index)
                if (match != null) {
                    builder.append(
                        when (val entity = match.value) {
                            "&lt;" -> "<"
                            "&gt;" -> ">"
                            "&amp;" -> "&"
                            "&quot;" -> "\""
                            "&apos;" -> "'"
                            else -> decodeNumericEntity(entity)
                        },
                    )
                    index = match.range.last + 1
                    continue
                }
            }
            builder.append(value[index])
            index++
        }
        return builder.toString()
    }

    private fun decodeNumericEntity(entity: String): String {
        return runCatching {
            val number = if (entity.startsWith("&#x", ignoreCase = true)) {
                entity.substring(3, entity.length - 1).toInt(16)
            } else {
                entity.substring(2, entity.length - 1).toInt()
            }
            String(Character.toChars(number))
        }.getOrDefault(entity)
    }

    private fun Regex.matchAtExact(input: String, index: Int): MatchResult? {
        val match = find(input, index) ?: return null
        return if (match.range.first == index) match else null
    }

    private data class TagEntry(val name: String, val spoilerId: Int?)
}

data class InlineFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val monospace: Boolean = false,
)

sealed interface MessageToken {
    data class Text(
        val text: String,
        val format: InlineFormat,
        val spoiler: Int?,
    ) : MessageToken

    data class Link(val href: String, val label: String) : MessageToken

    data class Norloge(
        val index: Int,
        val raw: String,
        val time: String,
        val targetTribune: String?,
        val title: String,
    ) : MessageToken {
        val clockKey: String get() = time.replace(":", "")
        val displayText: String get() = time + (targetTribune?.let { "@$it" } ?: "")
    }

    data class Totoz(val name: String) : MessageToken

    data class Bigorno(val word: String) : MessageToken
}

data class ParsedMessage(
    val source: String,
    val tokens: List<MessageToken>,
    val norloges: List<MessageToken.Norloge>,
    val bigornos: Set<String>,
    val spoilerCount: Int,
)
