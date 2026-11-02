package org.zorel.gb3android.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where a tribune comes from. One gb2c server can expose several tribunes, and
 * a direct board is read and written without any intermediary. Both kinds can
 * be enabled at the same time, like in roinroin.
 */
sealed interface SourceId {
    data class Gb2c(val url: String) : SourceId

    data class Direct(val board: String) : SourceId

    val kind: String
        get() = when (this) {
            is Gb2c -> "gb2c"
            is Direct -> "direct"
        }

    val id: String
        get() = when (this) {
            is Gb2c -> url
            is Direct -> board
        }

    val key: String get() = "$kind:$id"

    companion object {
        fun fromKey(key: String): SourceId? {
            val separator = key.indexOf(':')
            if (separator <= 0) return null
            val kind = key.substring(0, separator)
            val id = key.substring(separator + 1)
            if (id.isBlank()) return null
            return when (kind) {
                "gb2c" -> Gb2c(id)
                "direct" -> Direct(id)
                else -> null
            }
        }
    }
}

/** A tribune name qualified by the source that provides it. */
data class TribuneKey(val source: SourceId, val name: String) {
    val key: String get() = "${source.key}|$name"

    companion object {
        fun fromKey(key: String): TribuneKey? {
            val separator = key.lastIndexOf('|')
            if (separator <= 0 || separator == key.lastIndex) return null
            val source = SourceId.fromKey(key.substring(0, separator)) ?: return null
            return TribuneKey(source, key.substring(separator + 1))
        }
    }
}

/** One gb2c server, with the name used in the source labels. */
data class Gb2cServer(
    val name: String,
    val url: String,
    val enabled: Boolean = true,
) {
    fun normalized(): Gb2cServer {
        val normalizedUrl = Gb3Api.normalizeServerUrl(url).orEmpty()
        val normalizedName = name.trim().ifBlank { defaultName(normalizedUrl) }
        return copy(name = normalizedName, url = normalizedUrl)
    }

    fun validationError(): ServerValidationError? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return ServerValidationError.NameRequired
        if (trimmedName.length > MAX_NAME_LENGTH) return ServerValidationError.NameTooLong
        if (trimmedName.any { it.isISOControl() }) return ServerValidationError.NameInvalid
        if (Gb3Api.normalizeServerUrl(url) == null) return ServerValidationError.Url
        return null
    }

    companion object {
        const val MAX_NAME_LENGTH = 64

        fun defaultName(url: String): String {
            val host = url.trim().toHttpUrlOrNull()?.host
            return host?.takeIf { it.isNotBlank() } ?: "gb2c"
        }
    }
}

enum class ServerValidationError {
    NameRequired,
    NameTooLong,
    NameInvalid,
    Url,
}

/** JSON persistence for the configured gb2c servers. */
object Gb2cServerCodec {
    fun encode(servers: List<Gb2cServer>): String {
        val array = JSONArray()
        for (server in servers) {
            val normalized = server.normalized()
            if (normalized.url.isBlank()) continue
            array.put(
                JSONObject()
                    .put("name", normalized.name)
                    .put("url", normalized.url)
                    .put("enabled", normalized.enabled),
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<Gb2cServer> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val urls = HashSet<String>()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val server = Gb2cServer(
                        name = item.optString("name"),
                        url = item.optString("url"),
                        enabled = item.optBoolean("enabled", true),
                    )
                    if (server.validationError() != null) continue
                    val normalized = server.normalized()
                    if (urls.add(normalized.url)) add(normalized)
                }
            }
        }.getOrDefault(emptyList())
    }
}

/** Which tribunes are displayed for each gb2c server. */
object TribuneSelection {
    /**
     * `null` means the server was never configured: every known tribune is
     * shown, which is the convenient default for a newly added server.
     */
    fun effective(known: List<String>, selection: Set<String>?): List<String> =
        if (selection == null) known.sorted() else known.filter { it in selection }.sorted()
}

/** JSON persistence for the per-server tribune selection. */
object TribuneSelectionCodec {
    fun encode(byServer: Map<String, Set<String>>): String {
        val json = JSONObject()
        for ((url, names) in byServer) {
            val array = JSONArray()
            names.sorted().forEach(array::put)
            json.put(url, array)
        }
        return json.toString()
    }

    fun decode(raw: String): Map<String, Set<String>> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                for (url in json.keys()) {
                    val array = json.optJSONArray(url) ?: continue
                    val names = buildSet {
                        for (index in 0 until array.length()) {
                            val name = array.optString(index, "")
                            if (name.isNotBlank()) add(name)
                        }
                    }
                    put(url, names)
                }
            }
        }.getOrDefault(emptyMap())
    }
}

/** Connection state of one source, shown like roinroin's status dots. */
enum class SourceStatus { Working, Online, Error }
