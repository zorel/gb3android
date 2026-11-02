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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.zorel.gb3android.R
import org.zorel.gb3android.data.Gb2cServer
import org.zorel.gb3android.data.TotozServer
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
    val initialServer = state.gb2cServers.firstOrNull { it.enabled }
        ?: state.gb2cServers.firstOrNull()
    var serverName by rememberSaveable { mutableStateOf(initialServer?.name.orEmpty()) }
    var serverUrl by rememberSaveable { mutableStateOf(initialServer?.url.orEmpty()) }
    var totozServerUrl by rememberSaveable { mutableStateOf(state.totozServerUrl) }
    var nickname by rememberSaveable { mutableStateOf(state.nickname) }
    var selectedBoardsCsv by rememberSaveable {
        mutableStateOf(state.selectedBoards.joinToString(","))
    }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val selectedBoards = selectedBoardsCsv.csvSet()
    val noSourceError = stringResource(R.string.setup_error_no_source)
    val serverError = stringResource(R.string.setup_error_server)

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
                text = stringResource(R.string.app_name),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.setup_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = palette.muted,
            )
            Spacer(Modifier.height(36.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.setup_server_url_optional)) },
                placeholder = { Text("https://gb3.example.com") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(stringResource(R.string.setup_server_optional_support))
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text(stringResource(R.string.settings_server_name)) },
                placeholder = { Text(stringResource(R.string.settings_server_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = totozServerUrl,
                onValueChange = { totozServerUrl = it },
                label = { Text(stringResource(R.string.settings_totoz_server)) },
                placeholder = { Text(TotozServer.DEFAULT_URL) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(stringResource(R.string.settings_totoz_server_support))
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(stringResource(R.string.setup_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.setup_nickname_support)) },
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.setup_boards),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.setup_boards_support),
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
                            selectedBoardsCsv = next.sorted().joinToString(",")
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
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val typed = if (serverUrl.isBlank()) {
                        null
                    } else {
                        Gb2cServer(serverName, serverUrl, enabled = true).normalized()
                    }
                    if (typed != null && typed.validationError() != null) {
                        error = serverError
                        return@Button
                    }
                    val servers = buildList {
                        addAll(state.gb2cServers.filter { it.url != initialServer?.url })
                        if (typed != null) add(typed)
                    }
                    if (servers.none { it.enabled } && selectedBoards.isEmpty()) {
                        error = noSourceError
                        return@Button
                    }
                    val accepted = onConnect(
                        SettingsInput(
                            gb2cServers = servers,
                            selectedBoards = selectedBoards,
                            selectedGb2cTribunes = state.selectedGb2cTribunes,
                            totozServerUrl = totozServerUrl,
                            nickname = nickname,
                            blockedIdentities = state.localBlocked.joinToString(","),
                            displayMode = state.displayMode,
                            plopList = state.plopList.joinToString(","),
                        ),
                    )
                    if (!accepted) error = serverError
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.setup_connect))
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
                text = stringResource(R.string.setup_footer),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
