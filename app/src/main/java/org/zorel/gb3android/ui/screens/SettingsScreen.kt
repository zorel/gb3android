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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import org.zorel.gb3android.data.BlockedDisplayMode
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
) {
    var serverUrl by rememberSaveable { mutableStateOf(state.serverUrl) }
    var nickname by rememberSaveable { mutableStateOf(state.nickname) }
    var selectedCsv by rememberSaveable { mutableStateOf(state.tribunes.joinToString(",")) }
    var blocked by rememberSaveable { mutableStateOf(state.localBlocked.joinToString(",")) }
    var displayModeName by rememberSaveable { mutableStateOf(state.displayMode.name) }
    var plopList by rememberSaveable { mutableStateOf(state.plopList.joinToString(",")) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.tribunes) {
        selectedCsv = state.tribunes.joinToString(",")
    }

    val options = if (state.availableTribunes.isNotEmpty()) {
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
                SettingsSection("Server") {
                    androidx.compose.material3.OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("gb2c URL") },
                        placeholder = { Text("https://gb3.example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Changing the server reloads tribunes and conversation filters.")
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val ok = onSave(
                                SettingsInput(
                                    serverUrl = serverUrl,
                                    nickname = nickname,
                                    selectedTribunes = selected,
                                    blockedIdentities = blocked,
                                    displayMode = displayModeName.toBlockedDisplayMode(),
                                    plopList = plopList,
                                ),
                            )
                            if (!ok) error = "Invalid server URL"
                        }) {
                            Text("Save settings")
                        }
                        OutlinedButton(onClick = onRefresh) {
                            Text("Refresh tribunes")
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
                            text = "No tribunes loaded yet. Save a valid server URL and refresh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Selected tribunes are streamed live. Keep at least one.",
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
                                nickname = nickname,
                                selectedTribunes = selected,
                                blockedIdentities = blocked,
                                displayMode = displayModeName.toBlockedDisplayMode(),
                                plopList = plopList,
                            ),
                        )
                        if (!ok) error = "Invalid server URL"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save settings")
                }
            }
        }
    }
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
