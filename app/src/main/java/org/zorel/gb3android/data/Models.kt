package org.zorel.gb3android.data

/** A post as published by gb2c on the poll stream. */
data class Post(
    val id: Long,
    val time: String,
    val info: String,
    val login: String,
    val message: String,
    val tribune: String,
) {
    val key: String get() = keyOf(tribune, id)

    /** gb2c uses the compact `yyyyMMddHHmmss` format, but be tolerant of ISO-ish input. */
    private val timeDigits: String
        get() = time.filter { it.isDigit() }

    /** HH:MM:SS, the form used by clock references (norloges). */
    val clock: String
        get() = if (timeDigits.length >= 14) {
            "${timeDigits.substring(8, 10)}:${timeDigits.substring(10, 12)}:${timeDigits.substring(12, 14)}"
        } else {
            time
        }

    val clockKey: String
        get() = if (timeDigits.length >= 14) timeDigits.substring(8, 14) else ""

    val fullDate: String
        get() = if (timeDigits.length >= 8) {
            "${timeDigits.substring(0, 4)}-${timeDigits.substring(4, 6)}-${timeDigits.substring(6, 8)}"
        } else {
            ""
        }

    val displayIdentity: String
        get() = trimIdentity(login.ifBlank { shortBrowserUserAgent(info) })

    companion object {
        fun keyOf(tribune: String, id: Long): String = "$tribune:$id"
    }
}

data class Totoz(val name: String, val image: String)

data class EmojiItem(val name: String, val characters: String)

data class LinuxfrSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val login: String,
)

enum class BlockedDisplayMode { Hide, Gray }

enum class Screen { Main, Settings, LinuxfrOauth }

data class TribuneBadge(
    val hasBigorno: Boolean = false,
    val hasReply: Boolean = false,
) {
    val any: Boolean get() = hasBigorno || hasReply
}

data class TextDraft(
    val text: String = "",
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
) {
    val hasSelection: Boolean get() = selectionStart != selectionEnd

    fun coerce(): TextDraft {
        val length = text.length
        val start = selectionStart.coerceIn(0, length)
        val end = selectionEnd.coerceIn(0, length)
        return copy(
            selectionStart = minOf(start, end),
            selectionEnd = maxOf(start, end),
        )
    }

    fun selectedText(): String = text.substring(selectionStart, selectionEnd)
}

fun String.csvSet(): Set<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

fun Set<String>.toCsv(): String = joinToString(",")

fun shortBrowserUserAgent(userAgent: String): String {
    val versions = listOf(
        "Edge" to Regex("(?:Edg|EdgiOS|EdgA)/([0-9]+)"),
        "Opera" to Regex("(?:OPR|Opera)/([0-9]+)"),
        "Firefox" to Regex("(?:Firefox|FxiOS)/([0-9]+)"),
        "Chrome" to Regex("(?:Chrome|CriOS)/([0-9]+)"),
        "Safari" to Regex("Version/([0-9]+)(?:\\.[0-9]+)*.*Safari/"),
    )
    for ((name, expression) in versions) {
        val match = expression.find(userAgent) ?: continue
        val version = match.groupValues.getOrNull(1) ?: continue
        val platform = browserPlatform(userAgent)
        return if (platform.isEmpty()) "$name $version" else "$name $version · $platform"
    }
    return userAgent.ifBlank { "coward" }
}

/** Long logins and raw user agents would otherwise take over the post row. */
fun trimIdentity(identity: String, maxLength: Int = MAX_IDENTITY_LENGTH): String {
    if (identity.codePointCount(0, identity.length) <= maxLength) return identity
    val end = identity.offsetByCodePoints(0, maxLength - 1)
    return identity.substring(0, end).trimEnd() + "…"
}

private const val MAX_IDENTITY_LENGTH = 24

private fun browserPlatform(userAgent: String): String = when {
    userAgent.contains("Android") -> "Android"
    userAgent.contains("iPad") -> "iPadOS"
    userAgent.contains("iPhone") || userAgent.contains("iPod") -> "iOS"
    userAgent.contains("Windows") -> "Windows"
    userAgent.contains("CrOS") -> "ChromeOS"
    userAgent.contains("Macintosh") || userAgent.contains("Mac OS X") -> "macOS"
    userAgent.contains("Linux") -> "Linux"
    else -> ""
}

fun randomNickname(): String {
    val consonants = "bcdfghjklmnpqrstvwxz"
    val vowels = "aeiouy"
    val syllables = 2 + (0..1).random()
    return buildString {
        repeat(syllables) {
            append(consonants.random())
            append(vowels.random())
        }
    }
}
