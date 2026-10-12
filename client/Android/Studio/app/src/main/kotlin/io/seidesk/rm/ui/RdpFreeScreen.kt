package io.seidesk.rm.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.seidesk.rm.core.ConnectionProfile
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.forRdpFree
import io.seidesk.rm.rdpfree.RdpFreeApi
import io.seidesk.rm.rdpfree.RdpFreeStatus
import io.seidesk.rm.security.CredentialVault
import io.seidesk.rm.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Native API screen, not a WebView. Browser is used only to issue a scoped device token. */
@Composable
fun RdpFreeScreen(onBack: () -> Unit, onConnect: (ConnectionProfile) -> Unit) {
    val context = LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val vault = remember { CredentialVault(context.applicationContext) }
    val api = remember { RdpFreeApi { vault.get(RdpFreeApi.VAULT_KEY) } }
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(vault.has(RdpFreeApi.VAULT_KEY)) }
    var tokenInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var microphone by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<RdpFreeStatus?>(null) }
    var inputs by remember { mutableStateOf<JSONObject?>(null) }
    var durations by remember { mutableStateOf<List<String>>(emptyList()) }
    var duration by remember { mutableStateOf("360") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var chooseDuration by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var stopRunId by remember { mutableStateOf<Long?>(null) }

    fun openBrowser(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { message = "Browser tidak tersedia" }
    }

    suspend fun load() {
        status = api.status()
        if (inputs == null) {
            val config = api.settings()
            inputs = config.getJSONObject("inputs")
            duration = inputs?.optString("durasi_menit", "360") ?: "360"
            val options = config.getJSONObject("choices").getJSONArray("durasi_menit")
            durations = (0 until options.length()).map { options.getString(it) }
        }
    }

    fun error(e: Exception) {
        if (e is CancellationException) throw e
        message = e.message ?: seiNow("RdpFree connection failed. Try again.")
        status = null // Never connect using a stale address after an API error.
        if (e is RdpFreeApi.ApiException && e.status == 401) {
            vault.remove(RdpFreeApi.VAULT_KEY)
            connected = false
            inputs = null
            password = ""
            message += seiNow(" Open repository guide, then create a new device token.")
        }
    }

    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        while (true) {
            var wait = 15000L
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !busy) {
                try { load() } catch (e: Exception) { error(e); wait = 30000L }
            }
            delay(wait)
        }
    }

    fun operate(action: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                when (action) {
                    "start" -> { api.start(); message = seiNow("Workflow triggered. Awaiting next status update.") }
                    "stop" -> { api.stop(stopRunId ?: throw IllegalStateException(seiNow("Run is unavailable"))); message = seiNow("Stop request submitted.") }
                    "replace" -> {
                        vault.remove(RdpFreeApi.VAULT_KEY)
                        connected = false
                        status = null
                        inputs = null
                        password = ""
                        microphone = false
                        message = seiNow("Local token removed. Token is not revoked on server; manage via SeiDesk web menu. Paste a new token for the selected repo.")
                    }
                    "revoke" -> {
                        api.revoke()
                        vault.remove(RdpFreeApi.VAULT_KEY)
                        connected = false
                        status = null
                        inputs = null
                        password = ""
                        message = seiNow("Device access revoked.")
                    }
                }
                if (action != "revoke" && action != "replace") load()
            } catch (e: Exception) { error(e) } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SeiTopBar(title = sei("RdpFree"), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(sei("RdpFree · native control"), style = MaterialTheme.typography.titleLarge)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            if (!connected) {
                SeiCard(Modifier.fillMaxWidth()) {
                    Text(sei("Connect repository, then link application"), style = MaterialTheme.typography.titleMedium)
                    RdpFreeApi.ONBOARDING_STEPS.forEachIndexed { index, step ->
                        Spacer(Modifier.height(8.dp))
                        Text("${index + 1}. $step")
                    }
                    Text(sei("Installing the GitHub App grants repository permissions on GitHub, not installing an APK. Login alone does not grant repo access."))
                    Spacer(Modifier.height(12.dp))
                    SeiPillButton(sei("Start / continue repository guide"), { openBrowser(RdpFreeApi.CONNECTION_URL) }, primary = true)
                    Spacer(Modifier.height(12.dp))
                    Text(sei("Completed setup? Create token in SeiDesk website menu. Do not enter admin password or GitHub token."))
                    Spacer(Modifier.height(12.dp))
                    SeiPillButton(sei("Open tokens page"), {
                        openBrowser(RdpFreeApi.DEVICES_URL)
                    }, primary = false)
                    Spacer(Modifier.height(12.dp))
                    SeiField(tokenInput, { tokenInput = it.take(100) }, sei("Device token"), isPassword = true)
                    Spacer(Modifier.height(12.dp))
                    SeiPillButton(sei("Connect"), {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val candidate = tokenInput.trim()
                                    RdpFreeApi { candidate }.settings() // Validate before replacing saved token.
                                    val saved = withContext(Dispatchers.IO) { vault.put(RdpFreeApi.VAULT_KEY, candidate) }
                                    check(saved) { seiNow("Android Keystore could not save token") }
                                    tokenInput = ""
                                    inputs = null
                                    connected = true
                                    message = seiNow("Device connected.")
                                } catch (e: Exception) { error(e) } finally { busy = false }
                            }
                        }
                    }, enabled = !busy && RdpFreeApi.TOKEN_PATTERN.matches(tokenInput.trim()))
                }
            } else {
                SeiCard(Modifier.fillMaxWidth()) {
                    Text(status?.repo ?: seiNow("Loading repository…"), style = MaterialTheme.typography.titleMedium)
                    Text(sei("Status: {0}", status?.phase ?: seiNow("waiting")))
                    status?.expiresAt?.let { Text(sei("Expires (UTC): {0}", it)) }
                    Spacer(Modifier.height(12.dp))
                    SeiPillButton(sei("Duration: {0} min", duration), { chooseDuration = true }, primary = false,
                        enabled = !busy && durations.isNotEmpty())
                    Spacer(Modifier.height(8.dp))
                    SeiPillButton(sei("Save duration"), {
                        busy = true
                        scope.launch {
                            try {
                                val updated = JSONObject(api.settings().getJSONObject("inputs").toString())
                                updated.put("durasi_menit", duration)
                                api.saveSettings(updated)
                                inputs = updated
                                message = seiNow("Duration saved for next run.")
                            } catch (e: Exception) { error(e) } finally { busy = false }
                        }
                    }, primary = false, enabled = !busy && inputs != null)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeiPillButton(sei("Run"), { confirmAction = "start" }, enabled = !busy && status != null && status?.active == false)
                        SeiPillButton(sei("Stop"), { stopRunId = status?.runId; confirmAction = "stop" }, primary = false,
                            enabled = !busy && status?.active == true && status?.runId != null)
                    }
                    Spacer(Modifier.height(8.dp))
                    SeiPillButton(sei("Refresh"), {
                        busy = true
                        scope.launch { try { load() } catch (e: Exception) { error(e) } finally { busy = false } }
                    }, primary = false, enabled = !busy)
                }
                status?.connection?.let { connection ->
                    SeiCard(Modifier.fillMaxWidth()) {
                        Text("${connection.host}:${connection.port}", style = MaterialTheme.typography.titleMedium)
                        Text("${connection.username} · ${connection.transport}")
                        if (connection.transport == "tailscale") Text(sei("Ensure Tailscale on this device is connected to the same network."))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sei("Microphone input"), modifier = Modifier.weight(1f))
                            Switch(checked = microphone, onCheckedChange = { microphone = it }, enabled = !busy)
                        }
                        Text(sei("Optional: Android requests microphone permission when connected. Test Windows Sound > Input, not just speakers. Desktop wallpaper active."))
                        Spacer(Modifier.height(12.dp))
                        SeiField(password, { password = it }, sei("Password RDP_PASSWORD"), isPassword = true)
                        Text(sei("Password is not read from GitHub and is not saved automatically."))
                        Spacer(Modifier.height(12.dp))
                        SeiPillButton(sei("Connect RDP"), {
                            busy = true
                            scope.launch {
                                try {
                                    val fresh = api.status().connection ?: throw IllegalStateException(seiNow("Session is no longer ready"))
                                    val profile = ConnectionProfile(host = fresh.host, port = fresh.port,
                                        username = fresh.username, password = password, label = "RdpFree",
                                        key = "rdpfree:${status?.repo ?: fresh.host}")
                                    check(fresh.transport == "tailscale") { seiNow("Switch session to Tailscale first") }
                                    RdpOptions.of(context, profile.id).forRdpFree(microphone).write(context, profile.id)
                                    password = ""
                                    onConnect(profile)
                                } catch (e: Exception) { error(e) } finally { busy = false }
                            }
                        }, enabled = !busy && password.isNotBlank())
                    }
                }
                SeiCard(Modifier.fillMaxWidth()) {
                    Text(sei("Manage repository"), style = MaterialTheme.typography.titleMedium)
                    Text(sei("This token remains bound to the repo when created. If you change repos, create a new token. Delete repo only through owner browser with confirmation, not via app token."))
                    Spacer(Modifier.height(12.dp))
                    SeiPillButton(sei("Guide / select / remove repo"), { openBrowser(RdpFreeApi.CONNECTION_URL) }, primary = false)
                    Spacer(Modifier.height(8.dp))
                    SeiPillButton(sei("Setup Tailscale & password"), { openBrowser(RdpFreeApi.SETUP_URL) }, primary = false)
                    Spacer(Modifier.height(8.dp))
                    SeiPillButton(sei("Change device token"), { confirmAction = "replace" }, primary = false, enabled = !busy)
                }
                SeiPillButton(sei("Revoke device access"), { confirmAction = "revoke" }, primary = false, enabled = !busy)
            }
            Text("Credit: KallAncrit", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (chooseDuration) {
        SeiOverlay(title = sei("Session duration"), onDismiss = { chooseDuration = false }) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                durations.forEach { minutes ->
                    SeiPillButton(sei("{0} min", minutes), { duration = minutes; chooseDuration = false },
                        primary = duration == minutes, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    confirmAction?.let { action ->
        SeiDialog(
            title = when (action) {
                "start" -> sei("Start RDP?")
                "stop" -> sei("Stop RDP?")
                "replace" -> sei("Change device token?")
                else -> sei("Revoke device token?")
            },
            body = when (action) {
                "start" -> sei("Uses saved settings and GitHub Actions quota. Do not resend if connection drops; check status first.")
                "stop" -> sei("Active session will be terminated. Save your work first.")
                "replace" -> sei("Only removes token from this phone. Does not delete repo, does not stop session, and does not revoke token on server. Create a new token after selecting repo on website.")
                else -> sei("This app cannot control the repository until a new token is configured.")
            },
            confirmLabel = sei("Continue"), onConfirm = { confirmAction = null; operate(action) },
            dismissLabel = sei("Cancel"), onDismiss = { confirmAction = null },
        )
    }
}
