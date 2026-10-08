package com.sift.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sift.app.data.CategorySignals
import com.sift.app.data.LoginRequest
import com.sift.app.data.PinOutcome
import com.sift.app.data.PriceBlock
import com.sift.app.data.WatchlistResult
import com.sift.app.data.productIdFor
import com.sift.app.lib.parseShareText
import com.sift.app.ui.HomeScreen
import com.sift.app.ui.LoginScreen
import com.sift.app.ui.ShareFlowScreen
import kotlinx.coroutines.launch

/**
 * Single activity. Routes by state (no nav library in v1):
 * - shared text present -> share/confirm flow (requires login first)
 * - signed in -> home placeholder
 * - guest -> login
 *
 * The shared URL is parsed locally and NEVER fetched (no-scraping rule).
 */
class MainActivity : ComponentActivity() {

    private fun incomingSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedText = incomingSharedText(intent)

        setContent {
            val container = (application as SiftApp).container
            val scope = rememberCoroutineScope()
            val token by container.authStore.tokenFlow.collectAsState()

            var username by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var loginLoading by remember { mutableStateOf(false) }
            var loginError by remember { mutableStateOf<String?>(null) }
            var shareConsumed by remember { mutableStateOf(false) }

            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Sift") }) },
                ) { inner ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                    ) {
                        val signedIn = !token.isNullOrBlank()
                        val activeShare =
                            if (!shareConsumed) sharedText else null

                        when {
                            activeShare != null && signedIn -> {
                                val parsed = remember(activeShare) { parseShareText(activeShare) }
                                var pinning by remember { mutableStateOf(false) }
                                var outcome by remember { mutableStateOf<PinOutcome?>(null) }
                                ShareFlowScreen(
                                    parsed = parsed,
                                    prefillPrice = "",
                                    pinning = pinning,
                                    outcome = outcome,
                                    onPin = { storeName, name, price ->
                                        scope.launch {
                                            pinning = true
                                            outcome = container.watchlistRepository.pin(
                                                WatchlistResult(
                                                    id = productIdFor(
                                                        storeName,
                                                        activeShare.ifBlank { name },
                                                    ),
                                                    name = name,
                                                    store = storeName,
                                                    productUrl = parsed.url ?: "",
                                                    prices = price?.let { PriceBlock(normal = it) },
                                                    categorySignals = CategorySignals(
                                                        title = name,
                                                        store = storeName,
                                                    ),
                                                ),
                                            )
                                            pinning = false
                                        }
                                    },
                                    onDone = { shareConsumed = true },
                                )
                            }
                            signedIn -> {
                                HomeScreen(
                                    username = username.ifBlank { "you" },
                                    onSignOut = {
                                        scope.launch { container.authStore.clear() }
                                        shareConsumed = false
                                    },
                                )
                            }
                            else -> {
                                if (activeShare != null) {
                                    // Remember: after login, continue into the share flow.
                                    LaunchedEffect(signedIn) { /* state recomposes automatically */ }
                                }
                                LoginScreen(
                                    username = username,
                                    password = password,
                                    loading = loginLoading,
                                    error = loginError,
                                    onUsernameChange = { username = it },
                                    onPasswordChange = { password = it },
                                    onLogin = {
                                        scope.launch {
                                            loginLoading = true
                                            loginError = null
                                            try {
                                                val resp = container.api.login(
                                                    LoginRequest(username.trim(), password),
                                                )
                                                username = resp.user.username
                                                container.authStore.saveToken(resp.token)
                                            } catch (e: Exception) {
                                                loginError = "Sign in failed — check your details."
                                            } finally {
                                                loginLoading = false
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: a second share while open restarts the flow cleanly.
        setIntent(intent)
        recreate()
    }
}
