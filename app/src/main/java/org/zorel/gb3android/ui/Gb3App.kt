package org.zorel.gb3android.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.Screen
import org.zorel.gb3android.data.TextDraft
import org.zorel.gb3android.ui.components.AltSheet
import org.zorel.gb3android.ui.components.Composer
import org.zorel.gb3android.ui.components.EmojiSheet
import org.zorel.gb3android.ui.components.MenuSheet
import org.zorel.gb3android.ui.components.PostActionsSheet
import org.zorel.gb3android.ui.components.PostList
import org.zorel.gb3android.ui.components.TotozSheet
import org.zorel.gb3android.ui.components.TribuneBar
import org.zorel.gb3android.domain.MessageToken
import org.zorel.gb3android.ui.screens.LinuxfrOAuthScreen
import org.zorel.gb3android.ui.screens.SettingsScreen
import org.zorel.gb3android.ui.screens.SetupScreen
import org.zorel.gb3android.ui.theme.Gb3Theme
import org.zorel.gb3android.ui.theme.LocalGb3Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private sealed interface ActiveSheet {
    data object Menu : ActiveSheet
    data object Alt : ActiveSheet
    data object Totoz : ActiveSheet
    data object Emoji : ActiveSheet
    data class PostActions(val post: DisplayPost) : ActiveSheet
}

@Composable
fun Gb3App(viewModel: Gb3ViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var sheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var totozPreview by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onForeground()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(
                    message = event.text,
                    duration = if (event.isError) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            }
        }
    }

    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.attach(uri)
    }

    fun openExternal(url: String) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            runCatching { uriHandler.openUri(url) }.onFailure {
                scope.launch { snackbarHostState.showSnackbar("Cannot open this link") }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Unsupported link: $url") }
        }
    }

    BackHandler(enabled = state.screen != Screen.Main) {
        when (state.screen) {
            Screen.Settings -> viewModel.closeSettings()
            Screen.LinuxfrOauth -> viewModel.onLinuxfrOauthCancelled()
            Screen.Main -> Unit
        }
    }

    Gb3Theme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.setupRequired || state.serverUrl.isBlank() -> {
                        SetupScreen(state = state, onConnect = viewModel::saveSettings)
                    }

                    state.screen == Screen.Settings -> {
                        SettingsScreen(
                            state = state,
                            onBack = viewModel::closeSettings,
                            onSave = viewModel::saveSettings,
                            onConnectLinuxfr = viewModel::openLinuxfrOauth,
                            onDisconnectLinuxfr = viewModel::disconnectLinuxfr,
                            onMarkAllRead = viewModel::markAllRead,
                            onReconnect = viewModel::reconnect,
                            onRefresh = viewModel::refreshServerInfo,
                            onAddBoard = viewModel::addCustomBoard,
                            onRemoveBoard = viewModel::removeCustomBoard,
                        )
                    }

                    state.screen == Screen.LinuxfrOauth -> {
                        LinuxfrOAuthScreen(
                            state = state,
                            onConnected = viewModel::onLinuxfrConnected,
                            onCancel = viewModel::onLinuxfrOauthCancelled,
                        )
                    }

                    else -> {
                        MainScreen(
                            state = state,
                            onSelectTribune = viewModel::selectTribune,
                            onSwipeTribune = viewModel::selectNextTribune,
                            onSelectPost = viewModel::selectPost,
                            onReply = viewModel::insertReplyClock,
                            onCallAuthor = viewModel::callAuthor,
                            onActions = { sheet = ActiveSheet.PostActions(it) },
                            onNorlogeClick = viewModel::jumpToNorloge,
                            onUrlClick = ::openExternal,
                            onTotozClick = { totozPreview = it },
                            onDraftChange = viewModel::updateDraft,
                            onSend = viewModel::send,
                            onMenu = { sheet = ActiveSheet.Menu },
                            onReplySelected = { viewModel.insertReplyClock(it.key) },
                            onClearSelection = viewModel::clearSelection,
                            onJumpHandled = viewModel::jumpHandled,
                            onRetry = viewModel::reconnect,
                        )
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 58.dp, start = 12.dp, end = 12.dp),
                )

                totozPreview?.let { name ->
                    TotozImageOverlay(
                        name = name,
                        onDismiss = { totozPreview = null },
                    )
                }
            }
        }

        when (val active = sheet) {
            null -> Unit
            ActiveSheet.Menu -> MenuSheet(
                onDismiss = { sheet = null },
                onTotoz = { sheet = ActiveSheet.Totoz },
                onEmoji = { sheet = ActiveSheet.Emoji },
                onAttach = {
                    sheet = null
                    attachmentLauncher.launch(arrayOf("*/*"))
                },
                onAlt = { sheet = ActiveSheet.Alt },
                onSettings = {
                    sheet = null
                    viewModel.openSettings()
                },
                onMarkAllRead = {
                    sheet = null
                    viewModel.markAllRead()
                },
                onReconnect = {
                    sheet = null
                    viewModel.reconnect()
                },
            )

            ActiveSheet.Alt -> AltSheet(
                onDismiss = { sheet = null },
                onAlt = viewModel::applyAlt,
            )

            ActiveSheet.Totoz -> TotozSheet(
                onDismiss = { sheet = null },
                onSearch = viewModel::searchTotoz,
                onPick = viewModel::insertTotoz,
            )

            ActiveSheet.Emoji -> EmojiSheet(
                onDismiss = { sheet = null },
                onSearch = viewModel::searchEmoji,
                onPick = viewModel::insertEmoji,
            )

            is ActiveSheet.PostActions -> PostActionsSheet(
                display = active.post,
                onDismiss = { sheet = null },
                onReply = { viewModel.insertReplyClock(active.post.post.key) },
                onCallAuthor = { viewModel.callAuthor(active.post.post) },
                onSelect = { viewModel.selectPost(active.post.post.key) },
            )
        }
    }
}

@Composable
private fun MainScreen(
    state: UiState,
    onSelectTribune: (String) -> Unit,
    onSwipeTribune: (Int) -> Unit,
    onSelectPost: (String) -> Unit,
    onReply: (String) -> Unit,
    onCallAuthor: (Post) -> Unit,
    onActions: (DisplayPost) -> Unit,
    onNorlogeClick: (String, MessageToken.Norloge) -> Unit,
    onUrlClick: (String) -> Unit,
    onTotozClick: (String) -> Unit,
    onDraftChange: (TextDraft) -> Unit,
    onSend: () -> Unit,
    onMenu: () -> Unit,
    onReplySelected: (Post) -> Unit,
    onClearSelection: () -> Unit,
    onJumpHandled: () -> Unit,
    onRetry: () -> Unit,
) {
    val palette = LocalGb3Palette.current
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 1.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "gb3",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
            Spacer(Modifier.width(10.dp))
            val active = state.activeTribune
            if (active != null) {
                Text(
                    text = "/ $active",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            ConnectionStatus(state)
            Spacer(Modifier.width(10.dp))
            if (state.badges.any { (name, badge) -> name != state.activeTribune && badge.hasBigorno }) {
                Text("📢", fontSize = 13.sp)
            }
            if (state.badges.any { (name, badge) -> name != state.activeTribune && badge.hasReply }) {
                Text("↩", fontSize = 13.sp, color = palette.reply)
            }
            AlarmClock()
            IconButton(
                onClick = onMenu,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Open menu")
            }
        }
        TribuneBar(
            tribunes = state.tribunes,
            activeTribune = state.activeTribune,
            badges = state.badges,
            onSelect = onSelectTribune,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        PostList(
            state = state,
            onSelectPost = onSelectPost,
            onReply = onReply,
            onCallAuthor = onCallAuthor,
            onActions = onActions,
            onNorlogeClick = onNorlogeClick,
            onUrlClick = onUrlClick,
            onTotozClick = onTotozClick,
            onSwipeTribune = onSwipeTribune,
            onJumpHandled = onJumpHandled,
            onRetry = onRetry,
            modifier = Modifier.weight(1f),
        )
        Composer(
            state = state,
            selectedPost = state.selectedPost,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onTools = onMenu,
            onReplySelected = onReplySelected,
            onClearSelection = onClearSelection,
        )
    }
}

@Composable
private fun ConnectionStatus(state: UiState) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalGb3Palette.current
    val color: Color
    val label: String
    when {
        state.connected -> {
            color = colors.secondary
            label = "live"
        }
        state.connecting -> {
            color = colors.tertiary
            label = "sync"
        }
        else -> {
            color = colors.error
            label = "offline"
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics { contentDescription = "Stream $label" },
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
        )
    }
}

@Composable
private fun AlarmClock() {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }
    val preums = now.minute == 59
    val formatter = remember(preums) {
        DateTimeFormatter.ofPattern(if (preums) "HH:mm:ss" else "HH:mm")
    }
    Text(
        text = now.format(formatter),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = if (preums) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.End,
        modifier = Modifier
            .width(68.dp)
            .semantics {
                contentDescription = "Clock ${now.format(formatter)}"
            },
    )
}

@Composable
private fun TotozImageOverlay(
    name: String,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(name) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Any tap or the beginning of a scroll dismisses the image.
                    down.consume()
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "https://nsfw.totoz.eu/img/" + Uri.encode(name),
            contentDescription = "Totoz $name",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .padding(8.dp),
        )
    }
}
