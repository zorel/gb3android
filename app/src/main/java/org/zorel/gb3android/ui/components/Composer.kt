package org.zorel.gb3android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.TextDraft
import org.zorel.gb3android.ui.DisplayPost
import org.zorel.gb3android.ui.UiState
import org.zorel.gb3android.ui.theme.LocalGb3Palette

private const val MAX_COMPOSER_LENGTH = 65_536

@Composable
fun Composer(
    state: UiState,
    selectedPost: DisplayPost?,
    onDraftChange: (TextDraft) -> Unit,
    onSend: () -> Unit,
    onTools: () -> Unit,
    onReplySelected: (Post) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val palette = LocalGb3Palette.current
    val colors = MaterialTheme.colorScheme

    // Keep a local TextFieldValue instead of rebuilding it from TextDraft on
    // every recomposition. TextFieldValue carries the IME composition range;
    // dropping it on each keystroke prevents predictive text / suggestions
    // from working on many keyboards.
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = state.draft.text,
                selection = TextRange(state.draft.selectionStart, state.draft.selectionEnd),
            ),
        )
    }

    LaunchedEffect(state.draft.text) {
        if (fieldValue.text != state.draft.text) {
            fieldValue = TextFieldValue(
                text = state.draft.text,
                selection = TextRange(state.draft.selectionStart, state.draft.selectionEnd),
            )
        }
    }

    LaunchedEffect(state.focusComposerToken) {
        if (state.focusComposerToken > 0) {
            if (fieldValue.text == state.draft.text &&
                fieldValue.selection != TextRange(state.draft.selectionStart, state.draft.selectionEnd)
            ) {
                fieldValue = fieldValue.copy(
                    selection = TextRange(state.draft.selectionStart, state.draft.selectionEnd),
                )
            }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 5.dp),
        ) {
            if (state.uploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (selectedPost != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp),
                    ) {
                        Text(
                            text = "↩ ${selectedPost.post.clock} ${selectedPost.post.displayIdentity}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onReplySelected(selectedPost.post) }) {
                            Text("Insert")
                        }
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onTools,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Post tools")
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { value ->
                        if (value.text.length <= MAX_COMPOSER_LENGTH) {
                            fieldValue = value
                            onDraftChange(
                                TextDraft(value.text, value.selection.start, value.selection.end),
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.onSurface,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = true,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            keyboard?.hide()
                            onSend()
                        },
                    ),
                    decorationBox = { innerTextField ->
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.7f),
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            ) {
                                if (fieldValue.text.isEmpty()) {
                                    Text(
                                        text = "Message to ${state.activeTribune ?: "…"}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = palette.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                        }
                    },
                )
                FilledIconButton(
                    onClick = {
                        keyboard?.hide()
                        onSend()
                    },
                    enabled = state.canSend,
                    modifier = Modifier.size(46.dp),
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                        )
                    }
                }
            }
        }
    }
}
