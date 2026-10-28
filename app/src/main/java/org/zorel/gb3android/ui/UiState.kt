package org.zorel.gb3android.ui

import org.zorel.gb3android.data.BlockedDisplayMode
import org.zorel.gb3android.data.Board
import org.zorel.gb3android.data.ConnectionMode
import org.zorel.gb3android.data.LinuxfrSession
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.Screen
import org.zorel.gb3android.data.TextDraft
import org.zorel.gb3android.data.TotozServer
import org.zorel.gb3android.data.TribuneBadge
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

data class UiState(
    val serverUrl: String = "",
    val connectionMode: ConnectionMode = ConnectionMode.Server,
    val boards: List<Board> = emptyList(),
    val totozServerUrl: String = TotozServer.DEFAULT_URL,
    val nickname: String = "",
    val availableTribunes: List<String> = emptyList(),
    val tribunes: List<String> = emptyList(),
    val activeTribune: String? = null,
    val displayPosts: List<DisplayPost> = emptyList(),
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val initializing: Boolean = false,
    val localBlocked: Set<String> = emptySet(),
    val serverBlocked: Set<String> = emptySet(),
    val displayMode: BlockedDisplayMode = BlockedDisplayMode.Hide,
    val plopList: Set<String> = emptySet(),
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
    val directBoards: Boolean get() = connectionMode == ConnectionMode.DirectBoards
    val activeIndex: Int get() = activeTribune?.let { tribunes.indexOf(it) } ?: -1
    val selectedPost: DisplayPost? get() = displayPosts.firstOrNull { it.post.key == selectedKey }
    val canSend: Boolean
        get() = !sending && activeTribune != null && draft.text.isNotBlank()
}

data class SettingsInput(
    val serverUrl: String,
    val connectionMode: ConnectionMode,
    val totozServerUrl: String,
    val nickname: String,
    val selectedTribunes: Set<String>,
    val blockedIdentities: String,
    val displayMode: BlockedDisplayMode,
    val plopList: String,
)

sealed interface UiEvent {
    data class Message(val text: String, val isError: Boolean = false) : UiEvent
}
