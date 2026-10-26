package org.zorel.gb3android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

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
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("gb2c server URL") },
                placeholder = { Text("https://gb3.example.com") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                ),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Plain http:// is supported for self-hosted servers on a LAN.")
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
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val accepted = onConnect(
                        SettingsInput(
                            serverUrl = serverUrl,
                            nickname = nickname,
                            selectedTribunes = emptySet(),
                            blockedIdentities = state.localBlocked.joinToString(","),
                            displayMode = state.displayMode,
                            plopList = state.plopList.joinToString(","),
                        ),
                    )
                    if (!accepted) error = "Please enter a valid http(s) server URL."
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
