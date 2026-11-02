package org.zorel.gb3android.ui

import org.zorel.gb3android.data.BlockedDisplayMode
import org.zorel.gb3android.data.Board
import org.zorel.gb3android.data.Gb2cServer
import org.zorel.gb3android.data.LinuxfrSession
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.Screen
import org.zorel.gb3android.data.SourceId
import org.zorel.gb3android.data.SourceStatus
import org.zorel.gb3android.data.TextDraft
import org.zorel.gb3android.data.TotozServer
import org.zorel.gb3android.data.TribuneBadge
import org.zorel.gb3android.data.TribuneKey
import org.zorel.gb3android.domain.ParsedMessage

data class DisplayPost(
    val post: Post,
    val parsed: ParsedMessage,
    val hidden: Boolean,
    val conversationColor: Int?,
    val isMine: Boolean,
    val isBigorno: Boolean,
    val isReply: Boolean,
    /** Resolved target key of each norloge in [parsed], by norloge index. */
    val refTargets: List<String?>,
)

/** Tribunes sharing one source, grouped like roinroin's sidebar. */
data class TribuneGroup(
    val source: SourceId,
    /** Server name for a gb2c group; empty for the direct-board group. */
    val labelName: String,
    val tribunes: List<TribuneKey>,
)

data class UiState(
    /** Optional gb2c URL used for emoji, attachments and LinuxFR OAuth. */
    val serverUrl: String = "",
    val gb2cServers: List<Gb2cServer> = emptyList(),
    val boards: List<Board> = emptyList(),
    val selectedBoards: Set<String> = emptySet(),
    /** Known tribune names per gb2c server URL, for the settings picker. */
    val knownGb2cTribunes: Map<String, List<String>> = emptyMap(),
    /**
     * Selected tribune names per gb2c server URL. An absent entry means every
     * known tribune is shown; an empty set means none is shown.
     */
    val selectedGb2cTribunes: Map<String, Set<String>> = emptyMap(),
    val totozServerUrl: String = TotozServer.DEFAULT_URL,
    val nickname: String = "",
    val tribunes: List<TribuneKey> = emptyList(),
    val activeTribune: TribuneKey? = null,
    val displayPosts: List<DisplayPost> = emptyList(),
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val initializing: Boolean = false,
    val gb2cStatus: Map<String, SourceStatus> = emptyMap(),
    val boardStatus: Map<String, SourceStatus> = emptyMap(),
    val localBlocked: Set<String> = emptySet(),
    val serverBlocked: Set<String> = emptySet(),
    val displayMode: BlockedDisplayMode = BlockedDisplayMode.Hide,
    val plopList: Set<String> = emptySet(),
    /** Keyed by [TribuneKey.key]. */
    val badges: Map<String, TribuneBadge> = emptyMap(),
    val selectedKey: String? = null,
    val jumpTargetKey: String? = null,
    val draft: TextDraft = TextDraft(),
    val screen: Screen = Screen.Main,
    val linuxfr: LinuxfrSession? = null,
    val lastPosted: String? = null,
    val sending: Boolean = false,
    val uploading: Boolean = false,
    val setupRequired: Boolean = false,
    val focusComposerToken: Int = 0,
) {
    val blockedIdentities: Set<String> get() = localBlocked + serverBlocked
    val activeKey: String? get() = activeTribune?.key
    val activeName: String? get() = activeTribune?.name
    val activeIndex: Int
        get() = activeTribune?.let { active ->
            tribunes.indexOfFirst { it.key == active.key }
        } ?: -1
    val selectedPost: DisplayPost? get() = displayPosts.firstOrNull { it.post.key == selectedKey }
    val canSend: Boolean
        get() = !sending && activeTribune != null && draft.text.isNotBlank()

    val activeStatus: SourceStatus?
        get() = when (val source = activeTribune?.source) {
            is SourceId.Gb2c -> gb2cStatus[source.url]
            is SourceId.Direct -> boardStatus[source.board]
            null -> null
        }

    fun serverName(url: String): String? =
        gb2cServers.firstOrNull { it.url == url }?.name?.takeIf { it.isNotBlank() }

    fun knownTribunes(server: Gb2cServer): List<String> =
        knownGb2cTribunes[server.url].orEmpty()

    fun isGb2cTribuneSelected(server: Gb2cServer, name: String): Boolean =
        selectedGb2cTribunes[server.url]?.contains(name) ?: true

    /** Group enabled tribunes by source, in configuration order. */
    fun tribuneGroups(): List<TribuneGroup> {
        val groups = ArrayList<TribuneGroup>()
        for (server in gb2cServers) {
            if (!server.enabled) continue
            val source = SourceId.Gb2c(server.url)
            val members = tribunes.filter { it.source == source }
            if (members.isNotEmpty()) {
                groups += TribuneGroup(source, server.name, members)
            }
        }
        val gb2cUrls = gb2cServers.mapTo(HashSet()) { it.url }
        val orphanGb2c = tribunes
            .filter { it.source is SourceId.Gb2c && (it.source as SourceId.Gb2c).url !in gb2cUrls }
            .groupBy { (it.source as SourceId.Gb2c).url }
        for ((url, members) in orphanGb2c) {
            groups += TribuneGroup(SourceId.Gb2c(url), url, members)
        }
        val direct = tribunes.filter { it.source is SourceId.Direct }
        if (direct.isNotEmpty()) {
            groups += TribuneGroup(SourceId.Direct(""), "", direct)
        }
        return groups
    }
}

data class SettingsInput(
    val gb2cServers: List<Gb2cServer>,
    val selectedBoards: Set<String>,
    val selectedGb2cTribunes: Map<String, Set<String>>,
    val totozServerUrl: String,
    val nickname: String,
    val blockedIdentities: String,
    val displayMode: BlockedDisplayMode,
    val plopList: String,
)

sealed interface UiEvent {
    data class Message(val text: String, val isError: Boolean = false) : UiEvent
}
