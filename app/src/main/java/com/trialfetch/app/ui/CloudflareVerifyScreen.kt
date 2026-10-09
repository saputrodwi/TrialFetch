package com.trialfetch.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.trialfetch.app.core.Cloudflare
import com.trialfetch.app.core.CloudflareCookieStore

/**
 * Layar WebView untuk menyelesaikan challenge Cloudflare secara manual.
 *
 * UA WebView disamakan dengan UA HttpClient supaya cookie `cf_clearance`
 * yang diterbitkan Cloudflare bisa dipakai ulang oleh request OkHttp.
 *
 * Setelah user menyelesaikan challenge (menyelesaikan Turnstile atau
 * halaman redirect), cookie disalin dari CookieManager ke
 * [CloudflareCookieStore] dan request asli dijalankan ulang.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareVerifyScreen(
    url: String,
    userAgent: String,
    onDone: () -> Unit,
    onClose: () -> Unit
) {
    var pageTitle by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler { onClose() }

    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Verifikasi Cloudflare",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tutup")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClose) { Text("Batal") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    webViewRef?.let { wv ->
                        CloudflareCookieStore.syncFromWebView(wv.url ?: url)
                    }
                    onDone()
                }) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Selesai")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val hint = if (Cloudflare.isChallengeTitle(pageTitle)) {
                "Selesaikan verifikasi di halaman berikut, lalu ketuk Selesai."
            } else {
                "Verifikasi tampaknya sudah selesai — ketuk Selesai."
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        if (userAgent.isNotBlank()) {
                            settings.userAgentString = userAgent
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, u: String?) {
                                super.onPageFinished(view, u)
                                loading = false
                                pageTitle = view?.title ?: ""
                            }
                        }
                        loadUrl(url)
                        webViewRef = this
                    }
                }
            )
        }
    }
}
