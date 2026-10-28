package org.zorel.gb3android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zorel.gb3android.data.ConnectionMode
import org.zorel.gb3android.data.csvSet
import org.zorel.gb3android.ui.SettingsInput
import org.zorel.gb3android.ui.UiState
import org.zorel.gb3android.ui.theme.LocalGb3Palette

@Composable
fun SetupScreen(
    state: UiState,
    onConnect: (SettingsInput) -> Boolean,
) {
    val palette = LocalGb3Palette.current
    var serverUrl by rememberSaveable { mutableStateOf(state.serverUrl) }
    var nickname by rememberSaveable { mutableStateOf(state.nickname) }
    var modeName by rememberSaveable { mutableStateOf(state.connectionMode.name) }
    var directSelectionCsv by rememberSaveable {
        mutableStateOf(state.tribunes.joinToString(","))
    }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val directMode = modeName == ConnectionMode.DirectBoards.name
    val selectedBoards = directSelectionCsv.csvSet()

    LaunchedEffect(state.boards, directMode) {
        if (directMode && selectedBoards.isEmpty() && state.boards.isNotEmpty()) {
            directSelectionCsv = state.boards.map { it.name }.joinToString(",")
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "gb3",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Tribune and mouling agent",
                style = MaterialTheme.typography.titleMedium,
                color = palette.muted,
            )
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !directMode,
                    onClick = { modeName = ConnectionMode.Server.name },
                    label = { Text("gb2c server") },
                )
                FilterChip(
                    selected = directMode,
                    onClick = { modeName = ConnectionMode.DirectBoards.name },
                    label = { Text("Direct boards") },
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = {
                    Text(if (directMode) "gb2c server URL (optional)" else "gb2c server URL")
                },
                placeholder = { Text("https://gb3.example.com") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        if (directMode) {
                            "Only used for totoz, emoji, attachments and LinuxFR login. " +
                                "Posts are sent straight to the selected boards."
                        } else {
                            "Plain http:// is supported for self-hosted servers on a LAN."
                        },
                    )
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Nickname (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("This is the info sent with your posts.") },
            )
            if (directMode) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Boards",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Selected boards are downloaded and posted to directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.boards.forEach { board ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = board.name in selectedBoards,
                            onCheckedChange = { checked ->
                                val next = if (checked) {
                                    selectedBoards + board.name
                                } else {
                                    selectedBoards - board.name
                                }
                                directSelectionCsv = next.sorted().joinToString(",")
                                if (next.isNotEmpty()) error = null
                            },
                        )
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (directMode && selectedBoards.isEmpty()) {
                        error = "Select at least one board."
                        return@Button
                    }
                    val accepted = onConnect(
                        SettingsInput(
                            serverUrl = serverUrl,
                            connectionMode = if (directMode) {
                                ConnectionMode.DirectBoards
                            } else {
                                ConnectionMode.Server
                            },
                            nickname = nickname,
                            selectedTribunes = if (directMode) selectedBoards else emptySet(),
                            blockedIdentities = state.localBlocked.joinToString(","),
                            displayMode = state.displayMode,
                            plopList = state.plopList.joinToString(","),
                        ),
                    )
                    if (!accepted) {
                        error = if (directMode) {
                            "Check the optional server URL."
                        } else {
                            "Please enter a valid http(s) server URL."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "gb3 is a KISS bouchot stack. Posts stream live while the app is in the foreground. " +
                    "All settings stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
