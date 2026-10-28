package org.zorel.gb3android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Small persistence layer replacing the browser localStorage used by gc2.
 * Everything is intentionally plain SharedPreferences: the payload is tiny and
 * the app is usable without any account on the server.
 */
class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("gb3", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_SERVER, value) }

    var connectionMode: ConnectionMode
        get() = if (prefs.getString(KEY_CONNECTION_MODE, null) == "direct") {
            ConnectionMode.DirectBoards
        } else {
            ConnectionMode.Server
        }
        set(value) = prefs.edit {
            putString(
                KEY_CONNECTION_MODE,
                if (value == ConnectionMode.DirectBoards) "direct" else "server",
            )
        }

    var customBoardsJson: String
        get() = prefs.getString(KEY_CUSTOM_BOARDS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CUSTOM_BOARDS, value) }

    var totozServerUrl: String
        get() = prefs.getString(KEY_TOTOZ_SERVER, TotozServer.DEFAULT_URL).orEmpty()
        set(value) = prefs.edit { putString(KEY_TOTOZ_SERVER, value) }

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_NICKNAME, value) }

    /** Raw comma-separated list, as edited in the settings screen. */
    var selectedTribunesCsv: String
        get() = prefs.getString(KEY_TRIBUNES, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TRIBUNES, value) }

    var knownTribunesCsv: String
        get() = prefs.getString(KEY_KNOWN_TRIBUNES, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_KNOWN_TRIBUNES, value) }

    var localBlockedIdentitiesCsv: String
        get() = prefs.getString(KEY_BLOCKED, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_BLOCKED, value) }

    var blockedDisplayMode: BlockedDisplayMode
        get() = when (prefs.getString(KEY_DISPLAY_MODE, null)) {
            "gray" -> BlockedDisplayMode.Gray
            else -> BlockedDisplayMode.Hide
        }
        set(value) = prefs.edit {
            putString(KEY_DISPLAY_MODE, if (value == BlockedDisplayMode.Gray) "gray" else "hide")
        }

    var plopListCsv: String
        get() = prefs.getString(KEY_PLOP_LIST, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PLOP_LIST, value) }

    var lastPostedMessage: String
        get() = prefs.getString(KEY_LAST_POSTED, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_LAST_POSTED, value) }

    var draft: String
        get() = prefs.getString(KEY_DRAFT, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_DRAFT, value) }

    var pendingLinuxfrMessage: String?
        get() = prefs.getString(KEY_PENDING_LINUXFR, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_PENDING_LINUXFR) else putString(KEY_PENDING_LINUXFR, value)
        }

    var linuxfrSession: LinuxfrSession?
        get() {
            val access = prefs.getString(KEY_LINUXFR_ACCESS, "").orEmpty()
            if (access.isBlank()) return null
            return LinuxfrSession(
                accessToken = access,
                refreshToken = prefs.getString(KEY_LINUXFR_REFRESH, "").orEmpty(),
                expiresAt = prefs.getLong(KEY_LINUXFR_EXPIRES, 0L),
                login = prefs.getString(KEY_LINUXFR_LOGIN, "").orEmpty(),
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_LINUXFR_ACCESS)
                remove(KEY_LINUXFR_REFRESH)
                remove(KEY_LINUXFR_EXPIRES)
                remove(KEY_LINUXFR_LOGIN)
            } else {
                putString(KEY_LINUXFR_ACCESS, value.accessToken)
                putString(KEY_LINUXFR_REFRESH, value.refreshToken)
                putLong(KEY_LINUXFR_EXPIRES, value.expiresAt)
                putString(KEY_LINUXFR_LOGIN, value.login)
            }
        }

    fun lastReadPostId(tribune: String): Long =
        prefs.getLong(lastReadKey(tribune), -1L)

    fun setLastReadPostId(tribune: String, postId: Long) {
        prefs.edit { putLong(lastReadKey(tribune), postId) }
    }

    fun markAllRead(tribunes: Collection<String>, maxIdByTribune: Map<String, Long>) {
        prefs.edit(commit = true) {
            for (tribune in tribunes) {
                maxIdByTribune[tribune]?.let { putLong(lastReadKey(tribune), it) }
            }
        }
    }

    private fun lastReadKey(tribune: String) = "last_read_$tribune"

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_CONNECTION_MODE = "connection_mode"
        const val KEY_CUSTOM_BOARDS = "custom_boards"
        const val KEY_TOTOZ_SERVER = "totoz_server_url"
        const val KEY_NICKNAME = "nickname"
        const val KEY_TRIBUNES = "tribunes"
        const val KEY_KNOWN_TRIBUNES = "known_tribunes"
        const val KEY_BLOCKED = "blocked_identities"
        const val KEY_DISPLAY_MODE = "blocked_display_mode"
        const val KEY_PLOP_LIST = "plop_list"
        const val KEY_LAST_POSTED = "last_posted_message"
        const val KEY_DRAFT = "draft"
        const val KEY_PENDING_LINUXFR = "linuxfr_pending_message"
        const val KEY_LINUXFR_ACCESS = "linuxfr_access_token"
        const val KEY_LINUXFR_REFRESH = "linuxfr_refresh_token"
        const val KEY_LINUXFR_EXPIRES = "linuxfr_expires_at"
        const val KEY_LINUXFR_LOGIN = "linuxfr_login"
    }
}
