package org.zorel.gb3android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.domain.MessageToken
import org.zorel.gb3android.ui.DisplayPost
import org.zorel.gb3android.ui.UiState
import org.zorel.gb3android.ui.theme.LocalGb3Palette
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PostList(
    state: UiState,
    onSelectPost: (String) -> Unit,
    onReply: (String) -> Unit,
    onCallAuthor: (Post) -> Unit,
    onActions: (DisplayPost) -> Unit,
    onNorlogeClick: (String, MessageToken.Norloge) -> Unit,
    onUrlClick: (String) -> Unit,
    onTotozClick: (String) -> Unit,
    onSwipeTribune: (Int) -> Unit,
    onJumpHandled: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val posts = state.displayPosts
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val palette = LocalGb3Palette.current
    var dragAmount by remember { mutableFloatStateOf(0f) }
    var previousSize by remember { mutableIntStateOf(posts.size) }

    LaunchedEffect(state.activeTribune) {
        previousSize = posts.size
        if (state.jumpTargetKey == null && posts.isNotEmpty()) {
            listState.scrollToItem(posts.lastIndex)
        }
    }

    LaunchedEffect(posts.size, state.activeTribune) {
        if (posts.isEmpty()) {
            previousSize = 0
            return@LaunchedEffect
        }
        if (posts.size > previousSize && state.jumpTargetKey == null) {
            val wasAtBottom = !listState.canScrollForward
            if (wasAtBottom || previousSize == 0) {
                listState.animateScrollToItem(posts.lastIndex)
            }
        }
        previousSize = posts.size
    }

    LaunchedEffect(state.jumpTargetKey, posts.size) {
        val target = state.jumpTargetKey ?: return@LaunchedEffect
        val index = posts.indexOfFirst { it.post.key == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            onJumpHandled()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.tribunes) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = 0f },
                    onHorizontalDrag = { _, amount -> dragAmount += amount },
                    onDragEnd = {
                        if (abs(dragAmount) > 90f) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSwipeTribune(if (dragAmount < 0f) 1 else -1)
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f },
                )
            },
    ) {
        when {
            posts.isEmpty() && (state.initializing || state.connecting) -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp))
                    Text(
                        text = if (state.directBoards) {
                            "Refreshing boards…"
                        } else {
                            "Connecting to ${state.serverUrl}…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            posts.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.activeTribune == null) {
                            "No tribune selected"
                        } else {
                            "No post in ${state.activeTribune}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (state.tribunes.isEmpty()) {
                            if (state.directBoards) {
                                "Open the menu to retry or add a board in Settings."
                            } else {
                                "Open the menu to retry or check the server URL in Settings."
                            }
                        } else {
                            "Swipe left or right to change tribune."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                    if (state.tribunes.isEmpty()) {
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(posts, key = { _, post -> post.post.key }) { index, display ->
                        PostRow(
                            index = index,
                            display = display,
                            selected = display.post.key == state.selectedKey,
                            selectedKey = state.selectedKey,
                            onSelect = { onSelectPost(display.post.key) },
                            onReply = { onReply(display.post.key) },
                            onCallAuthor = { onCallAuthor(display.post) },
                            onActions = { onActions(display) },
                            onNorlogeClick = { ref -> onNorlogeClick(display.post.key, ref) },
                            onUrlClick = onUrlClick,
                            onTotozClick = onTotozClick,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = listState.canScrollForward && posts.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(posts.lastIndex) }
                        },
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to latest posts")
                    }
                }
            }
        }
    }
}
