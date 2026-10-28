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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zorel.gb3android.R
import org.zorel.gb3android.data.BlockedDisplayMode
import org.zorel.gb3android.data.Board
import org.zorel.gb3android.data.BoardAuth
import org.zorel.gb3android.data.BoardValidationError
import org.zorel.gb3android.data.ConnectionMode
import org.zorel.gb3android.data.TotozServer
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
    var totozServerUrl by rememberSaveable { mutableStateOf(state.totozServerUrl) }
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
    val saveErrorMessage = stringResource(R.string.settings_error_save)
    val keepOneMessage = stringResource(R.string.settings_keep_one)

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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
                SettingsSection(stringResource(R.string.settings_connection)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !directMode,
                            onClick = {
                                modeName = ConnectionMode.Server.name
                                selectedCsv = state.tribunes.joinToString(",")
                            },
                            label = { Text(stringResource(R.string.setup_mode_server)) },
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
                            label = { Text(stringResource(R.string.setup_mode_direct)) },
                        )
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = {
                            Text(
                                stringResource(
                                    if (directMode) {
                                        R.string.settings_server_url_optional
                                    } else {
                                        R.string.settings_server_url
                                    },
                                ),
                            )
                        },
                        placeholder = { Text("https://gb3.example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                stringResource(
                                    if (directMode) {
                                        R.string.settings_server_direct_support
                                    } else {
                                        R.string.settings_server_support
                                    },
                                ),
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
                                    totozServerUrl = totozServerUrl,
                                    nickname = nickname,
                                    selectedTribunes = selected,
                                    blockedIdentities = blocked,
                                    displayMode = displayModeName.toBlockedDisplayMode(),
                                    plopList = plopList,
                                ),
                            )
                            if (!ok) error = saveErrorMessage
                        }) {
                            Text(stringResource(R.string.settings_save))
                        }
                        OutlinedButton(onClick = onRefresh) {
                            Text(
                                stringResource(
                                    if (directMode) {
                                        R.string.settings_refresh_boards
                                    } else {
                                        R.string.settings_refresh_tribunes
                                    },
                                ),
                            )
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
                SettingsSection(stringResource(R.string.settings_identity)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text(stringResource(R.string.settings_nickname)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(stringResource(R.string.settings_nickname_support))
                        },
                    )
                    Text(
                        text = state.linuxfr?.let {
                            stringResource(
                                R.string.settings_linuxfr_connected,
                                it.login.ifBlank {
                                    stringResource(R.string.settings_linuxfr_unknown)
                                },
                            )
                        } ?: stringResource(R.string.settings_linuxfr_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnectLinuxfr) {
                            Text(
                                stringResource(
                                    if (state.linuxfr == null) {
                                        R.string.settings_linuxfr_connect
                                    } else {
                                        R.string.settings_linuxfr_reconnect
                                    },
                                ),
                            )
                        }
                        if (state.linuxfr != null) {
                            TextButton(onClick = onDisconnectLinuxfr) {
                                Text(stringResource(R.string.settings_linuxfr_disconnect))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_linuxfr_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_services)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = totozServerUrl,
                        onValueChange = { totozServerUrl = it },
                        label = { Text(stringResource(R.string.settings_totoz_server)) },
                        placeholder = { Text(TotozServer.DEFAULT_URL) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(stringResource(R.string.settings_totoz_server_support))
                        },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_tribunes)) {
                    if (options.isEmpty()) {
                        Text(
                            text = stringResource(
                                if (directMode) {
                                    R.string.settings_no_boards
                                } else {
                                    R.string.settings_no_tribunes
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (directMode) {
                                    R.string.settings_selected_boards
                                } else {
                                    R.string.settings_selected_tribunes
                                },
                            ),
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
                                            selectionError = keepOneMessage
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
                SettingsSection(stringResource(R.string.settings_boards)) {
                    Text(
                        text = stringResource(R.string.settings_boards_support),
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
                                    Text(stringResource(R.string.settings_remove))
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.settings_builtin),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { showBoardDialog = true }) {
                        Text(stringResource(R.string.settings_add_board))
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_conversation_filter)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = blocked,
                        onValueChange = { blocked = it },
                        label = { Text(stringResource(R.string.settings_hide_logins)) },
                        placeholder = {
                            Text(stringResource(R.string.settings_hide_logins_placeholder))
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(stringResource(R.string.settings_hide_logins_support))
                        },
                    )
                    if (state.serverBlocked.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.settings_server_filter,
                                state.serverBlocked.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_filtered_conversations),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = displayModeName == BlockedDisplayMode.Hide.name,
                            onClick = { displayModeName = BlockedDisplayMode.Hide.name },
                            label = { Text(stringResource(R.string.settings_hide_posts)) },
                        )
                        FilterChip(
                            selected = displayModeName == BlockedDisplayMode.Gray.name,
                            onClick = { displayModeName = BlockedDisplayMode.Gray.name },
                            label = { Text(stringResource(R.string.settings_gray_posts)) },
                        )
                    }
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_plop_list)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = plopList,
                        onValueChange = { plopList = it },
                        label = { Text(stringResource(R.string.settings_identities_to_plop)) },
                        placeholder = { Text(stringResource(R.string.settings_plop_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(stringResource(R.string.settings_plop_support))
                        },
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.settings_actions)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReconnect) {
                            Text(stringResource(R.string.settings_reconnect_stream))
                        }
                        OutlinedButton(onClick = onMarkAllRead) {
                            Text(stringResource(R.string.settings_mark_all_read))
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            runCatching {
                                uriHandler.openUri("https://codeberg.org/devnewton/gb3")
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_about))
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
                                totozServerUrl = totozServerUrl,
                                nickname = nickname,
                                selectedTribunes = selected,
                                blockedIdentities = blocked,
                                displayMode = displayModeName.toBlockedDisplayMode(),
                                plopList = plopList,
                            ),
                        )
                        if (!ok) error = saveErrorMessage
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_save))
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
    val validationMessages = mapOf(
        BoardValidationError.NameRequired to stringResource(R.string.board_error_name_required),
        BoardValidationError.NameTooLong to stringResource(R.string.board_error_name_too_long),
        BoardValidationError.NameInvalid to stringResource(R.string.board_error_name_invalid),
        BoardValidationError.BackendUrl to stringResource(R.string.board_error_backend_url),
        BoardValidationError.PostUrl to stringResource(R.string.board_error_post_url),
        BoardValidationError.PostField to stringResource(R.string.board_error_post_field),
        BoardValidationError.InfoField to stringResource(R.string.board_error_info_field),
    )
    val duplicateMessage = stringResource(R.string.board_duplicate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.board_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = { backendUrl = it },
                    label = { Text(stringResource(R.string.board_backend_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = postUrl,
                    onValueChange = { postUrl = it },
                    label = { Text(stringResource(R.string.board_post_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = postField,
                    onValueChange = { postField = it },
                    label = { Text(stringResource(R.string.board_post_field)) },
                    placeholder = { Text(Board.DEFAULT_POST_FIELD) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = infoField,
                    onValueChange = { infoField = it },
                    label = { Text(stringResource(R.string.board_info_field)) },
                    singleLine = true,
                    supportingText = {
                        Text(stringResource(R.string.board_info_support))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = oauth,
                        onCheckedChange = { oauth = it },
                    )
                    Text(
                        text = stringResource(R.string.board_oauth),
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
                        error = validationMessages.getValue(validation)
                    } else if (onAdd(board)) {
                        error = null
                    } else {
                        error = duplicateMessage
                    }
                },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
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
