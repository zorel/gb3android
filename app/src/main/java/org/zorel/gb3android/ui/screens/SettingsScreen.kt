package org.zorel.gb3android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zorel.gb3android.data.BlockedDisplayMode
import org.zorel.gb3android.data.Board
import org.zorel.gb3android.data.BoardAuth
import org.zorel.gb3android.data.ConnectionMode
import org.zorel.gb3android.data.csvSet
import org.zorel.gb3android.ui.SettingsInput
import org.zorel.gb3android.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSave: (SettingsInput) -> Boolean,
    onConnectLinuxfr: () -> Unit,
    onDisconnectLinuxfr: () -> Unit,
    onMarkAllRead: () -> Unit,
    onReconnect: () -> Unit,
    onRefresh: () -> Unit,
    onAddBoard: (Board) -> Boolean,
    onRemoveBoard: (String) -> Boolean,
) {
    var serverUrl by rememberSaveable { mutableStateOf(state.serverUrl) }
    var nickname by rememberSaveable { mutableStateOf(state.nickname) }
    var selectedCsv by rememberSaveable { mutableStateOf(state.tribunes.joinToString(",")) }
    var blocked by rememberSaveable { mutableStateOf(state.localBlocked.joinToString(",")) }
    var displayModeName by rememberSaveable { mutableStateOf(state.displayMode.name) }
    var plopList by rememberSaveable { mutableStateOf(state.plopList.joinToString(",")) }
    var modeName by rememberSaveable { mutableStateOf(state.connectionMode.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    var showBoardDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val directMode = modeName == ConnectionMode.DirectBoards.name
    val boardNames = state.boards.map { it.name }

    LaunchedEffect(state.tribunes) {
        selectedCsv = state.tribunes.joinToString(",")
    }

    val options = if (directMode) {
        boardNames.sorted()
    } else if (state.availableTribunes.isNotEmpty()) {
        state.availableTribunes
    } else {
        state.tribunes
    }
    val selected = selectedCsv.csvSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSection("Connection") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !directMode,
                            onClick = {
                                modeName = ConnectionMode.Server.name
                                selectedCsv = state.tribunes.joinToString(",")
                            },
                            label = { Text("gb2c server") },
                        )
                        FilterChip(
                            selected = directMode,
                            onClick = {
                                modeName = ConnectionMode.DirectBoards.name
                                val current = selectedCsv.csvSet()
                                if (current.none { it in boardNames }) {
                                    selectedCsv = boardNames.sorted().joinToString(",")
                                }
                            },
                            label = { Text("Direct boards") },
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = {
                            Text(
                                if (directMode) {
                                    "gb2c URL (optional, for totoz/emoji/LinuxFR)"
                                } else {
                                    "gb2c URL"
                                },
                            )
                        },
                        placeholder = { Text("https://gb3.example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                if (directMode) {
                                    "Boards are fetched and posted to directly; the server is only " +
                                        "used for auxiliary services."
                                } else {
                                    "Changing the server reloads tribunes and conversation filters."
                                },
                            )
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val ok = onSave(
                                SettingsInput(
                                    serverUrl = serverUrl,
                                    connectionMode = if (directMode) {
                                        ConnectionMode.DirectBoards
                                    } else {
                                        ConnectionMode.Server
                                    },
                                    nickname = nickname,
                                    selectedTribunes = selected,
                                    blockedIdentities = blocked,
                                    displayMode = displayModeName.toBlockedDisplayMode(),
                                    plopList = plopList,
                                ),
                            )
                            if (!ok) error = "Invalid server URL or board selection"
                        }) {
                            Text("Save settings")
                        }
                        OutlinedButton(onClick = onRefresh) {
                            Text(if (directMode) "Refresh boards" else "Refresh tribunes")
                        }
                    }
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                SettingsSection("Identity") {
                    androidx.compose.material3.OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Sent as the post info. Leave empty to generate one.")
                        },
                    )
                    Text(
                        text = state.linuxfr?.let {
                            "LinuxFR connected as ${it.login.ifBlank { "unknown" }}"
                        } ?: "LinuxFR not connected",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnectLinuxfr) {
                            Text(if (state.linuxfr == null) "Connect LinuxFR" else "Reconnect")
                        }
                        if (state.linuxfr != null) {
                            TextButton(onClick = onDisconnectLinuxfr) {
                                Text("Disconnect")
                            }
                        }
                    }
                    Text(
                        text = "LinuxFR is only required to post in the dlfp tribune.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection("Tribunes") {
                    if (options.isEmpty()) {
                        Text(
                            text = if (directMode) {
                                "No boards available. Add one below."
                            } else {
                                "No tribunes loaded yet. Save a valid server URL and refresh."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = if (directMode) {
                                "Selected boards are downloaded directly. Keep at least one."
                            } else {
                                "Selected tribunes are streamed live. Keep at least one."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        options.forEach { tribune ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                            ) {
                                Checkbox(
                                    checked = tribune in selected,
                                    onCheckedChange = { checked ->
                                        val current = selectedCsv.csvSet()
                                        val next = if (checked) current + tribune else current - tribune
                                        if (next.isEmpty()) {
                                            selectionError = "Keep at least one tribune selected."
                                        } else {
                                            selectionError = null
                                            selectedCsv = next.joinToString(",")
                                        }
                                    },
                                )
                                Text(
                                    text = tribune,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp),
                                )
                            }
                        }
                        selectionError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection("Boards") {
                    Text(
                        text = "Built-in boards mirror gb2c's default list. Custom boards are " +
                            "read and posted to directly, without gb2c.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.boards.forEach { board ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = board.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = board.backendUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!board.predefined) {
                                TextButton(onClick = { onRemoveBoard(board.name) }) {
                                    Text("Remove")
                                }
                            } else {
                                Text(
                                    text = "built-in",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { showBoardDialog = true }) {
                        Text("Add custom board")
                    }
                }
            }

            item {
                SettingsSection("Conversation filter") {
                    androidx.compose.material3.OutlinedTextField(
                        value = blocked,
                        onValueChange = { blocked = it },
                        label = { Text("Hide logins / user agents") },
                        placeholder = { Text("alice, Some bot UA") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Comma separated. Replies to hidden posts are hidden too.")
                        },
                    )
                    if (state.serverBlocked.isNotEmpty()) {
                        Text(
                            text = "Server-side filter: ${state.serverBlocked.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Filtered conversations",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = displayModeName == BlockedDisplayMode.Hide.name,
                            onClick = { displayModeName = BlockedDisplayMode.Hide.name },
                            label = { Text("Hide posts") },
                        )
                        FilterChip(
                            selected = displayModeName == BlockedDisplayMode.Gray.name,
                            onClick = { displayModeName = BlockedDisplayMode.Gray.name },
                            label = { Text("Gray posts") },
                        )
                    }
                }
            }

            item {
                SettingsSection("Plop list") {
                    androidx.compose.material3.OutlinedTextField(
                        value = plopList,
                        onValueChange = { plopList = it },
                        label = { Text("Identities to plop") },
                        placeholder = { Text("alice, bob") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Messages from these logins are replaced by a random plop word.")
                        },
                    )
                }
            }

            item {
                SettingsSection("Actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReconnect) { Text("Reconnect stream") }
                        OutlinedButton(onClick = onMarkAllRead) { Text("Mark all read") }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            runCatching {
                                uriHandler.openUri("https://codeberg.org/devnewton/gb3")
                            }
                        },
                    ) {
                        Text("About gb3")
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val ok = onSave(
                            SettingsInput(
                                serverUrl = serverUrl,
                                connectionMode = if (directMode) {
                                    ConnectionMode.DirectBoards
                                } else {
                                    ConnectionMode.Server
                                },
                                nickname = nickname,
                                selectedTribunes = selected,
                                blockedIdentities = blocked,
                                displayMode = displayModeName.toBlockedDisplayMode(),
                                plopList = plopList,
                            ),
                        )
                        if (!ok) error = "Invalid server URL or board selection"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save settings")
                }
            }
        }
    }

    if (showBoardDialog) {
        AddBoardDialog(
            onDismiss = { showBoardDialog = false },
            onAdd = { board ->
                val added = onAddBoard(board)
                if (added) showBoardDialog = false
                added
            },
        )
    }
}

@Composable
private fun AddBoardDialog(
    onDismiss: () -> Unit,
    onAdd: (Board) -> Boolean,
) {
    var name by remember { mutableStateOf("") }
    var backendUrl by remember { mutableStateOf("") }
    var postUrl by remember { mutableStateOf("") }
    var postField by remember { mutableStateOf(Board.DEFAULT_POST_FIELD) }
    var infoField by remember { mutableStateOf("") }
    var oauth by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom board") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    label = { Text("Backend TSV URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = postUrl,
                    onValueChange = { postUrl = it },
                    label = { Text("Post URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = postField,
                    onValueChange = { postField = it },
                    label = { Text("Post field") },
                    placeholder = { Text(Board.DEFAULT_POST_FIELD) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = infoField,
                    onValueChange = { infoField = it },
                    label = { Text("Info field (optional)") },
                    singleLine = true,
                    supportingText = {
                        Text("Leave empty to send the nickname in the User-Agent header, like gb2c.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = oauth,
                        onCheckedChange = { oauth = it },
                    )
                    Text(
                        text = "LinuxFR OAuth2 board",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val board = Board(
                        name = name,
                        backendUrl = backendUrl,
                        postUrl = postUrl,
                        postField = postField.ifBlank { Board.DEFAULT_POST_FIELD },
                        infoField = infoField,
                        auth = if (oauth) BoardAuth.OAuth2 else BoardAuth.None,
                    )
                    val validation = board.validationError()
                    if (validation != null) {
                        error = validation
                    } else if (onAdd(board)) {
                        error = null
                    } else {
                        error = "Could not add this board (duplicate name?)"
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun String.toBlockedDisplayMode(): BlockedDisplayMode =
    if (this == BlockedDisplayMode.Gray.name) BlockedDisplayMode.Gray else BlockedDisplayMode.Hide

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
