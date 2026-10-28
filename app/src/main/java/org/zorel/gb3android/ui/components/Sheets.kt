package org.zorel.gb3android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.zorel.gb3android.R
import org.zorel.gb3android.data.EmojiItem
import org.zorel.gb3android.data.Post
import org.zorel.gb3android.data.Totoz
import org.zorel.gb3android.ui.DisplayPost
import org.zorel.gb3android.ui.theme.LocalGb3Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gb3BottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        content()
    }
}

@Composable
fun MenuSheet(
    onDismiss: () -> Unit,
    onTotoz: () -> Unit,
    onEmoji: () -> Unit,
    onAttach: () -> Unit,
    onAlt: () -> Unit,
    onSettings: () -> Unit,
    onMarkAllRead: () -> Unit,
    onReconnect: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Gb3BottomSheet(onDismiss) {
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            SheetHeader(stringResource(R.string.app_name))
            MenuItem("😱", stringResource(R.string.menu_totoz_search), onTotoz)
            MenuItem("💩", stringResource(R.string.menu_emoji_search), onEmoji)
            MenuItem("📎", stringResource(R.string.menu_attach), onAttach)
            MenuItem("⎇", stringResource(R.string.menu_formatting), onAlt)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuItem("⚙", stringResource(R.string.menu_settings), onSettings)
            MenuItem("✅", stringResource(R.string.menu_mark_all_read), onMarkAllRead)
            MenuItem("🔄", stringResource(R.string.menu_reconnect), onReconnect)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuItem("ℹ", stringResource(R.string.menu_about)) {
                runCatching { uriHandler.openUri("https://codeberg.org/devnewton/gb3") }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun MenuItem(icon: String, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Text(icon, fontSize = 20.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private val ALT_BUTTONS = listOf(
    R.string.alt_blam to "blam",
    R.string.alt_moment to "moment",
    R.string.alt_phi to "phi",
    R.string.alt_bold to "bold",
    R.string.alt_italic to "italic",
    R.string.alt_underline to "underline",
    R.string.alt_strike to "strikeThrough",
    R.string.alt_teletext to "teletext",
    R.string.alt_code to "code",
    R.string.alt_spoiler to "spoiler",
    R.string.alt_paf to "paf",
    R.string.alt_acappella to "acappella",
    R.string.alt_last_posted to "lastPosted",
)

@Composable
fun AltSheet(
    onDismiss: () -> Unit,
    onAlt: (String) -> Unit,
) {
    Gb3BottomSheet(onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SheetHeader(stringResource(R.string.menu_formatting))
            Text(
                text = stringResource(R.string.alt_shortcuts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ALT_BUTTONS.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (labelRes, name) ->
                        OutlinedButton(
                            onClick = {
                                onAlt(name)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}

@Composable
fun TotozSheet(
    onDismiss: () -> Unit,
    onSearch: suspend (String) -> Result<List<Totoz>>,
    onPick: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Totoz>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchFailed = stringResource(R.string.totoz_search_failed)

    fun search() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            onSearch(query)
                .onSuccess {
                    results = it
                    searched = true
                }
                .onFailure { error = it.message ?: searchFailed }
            loading = false
        }
    }

    Gb3BottomSheet(onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SheetHeader("Totoz")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.totoz_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                IconButton(onClick = { search() }, enabled = !loading) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.totoz_search),
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(24.dp)
                        .size(28.dp),
                )
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (!loading && searched && results.isEmpty() && error == null) {
                Text(
                    text = stringResource(R.string.totoz_none),
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(92.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 430.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(results) { index, totoz ->
                    Surface(
                        onClick = {
                            onPick(totoz.name)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier,
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(
                                model = totoz.image,
                                contentDescription = totoz.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                            Text(
                                text = totoz.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 22.dp))
        }
    }
}

@Composable
fun EmojiSheet(
    onDismiss: () -> Unit,
    onSearch: suspend (String) -> Result<List<EmojiItem>>,
    onPick: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EmojiItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val searchFailed = stringResource(R.string.emoji_search_failed)

    fun search() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            onSearch(query)
                .onSuccess {
                    results = it
                    searched = true
                }
                .onFailure { error = it.message ?: searchFailed }
            loading = false
        }
    }

    Gb3BottomSheet(onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SheetHeader("Emoji")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.emoji_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                IconButton(onClick = { search() }, enabled = !loading) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.emoji_search),
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(24.dp)
                        .size(28.dp),
                )
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (!loading && searched && results.isEmpty() && error == null) {
                Text(
                    text = stringResource(R.string.emoji_none),
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(84.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 430.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(results) { _, emoji ->
                    Surface(
                        onClick = {
                            onPick(emoji.characters)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(emoji.characters, fontSize = 28.sp)
                            Text(
                                text = emoji.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 22.dp))
        }
    }
}

@Composable
fun PostActionsSheet(
    display: DisplayPost,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCallAuthor: () -> Unit,
    onSelect: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val palette = LocalGb3Palette.current
    val post = display.post
    Gb3BottomSheet(onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetHeader(stringResource(R.string.post_actions_title, post.clock))
            Text(
                text = stringResource(R.string.post_actions_meta, post.fullDate, post.tribune),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
            Text(
                text = stringResource(R.string.post_author, post.displayIdentity),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (post.login.isNotBlank()) {
                Text(
                    text = stringResource(R.string.post_login, post.login),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
            if (post.info.isNotBlank()) {
                Text(
                    text = stringResource(R.string.post_info, post.info),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onReply()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.action_reply))
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(post.message))
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.action_copy_message))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onCallAuthor()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.action_call_author))
                }
                TextButton(
                    onClick = {
                        onSelect()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.action_select))
                }
            }
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}
