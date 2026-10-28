package org.zorel.gb3android.data

/**
 * Tolerant parser for the five-column TSV backend served by bouchot boards.
 *
 * A remote board is untrusted input: it can return truncated rows, HTML error
 * pages, absurdly long lines or invalid identifiers. Every malformed row is
 * skipped instead of producing a broken post, extra columns are ignored like
 * gb2c does, and oversized fields are bounded so one bad response cannot
 * exhaust the app's memory.
 */
object BoardTsv {
    const val MAX_MESSAGE_LENGTH = 65_536
    const val MAX_POSTS = 2_000

    private const val MAX_TIME_LENGTH = 40
    private const val MAX_INFO_LENGTH = 2_048
    private const val MAX_LOGIN_LENGTH = 512
    private const val MAX_LINE_LENGTH = 512 * 1024
    private const val MAX_LINES = 20_000

    fun parse(body: String, tribune: String, maxPosts: Int = MAX_POSTS): List<Post> {
        if (tribune.isBlank()) return emptyList()
        val safeMax = maxPosts.coerceAtLeast(1)
        val postsById = LinkedHashMap<Long, Post>()
        var start = 0
        var lines = 0
        while (start <= body.length && lines < MAX_LINES) {
            val newline = body.indexOf('\n', start)
            val end = if (newline >= 0) newline else body.length
            val rawLine = body.substring(start, end)
            val line = if (rawLine.endsWith('\r')) rawLine.dropLast(1) else rawLine
            start = if (newline >= 0) newline + 1 else body.length + 1
            lines++
            if (line.isEmpty() || line.length > MAX_LINE_LENGTH) continue
            val post = parseLine(line, tribune) ?: continue
            // If a board repeats an id, the last occurrence wins.
            postsById[post.id] = post
        }
        return postsById.values.sortedBy { it.id }.takeLast(safeMax)
    }

    private fun parseLine(line: String, tribune: String): Post? {
        val record = splitTSVLine(line) ?: return null
        val rawId = record[0].trim().removePrefix("\uFEFF")
        val id = rawId.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val time = sanitize(record[1], MAX_TIME_LENGTH).trim()
        if (time.isEmpty()) return null
        val info = sanitize(record[2], MAX_INFO_LENGTH).trim()
        val login = sanitize(record[3], MAX_LOGIN_LENGTH).trim()
        val message = sanitize(record[4], MAX_MESSAGE_LENGTH)
        if (message.isEmpty() && login.isEmpty() && info.isEmpty()) return null
        return Post(
            id = id,
            time = time,
            info = info,
            login = login,
            message = message,
            tribune = tribune,
        )
    }

    /** Split the first five fields, sharing gb2c's "drop extra columns" rule. */
    private fun splitTSVLine(line: String): Array<String>? {
        val record = arrayOfNulls<String>(5)
        var offset = 0
        for (index in 0 until 4) {
            val tab = line.indexOf('\t', offset)
            if (tab < 0) return null
            record[index] = line.substring(offset, tab)
            offset = tab + 1
        }
        val nextTab = line.indexOf('\t', offset)
        record[4] = if (nextTab >= 0) line.substring(offset, nextTab) else line.substring(offset)
        @Suppress("UNCHECKED_CAST")
        return record as Array<String>
    }

    /**
     * Remove control characters that would corrupt the post list, keep normal
     * whitespace, and truncate to [maxCodePoints] without splitting surrogates.
     */
    private fun sanitize(value: String, maxCodePoints: Int): String {
        val builder = StringBuilder(minOf(value.length, maxCodePoints))
        var count = 0
        var index = 0
        while (index < value.length && count < maxCodePoints) {
            val codePoint = value.codePointAt(index)
            index += Character.charCount(codePoint)
            count++
            when {
                codePoint == '\t'.code || codePoint == '\n'.code || codePoint == '\r'.code -> {
                    builder.append(' ')
                }
                Character.isISOControl(codePoint) -> Unit
                else -> builder.appendCodePoint(codePoint)
            }
        }
        return builder.toString()
    }
}
