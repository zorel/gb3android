package org.zorel.gb3android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Small persistence layer replacing the browser localStorage used by gc2.
 * Everything is intentionally plain SharedPreferences: the payload is tiny and
 * the app is usable without any account on the server.
 */
class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("gb3", Context.MODE_PRIVATE)

    /**
     * Legacy single-server URL. It is still used as the optional gb2c helper
     * for emoji, attachments and LinuxFR when no server is enabled.
     */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_SERVER, value) }

    /** Legacy connection mode, only read while migrating older settings. */
    val legacyConnectionMode: ConnectionMode
        get() = if (prefs.getString(KEY_CONNECTION_MODE, null) == "direct") {
            ConnectionMode.DirectBoards
        } else {
            ConnectionMode.Server
        }

    var customBoardsJson: String
        get() = prefs.getString(KEY_CUSTOM_BOARDS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_CUSTOM_BOARDS, value) }

    var gb2cServersJson: String
        get() = prefs.getString(KEY_GB2C_SERVERS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_GB2C_SERVERS, value) }

    var selectedBoardsCsv: String
        get() = prefs.getString(KEY_SELECTED_BOARDS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_SELECTED_BOARDS, value) }

    var knownGb2cTribunesJson: String
        get() = prefs.getString(KEY_KNOWN_GB2C_TRIBUNES, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_KNOWN_GB2C_TRIBUNES, value) }

    var activeTribuneKey: String?
        get() = prefs.getString(KEY_ACTIVE_TRIBUNE, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_ACTIVE_TRIBUNE) else putString(KEY_ACTIVE_TRIBUNE, value)
        }

    var totozServerUrl: String
        get() = prefs.getString(KEY_TOTOZ_SERVER, TotozServer.DEFAULT_URL).orEmpty()
        set(value) = prefs.edit { putString(KEY_TOTOZ_SERVER, value) }

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_NICKNAME, value) }

    /** Legacy comma-separated selection, only read while migrating. */
    var selectedTribunesCsv: String
        get() = prefs.getString(KEY_TRIBUNES, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TRIBUNES, value) }

    /** Legacy known tribunes, only read while migrating. */
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

    /**
     * Configured gb2c servers, migrating the single-server settings used by
     * earlier releases. A `null` preference means "not migrated yet"; an empty
     * JSON array means the user really removed every server.
     */
    fun loadGb2cServers(): List<Gb2cServer> {
        val raw = prefs.getString(KEY_GB2C_SERVERS, null)
        if (raw != null) return Gb2cServerCodec.decode(raw)

        val legacyUrl = Gb3Api.normalizeServerUrl(serverUrl) ?: run {
            gb2cServersJson = Gb2cServerCodec.encode(emptyList())
            return emptyList()
        }
        val migrated = if (legacyConnectionMode == ConnectionMode.Server) {
            listOf(
                Gb2cServer(
                    name = Gb2cServer.defaultName(legacyUrl),
                    url = legacyUrl,
                    enabled = true,
                ),
            )
        } else {
            emptyList()
        }
        gb2cServersJson = Gb2cServerCodec.encode(migrated)
        return migrated
    }

    fun saveGb2cServers(servers: List<Gb2cServer>) {
        gb2cServersJson = Gb2cServerCodec.encode(servers)
    }

    /**
     * Direct boards selected as tribunes. Migrates the old direct-mode
     * selection the first time the new preference is absent.
     */
    fun loadSelectedBoards(boardNames: Set<String>): Set<String> {
        val raw = prefs.getString(KEY_SELECTED_BOARDS, null)
        if (raw != null) {
            return raw.csvSet().filter { it in boardNames }.toSet()
        }
        val migrated = if (legacyConnectionMode == ConnectionMode.DirectBoards) {
            selectedTribunesCsv.csvSet().filter { it in boardNames }.toSet()
        } else {
            emptySet()
        }
        selectedBoardsCsv = migrated.toCsv()
        return migrated
    }

    fun saveSelectedBoards(boards: Set<String>) {
        selectedBoardsCsv = boards.toCsv()
    }

    /** Cached tribune names per gb2c server URL. */
    fun loadKnownTribunes(): Map<String, List<String>> {
        val raw = prefs.getString(KEY_KNOWN_GB2C_TRIBUNES, null)
        if (raw != null) {
            if (raw.isBlank()) return emptyMap()
            return runCatching {
                val json = JSONObject(raw)
                buildMap {
                    for (url in json.keys()) {
                        val array = json.optJSONArray(url) ?: continue
                        val names = buildList {
                            for (index in 0 until array.length()) {
                                val name = array.optString(index, "")
                                if (name.isNotBlank()) add(name)
                            }
                        }
                        put(url, names.sorted())
                    }
                }
            }.getOrDefault(emptyMap())
        }

        val legacyUrl = Gb3Api.normalizeServerUrl(serverUrl) ?: run {
            knownGb2cTribunesJson = "{}"
            return emptyMap()
        }
        val names = knownTribunesCsv.csvSet().toList().sorted()
        val migrated = if (names.isEmpty()) emptyMap() else mapOf(legacyUrl to names)
        saveKnownTribunes(migrated)
        return migrated
    }

    fun saveKnownTribunes(byUrl: Map<String, List<String>>) {
        val json = JSONObject()
        for ((url, names) in byUrl) {
            val array = org.json.JSONArray()
            names.sorted().forEach(array::put)
            json.put(url, array)
        }
        knownGb2cTribunesJson = json.toString()
    }

    fun lastReadPostId(tribuneKey: String): Long {
        val key = lastReadKey(tribuneKey)
        if (prefs.contains(key)) return prefs.getLong(key, -1L)
        // Preserve markers written by releases that only knew tribune names.
        val legacyName = tribuneKey.substringAfterLast('|')
        return prefs.getLong(lastReadKey(legacyName), -1L)
    }

    fun setLastReadPostId(tribuneKey: String, postId: Long) {
        prefs.edit { putLong(lastReadKey(tribuneKey), postId) }
    }

    fun markAllRead(tribuneKeys: Collection<String>, maxIdByTribune: Map<String, Long>) {
        prefs.edit(commit = true) {
            for (tribune in tribuneKeys) {
                maxIdByTribune[tribune]?.let { putLong(lastReadKey(tribune), it) }
            }
        }
    }

    private fun lastReadKey(tribune: String) = "last_read_$tribune"

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_CONNECTION_MODE = "connection_mode"
        const val KEY_CUSTOM_BOARDS = "custom_boards"
        const val KEY_GB2C_SERVERS = "gb2c_servers"
        const val KEY_SELECTED_BOARDS = "selected_boards"
        const val KEY_KNOWN_GB2C_TRIBUNES = "known_gb2c_tribunes"
        const val KEY_ACTIVE_TRIBUNE = "active_tribune_key"
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
