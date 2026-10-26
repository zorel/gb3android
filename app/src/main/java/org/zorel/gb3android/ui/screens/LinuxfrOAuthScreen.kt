package org.zorel.gb3android.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.zorel.gb3android.data.LinuxfrSession
import org.zorel.gb3android.ui.UiState
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxfrOAuthScreen(
    state: UiState,
    onConnected: (LinuxfrSession) -> Unit,
    onCancel: () -> Unit,
) {
    val currentOnConnected by rememberUpdatedState(onConnected)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var tokenHandled by remember { mutableStateOf(false) }

    fun handleUrl(url: String?): Boolean {
        if (url == null || tokenHandled) return false
        val parsed = Uri.parse(url)
        if (parsed.path?.endsWith("/linuxfr.html") != true) return false
        val fragment = parsed.fragment ?: return false
        val session = runCatching {
            val json = JSONObject(String(Base64.decode(fragment, Base64.DEFAULT), Charsets.UTF_8))
            val access = json.optString("access_token")
            if (access.isBlank()) null else LinuxfrSession(
                accessToken = access,
                refreshToken = json.optString("refresh_token"),
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000,
                login = json.optString("login"),
            )
        }.getOrNull()
        if (session == null) {
            error = "The linuxfr server returned an unreadable token."
            return false
        }
        tokenHandled = true
        currentOnConnected(session)
        return true
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect LinuxFR") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Text(
                text = "Sign in on linuxfr.org. The app captures the token when the server redirects " +
                    "back to /linuxfr.html; it never sees your password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "${settings.userAgentString} gb3-android/1.0"
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (handleUrl(request.url.toString())) return true
                                return false
                            }

                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                loading = true
                                if (handleUrl(url)) view.stopLoading()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                loading = false
                                handleUrl(url)
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                webError: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    loading = false
                                    error = "Cannot reach linuxfr authorisation: ${webError.description}"
                                }
                            }
                        }
                        loadUrl("${state.serverUrl.trimEnd('/')}/gb2c/linuxfr/authorize")
                    }
                },
            )
        }
    }
}
