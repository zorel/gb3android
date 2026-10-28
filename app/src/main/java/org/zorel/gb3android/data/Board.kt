package org.zorel.gb3android.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Where posts and tribunes come from. */
enum class ConnectionMode { Server, DirectBoards }

/** How a board authenticates a post. */
enum class BoardAuth { None, OAuth2 }

/** Why a user-defined board cannot be used. */
enum class BoardValidationError {
    NameRequired,
    NameTooLong,
    NameInvalid,
    BackendUrl,
    PostUrl,
    PostField,
    InfoField,
}

/**
 * A bouchot-style board that the app can read and post to directly.
 *
 * The field names mirror gb2c's `Tribune` struct so a board configured there
 * can be copied here unchanged. `backendUrl` must return the usual five-column
 * TSV (`id`, `time`, `info`, `login`, `message`) and `postUrl` must accept an
 * `application/x-www-form-urlencoded` POST.
 */
data class Board(
    val name: String,
    val backendUrl: String,
    val postUrl: String,
    val postField: String = DEFAULT_POST_FIELD,
    val infoField: String = "",
    val auth: BoardAuth = BoardAuth.None,
    val predefined: Boolean = false,
) {
    val requiresOAuth: Boolean get() = auth == BoardAuth.OAuth2

    /** Returns why the board is invalid, or null when it is valid. */
    fun validationError(): BoardValidationError? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return BoardValidationError.NameRequired
        if (trimmedName.length > MAX_NAME_LENGTH) return BoardValidationError.NameTooLong
        if (trimmedName.any { it.isWhitespace() || it == ',' || it.isISOControl() }) {
            return BoardValidationError.NameInvalid
        }
        if (normalizeHttpUrl(backendUrl) == null) return BoardValidationError.BackendUrl
        if (normalizeHttpUrl(postUrl) == null) return BoardValidationError.PostUrl
        if (!isValidFieldName(postField)) return BoardValidationError.PostField
        if (infoField.isNotEmpty() && !isValidFieldName(infoField)) {
            return BoardValidationError.InfoField
        }
        return null
    }

    /** A trimmed, normalized copy suitable for persistence. */
    fun normalized(): Board = copy(
        name = name.trim(),
        backendUrl = normalizeHttpUrl(backendUrl).orEmpty(),
        postUrl = normalizeHttpUrl(postUrl).orEmpty(),
        postField = postField.trim().ifBlank { DEFAULT_POST_FIELD },
        infoField = infoField.trim(),
        predefined = false,
    )

    companion object {
        const val DEFAULT_POST_FIELD = "message"
        const val MAX_NAME_LENGTH = 64
        private const val MAX_FIELD_NAME_LENGTH = 64

        fun normalizeHttpUrl(raw: String): String? {
            val value = raw.trim()
            if (value.isEmpty()) return null
            val url = value.toHttpUrlOrNull() ?: return null
            if (url.scheme != "http" && url.scheme != "https") return null
            return url.toString()
        }

        private fun isValidFieldName(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.isNotEmpty() &&
                trimmed.length <= MAX_FIELD_NAME_LENGTH &&
                trimmed.none { it.isISOControl() }
        }
    }
}

/**
 * Boards bundled with the app, kept in sync with gb2c's default configuration:
 * the five hard-coded boards plus the default gb0 and miaoli tribunes.
 */
object PredefinedBoards {
    val all: List<Board> by lazy {
        listOf(
            Board(
                name = "dlfp",
                backendUrl = "https://linuxfr.org/board/index.tsv",
                postUrl = "https://linuxfr.org/api/v1/board",
                auth = BoardAuth.OAuth2,
                predefined = true,
            ),
            Board(
                name = "euromussels",
                backendUrl = "https://faab.euromussels.eu/data/backend.tsv",
                postUrl = "https://faab.euromussels.eu/add.php",
                predefined = true,
            ),
            Board(
                name = "gabuzomeu",
                backendUrl = "https://gb3.plop.cc/gb0/tsv",
                postUrl = "https://gb3.plop.cc/gb0/post",
                predefined = true,
            ),
            Board(
                name = "moules",
                backendUrl = "https://moules.org/board/backend/tsv",
                postUrl = "https://moules.org/board/add.php?backend=tsv",
                predefined = true,
            ),
            Board(
                name = "devnewton",
                backendUrl = "https://gb3.devnewton.fr/gb0/tsv",
                postUrl = "https://gb3.devnewton.fr/gb0/post",
                predefined = true,
            ),
            Board(
                name = "ototu",
                backendUrl = "https://www.ototu.me/goboard/backend/tsv",
                postUrl = "https://www.ototu.me/goboard/post",
                predefined = true,
            ),
            Board(
                name = "reveildutroll",
                backendUrl = "https://miaoli.im/%2B6VtcdSCI0Y/posts.tsv",
                postUrl = "https://miaoli.im/%2B6VtcdSCI0Y",
                predefined = true,
            ),
            Board(
                name = "sveetch",
                backendUrl = "http://sveetch.net/tribune/remote/tsv/",
                postUrl = "http://sveetch.net/tribune/post/tsv/?last_id=1",
                postField = "content",
                predefined = true,
            ),
        ).sortedBy { it.name }
    }

    /** Merge user-defined boards after the built-ins, rejecting duplicates. */
    fun merge(custom: List<Board>): List<Board> {
        val names = all.mapTo(HashSet()) { it.name }
        val accepted = ArrayList<Board>(custom.size)
        for (board in custom) {
            if (board.validationError() != null) continue
            val normalized = board.normalized()
            if (!names.add(normalized.name)) continue
            accepted += normalized
        }
        return (all + accepted).sortedBy { it.name }
    }
}

/** JSON persistence for user-defined boards. */
object BoardCodec {
    fun encode(boards: List<Board>): String {
        val array = JSONArray()
        for (board in boards) {
            if (board.predefined) continue
            val normalized = board.normalized()
            array.put(
                JSONObject()
                    .put("name", normalized.name)
                    .put("backendUrl", normalized.backendUrl)
                    .put("postUrl", normalized.postUrl)
                    .put("postField", normalized.postField)
                    .put("infoField", normalized.infoField)
                    .put("auth", normalized.auth.name),
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<Board> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val names = HashSet<String>()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val board = Board(
                        name = item.optString("name"),
                        backendUrl = item.optString("backendUrl"),
                        postUrl = item.optString("postUrl"),
                        postField = item.optString("postField", Board.DEFAULT_POST_FIELD)
                            .ifBlank { Board.DEFAULT_POST_FIELD },
                        infoField = item.optString("infoField", ""),
                        auth = if (item.optString("auth") == BoardAuth.OAuth2.name) {
                            BoardAuth.OAuth2
                        } else {
                            BoardAuth.None
                        },
                    )
                    if (board.validationError() != null) continue
                    val normalized = board.normalized()
                    if (names.add(normalized.name)) add(normalized)
                }
            }
        }.getOrDefault(emptyList())
    }
}
