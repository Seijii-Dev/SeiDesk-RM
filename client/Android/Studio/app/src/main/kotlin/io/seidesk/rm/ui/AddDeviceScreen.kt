package io.seidesk.rm.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seidesk.rm.core.ConnectionProfile
import io.seidesk.rm.core.DiscoveredPcHost
import io.seidesk.rm.core.LanScanner
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.SeiAudioMode
import io.seidesk.rm.core.SeiGateway
import io.seidesk.rm.core.SeiGpuProfile
import io.seidesk.rm.core.SeiSecurityProtocol
import io.seidesk.rm.core.SeiStreamProfile
import io.seidesk.rm.core.WakeOnLan
import io.seidesk.rm.core.WireNames
import io.seidesk.rm.core.formatRdpEndpoint
import io.seidesk.rm.core.parsePcIdOrEndpoint
import io.seidesk.rm.core.parseRdpEndpoint
import io.seidesk.rm.security.CredentialVault
import io.seidesk.rm.ui.components.SeiCard
import io.seidesk.rm.ui.components.SeiDialog
import io.seidesk.rm.ui.components.SeiField
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiOverlay
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.components.SeiTopBar
import io.seidesk.rm.ui.device.DeviceAudioSection
import io.seidesk.rm.ui.device.DeviceDisplaySection
import io.seidesk.rm.ui.device.DeviceGatewaySection
import io.seidesk.rm.ui.device.DeviceSecuritySection
import io.seidesk.rm.ui.device.DeviceStreamingSection
import io.seidesk.rm.ui.device.DeviceWolSection
import io.seidesk.rm.ui.device.SeiAccordionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SavedAccountOption(
    val username: String,
    val domain: String,
    val password: String,
)

private sealed interface ConnectionTestState {
    object Idle : ConnectionTestState
    object Testing : ConnectionTestState
    data class Online(val latencyMs: Long) : ConnectionTestState
    object Unreachable : ConnectionTestState
}

private fun formatPcIdInput(input: String): String {
    val clean = input.trim()
    if (WireNames.isPcId(clean)) return clean
    val digits = clean.filter(Char::isDigit)
    return when {
        digits.length in 7..10 -> buildString {
            append(digits.take(3))
            append('-')
            append(digits.drop(3).take(3))
            if (digits.length > 6) {
                append('-')
                append(digits.drop(6).take(4))
            }
        }
        else -> input
    }
}

@Composable
fun AddDeviceScreen(
    existing: ConnectionProfile?,
    savedUsers: List<String>,
    savedProfiles: List<ConnectionProfile> = emptyList(),
    initialPcQuickMode: Boolean = false,
    onCancel: () -> Unit,
    onSubmit: (profile: ConnectionProfile, rememberPassword: Boolean, connect: Boolean, onComplete: (Result<Boolean>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { CredentialVault(context.applicationContext) }

    val deviceKey = remember(existing) {
        existing?.key ?: existing?.id ?: java.util.UUID.randomUUID().toString()
    }
    val deviceId = deviceKey
    val existingStored = remember(deviceId, existing) {
        if (existing != null) RdpOptions.of(context, deviceId) else null
    }

    var isPcQuickMode by remember(existing, initialPcQuickMode) {
        mutableStateOf(
            if (existing != null) existingStored?.pcConnectMode == true
            else initialPcQuickMode,
        )
    }

    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var host by remember(existing) {
        mutableStateOf(existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty())
    }
    var portText by remember(existing) {
        mutableStateOf((existing?.port ?: 3389).toString())
    }
    var user by remember {
        mutableStateOf(existing?.username.orEmpty())
    }
    var pass by remember {
        mutableStateOf(
            existing?.password
                ?: existing?.let {
                    vault.get(it.id)
                        ?: vault.get(CredentialVault.hostCredKey(it.host, it.port, it.username.orEmpty()))
                }.orEmpty(),
        )
    }
    var domain by remember { mutableStateOf(existing?.domain.orEmpty()) }
    var rememberPass by remember { mutableStateOf(existing?.password?.isNotEmpty() ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var validationPopup by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Test connection state
    var testState by remember { mutableStateOf<ConnectionTestState>(ConnectionTestState.Idle) }

    // Accordion expansion states
    var displayExpanded by remember { mutableStateOf(false) }
    var audioExpanded by remember { mutableStateOf(false) }
    var streamingExpanded by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }
    var wolExpanded by remember { mutableStateOf(false) }
    var gatewayExpanded by remember { mutableStateOf(false) }

    // Auto-fill password when host+user is recognized
    LaunchedEffect(host, user) {
        if (pass.isNotEmpty()) return@LaunchedEffect
        val ep = parseRdpEndpoint(host) ?: return@LaunchedEffect
        val stored = vault.get(CredentialVault.hostCredKey(ep.host, ep.port, user))
        if (!stored.isNullOrEmpty()) {
            pass = stored
            rememberPass = true
        }
    }

    // Accounts management
    val baseAccounts = remember(savedProfiles, savedUsers, existing) {
        val list = mutableListOf<SavedAccountOption>()
        savedProfiles.forEach { prof ->
            val u = prof.username?.trim().orEmpty()
            if (u.isNotEmpty() && !u.equals(WireNames.ACCOUNT_MULTI_USER, ignoreCase = true)) {
                val p = prof.password ?: vault.get(prof.id).orEmpty()
                val d = prof.domain?.trim().orEmpty()
                if (list.none { it.username.equals(u, ignoreCase = true) && it.domain.equals(d, ignoreCase = true) }) {
                    list.add(SavedAccountOption(u, d, p))
                }
            }
        }
        savedUsers.forEach { u ->
            val clean = u.trim()
            if (clean.isNotEmpty() && !clean.equals(WireNames.ACCOUNT_MULTI_USER, ignoreCase = true) &&
                list.none { it.username.equals(clean, ignoreCase = true) }
            ) {
                list.add(SavedAccountOption(clean, "", ""))
            }
        }
        list
    }
    var extraAccounts by remember { mutableStateOf<List<SavedAccountOption>>(emptyList()) }
    val allAccounts = remember(baseAccounts, extraAccounts) {
        (extraAccounts + baseAccounts).distinctBy { "${it.domain.lowercase()}\\${it.username.lowercase()}" }
    }
    var accountDropdownOpen by remember { mutableStateOf(false) }
    var accountSearch by remember { mutableStateOf("") }
    val filteredAccounts = remember(allAccounts, accountSearch) {
        val query = accountSearch.trim()
        if (query.isEmpty()) allAccounts else allAccounts.filter { account ->
            "${account.domain} ${account.username}".contains(query, ignoreCase = true)
        }
    }

    // RdpOptions stored & active
    val stored = remember(deviceId, existing, initialPcQuickMode) {
        if (existingStored != null) {
            existingStored
        } else {
            val app = AppPrefs(context)
            val baseProfile = if (initialPcQuickMode) {
                SeiStreamProfile.ULTRA_LOW_LATENCY
            } else {
                SeiStreamProfile.entries.getOrElse(app.defaultStreamProfile) { SeiStreamProfile.AUTO }
            }
            val baseSecProto = if (initialPcQuickMode) {
                SeiSecurityProtocol.NLA
            } else {
                SeiSecurityProtocol.entries.getOrElse(app.defaultSecurityProtocol) { SeiSecurityProtocol.AUTO }
            }
            RdpOptions(
                pcConnectMode = initialPcQuickMode,
                clipboard = app.defaultClipboard,
                localDrive = if (initialPcQuickMode) false else app.defaultLocalDrive,
                udpTransport = if (initialPcQuickMode) true else app.defaultUdp,
                networkAutoDetect = app.defaultNetAuto,
                h264 = true,
                dynamicResolution = if (initialPcQuickMode) false else app.defaultDynamicResolution,
                asyncUpdate = if (initialPcQuickMode) true else app.defaultAsyncUpdate,
                asyncChannels = if (initialPcQuickMode) true else app.defaultAsyncChannels,
                securityProtocol = baseSecProto,
                tlsSecLevel = if (initialPcQuickMode) 2 else app.defaultTlsSecLevel,
            ).withStreamProfile(baseProfile).copy(
                pcConnectMode = initialPcQuickMode,
                udpTransport = if (initialPcQuickMode) true else app.defaultUdp,
                networkAutoDetect = app.defaultNetAuto,
                h264 = true,
                dynamicResolution = if (initialPcQuickMode) false else app.defaultDynamicResolution,
                asyncUpdate = if (initialPcQuickMode) true else app.defaultAsyncUpdate,
                asyncChannels = if (initialPcQuickMode) true else app.defaultAsyncChannels,
                securityProtocol = baseSecProto,
                tlsSecLevel = if (initialPcQuickMode) 2 else app.defaultTlsSecLevel,
            )
        }
    }
    var options by remember { mutableStateOf(stored) }
    var gatewayOn by remember { mutableStateOf(stored.gateway != null) }
    var gwHost by remember { mutableStateOf(stored.gateway?.host.orEmpty()) }
    var gwPort by remember { mutableStateOf((stored.gateway?.port ?: 443).toString()) }
    var gwUser by remember { mutableStateOf(stored.gateway?.username.orEmpty()) }
    var gwPass by remember { mutableStateOf(stored.gateway?.password.orEmpty()) }
    var gwDomain by remember { mutableStateOf(stored.gateway?.domain.orEmpty()) }

    var resolutionIndex by remember(deviceId) {
        mutableIntStateOf(
            DisplayPrefs.resolutionOptions.indexOfFirst {
                it.value == DisplayPrefs.resolution(context, deviceId)
            }.coerceAtLeast(0)
        )
    }
    var rotationIndex by remember(deviceId) {
        mutableIntStateOf(
            DisplayPrefs.rotations.indexOf(DisplayPrefs.rotation(context, deviceId)).coerceAtLeast(0)
        )
    }
    var dpi by remember(deviceId) {
        mutableIntStateOf(DisplayPrefs.dpi(context, deviceId))
    }
    var audioIndex by remember { mutableIntStateOf(options.audioMode.ordinal) }

    // LAN Scanner modal
    var lanScanModalOpen by remember { mutableStateOf(false) }
    var lanScanning by remember { mutableStateOf(false) }
    var lanFoundHosts by remember { mutableStateOf<List<DiscoveredPcHost>>(emptyList()) }
    var lanSelfIp by remember { mutableStateOf<String?>(null) }

    fun triggerLanScan() {
        lanScanModalOpen = true
        if (lanScanning) return
        lanScanning = true
        scope.launch {
            val localIp = withContext(Dispatchers.IO) { LanScanner.detectLocalIpv4Address() }
            lanSelfIp = localIp
            val hosts = withContext(Dispatchers.IO) { LanScanner.scanLocalSubnet() }
            lanFoundHosts = hosts
            lanScanning = false
        }
    }

    val initialPass = remember(existing) { pass }
    val initialPortText = remember(existing) { (existing?.port ?: 3389).toString() }
    val initialHost = remember(existing) {
        existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty()
    }
    val initialLabel = remember(existing) { existing?.label.orEmpty().trim() }
    val initialUser = remember(existing) { existing?.username.orEmpty().trim() }
    val initialDomain = remember(existing) { existing?.domain.orEmpty().trim() }
    val initialPcMode = remember(existing, initialPcQuickMode) {
        if (existing != null) existingStored?.pcConnectMode == true
        else initialPcQuickMode
    }
    val initialRememberPass = remember(existing) { existing?.password?.isNotEmpty() ?: true }

    val hasChanges = if (existing == null) {
        host.isNotBlank() || label.isNotBlank() || user.isNotBlank() || pass.isNotBlank() || domain.isNotBlank() || (portText.trim() != "3389" && portText.isNotBlank())
    } else {
        host.trim() != initialHost.trim() ||
            portText.trim() != initialPortText ||
            label.trim() != initialLabel ||
            user.trim() != initialUser ||
            pass != initialPass ||
            domain.trim() != initialDomain ||
            isPcQuickMode != initialPcMode ||
            rememberPass != initialRememberPass ||
            options != stored ||
            gatewayOn != (stored.gateway != null) ||
            (gatewayOn && (gwHost.trim() != stored.gateway?.host.orEmpty() || gwPort.trim() != (stored.gateway?.port ?: 443).toString() || gwUser.trim() != stored.gateway?.username.orEmpty() || gwPass != stored.gateway?.password.orEmpty() || gwDomain.trim() != stored.gateway?.domain.orEmpty()))
    }

    fun requestCancel() {
        if (confirmDiscard || !hasChanges) {
            confirmDiscard = false
            onCancel()
        } else {
            confirmDiscard = true
        }
    }

    BackHandler {
        when {
            accountDropdownOpen -> {
                accountDropdownOpen = false
                accountSearch = ""
            }
            validationPopup != null -> validationPopup = null
            confirmDiscard -> confirmDiscard = false
            else -> requestCancel()
        }
    }

    fun testConnection() {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) {
            error = seiNow("Enter host or PC ID to test connection.")
            return
        }
        val ep = parsePcIdOrEndpoint(cleanHost, portText.toIntOrNull() ?: 3389)
        if (ep == null) {
            error = seiNow("Invalid host or PC ID.")
            return
        }
        error = null
        testState = ConnectionTestState.Testing
        scope.launch {
            val res = ReachabilityProbe.check(ep.host, ep.port)
            testState = if (res.online) ConnectionTestState.Online(res.latencyMs) else ConnectionTestState.Unreachable
        }
    }

    fun submit(connect: Boolean) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) {
            val msg = if (isPcQuickMode) seiNow("PC ID or host address is required.")
            else seiNow("Host address is required.")
            error = msg
            validationPopup = msg
            return
        }

        val parsedPort = portText.trim().toIntOrNull()
        if (parsedPort == null || parsedPort !in 1..65535) {
            val msg = seiNow("Port must be a valid number between 1 and 65535.")
            error = msg
            validationPopup = msg
            return
        }

        val endpoint = parsePcIdOrEndpoint(cleanHost, parsedPort)
        if (endpoint == null) {
            val msg = seiNow("Invalid PC ID or address/port. Enter a 10-digit PC ID (e.g. 323-223-5826) or host:port.")
            error = msg
            validationPopup = msg
            return
        }

        if (gatewayOn && gwHost.isBlank()) {
            val msg = seiNow("RDP Gateway is enabled, but the Gateway host address is empty.")
            error = msg
            validationPopup = msg
            return
        }

        if (!options.macAddress.isNullOrBlank() && WakeOnLan.parseMacBytes(options.macAddress) == null) {
            val msg = seiNow("Invalid Wake-on-LAN MAC address format. Use 6 hex pairs like AA:BB:CC:DD:EE:FF.")
            error = msg
            validationPopup = msg
            return
        }

        val effectiveUser = if (isPcQuickMode) {
            if (pass.isNotBlank()) WireNames.ACCOUNT_MULTI_USER else null
        } else {
            user.trim().ifEmpty { null }
        }
        val effectiveDomain = if (isPcQuickMode) null else domain.trim().ifEmpty { null }

        val profile = try {
            ConnectionProfile(
                host = endpoint.host,
                port = endpoint.port,
                username = effectiveUser,
                password = pass.ifEmpty { null },
                domain = effectiveDomain,
                label = label.trim().ifEmpty { null },
                key = deviceKey,
            )
        } catch (e: IllegalArgumentException) {
            error = e.message
            validationPopup = e.message
            return
        }

        error = null
        val finalOptions = if (isPcQuickMode) {
            options.withPcStreamEngine(options.pcStreamEngine).copy(
                pcConnectMode = true,
                pcTargetFps = options.pcTargetFps,
                pcBitrateMbps = options.pcBitrateMbps,
                rawInputMouse = options.rawInputMouse,
                udpTransport = options.udpTransport,
                clipboard = options.clipboard,
                batterySaver = options.batterySaver,
                audioMode = SeiAudioMode.entries[audioIndex],
                microphone = false,
                localDrive = false,
                camera = false,
                dynamicResolution = false,
                consoleAdmin = true,
                restrictedAdmin = false,
                remoteProgram = null,
                remoteWorkDir = null,
                securityProtocol = SeiSecurityProtocol.NLA,
                tlsSecLevel = 2,
                gateway = null,
            )
        } else {
            options.copy(
                pcConnectMode = false,
                gpuProfile = SeiGpuProfile.AUTO,
                audioMode = SeiAudioMode.entries[audioIndex],
                gateway = if (gatewayOn && gwHost.isNotBlank()) {
                    SeiGateway(
                        host = gwHost.trim(),
                        port = gwPort.toIntOrNull() ?: 443,
                        username = gwUser.trim().ifEmpty { null },
                        password = gwPass.ifEmpty { null },
                        domain = gwDomain.trim().ifEmpty { null },
                    )
                } else null,
            )
        }

        finalOptions.write(context, profile.id)
        if (isPcQuickMode) {
            DisplayPrefs.setResolution(context, profile.id, DisplayPrefs.AUTOMATIC)
            DisplayPrefs.setRotation(context, profile.id, DisplayPrefs.rotations[rotationIndex])
            DisplayPrefs.setDpi(context, profile.id, 100)
        } else {
            DisplayPrefs.setResolution(context, profile.id, DisplayPrefs.resolutionOptions[resolutionIndex].value)
            DisplayPrefs.setRotation(context, profile.id, DisplayPrefs.rotations[rotationIndex])
            DisplayPrefs.setDpi(context, profile.id, dpi)
        }

        isSubmitting = true
        onSubmit(profile, rememberPass, connect) { result ->
            isSubmitting = false
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: seiNow("Failed to save profile.")
                error = msg
                validationPopup = msg
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
        ) {
            SeiTopBar(
                title = when {
                    existing != null && isPcQuickMode -> sei("Edit PC Connection")
                    existing != null -> sei("Edit RDP Device")
                    isPcQuickMode -> sei("PC Connect [Direct]")
                    else -> sei("New RDP Connection")
                },
                onBack = { requestCancel() },
                actions = {},
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (existing == null) {
                    SeiSegmented(
                        options = listOf(
                            sei("RDP Session (Desktop / Server)"),
                            sei("PC Connect (Direct Stream)"),
                        ),
                        selectedIndex = if (isPcQuickMode) 1 else 0,
                        onSelect = { idx ->
                            val wantPc = idx == 1
                            isPcQuickMode = wantPc
                            if (wantPc) {
                                options = options.withPcStreamEngine(options.pcStreamEngine)
                            } else {
                                options = options.withStreamProfile(SeiStreamProfile.AUTO).copy(
                                    pcConnectMode = false,
                                    gpuProfile = SeiGpuProfile.AUTO,
                                    dynamicResolution = true,
                                    consoleAdmin = false,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ==========================================
                // BASICS CARD (Always Open)
                // ==========================================
                SeiCard {
                    Text(
                        sei("Basic Connection Settings"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))

                    SeiField(
                        value = label,
                        onValueChange = { label = it },
                        label = if (isPcQuickMode) sei("PC Label (optional)") else sei("Device name"),
                        hint = if (isPcQuickMode) sei("e.g. Gaming PC / Workstation") else sei("e.g. Office PC / Server 01"),
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SeiField(
                            value = host,
                            onValueChange = {
                                host = if (isPcQuickMode) formatPcIdInput(it) else it
                                // If host contains port suffix, sync portText
                                val colonIdx = it.lastIndexOf(':')
                                if (colonIdx > 0 && it.indexOf(':') == colonIdx) {
                                    val extractedPort = it.substring(colonIdx + 1).filter(Char::isDigit)
                                    if (extractedPort.isNotEmpty()) portText = extractedPort
                                }
                            },
                            label = if (isPcQuickMode) sei("PC ID (10-Digit / SEI-ID)") else sei("Host / IP Address"),
                            hint = if (isPcQuickMode) "323-223-5826" else "192.168.1.100",
                            keyboardType = if (isPcQuickMode) KeyboardType.Number else KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.weight(1.5f),
                        )

                        SeiField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                            label = sei("Port"),
                            hint = "3389",
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                            modifier = Modifier.weight(0.7f),
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    SeiPillButton(
                        text = if (isPcQuickMode) sei("Auto-Scan PCs on Wi-Fi (LAN)") else sei("Scan Local RDP Hosts"),
                        onClick = { triggerLanScan() },
                        primary = false,
                        compact = true,
                        icon = SeiIcons.Search,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))

                    // Credentials
                    if (isPcQuickMode) {
                        Text(
                            sei("Host Access Password (optional)"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        SeiField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = sei("PC Security PIN / Password"),
                            hint = sei("Enter host PIN or leave empty"),
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        )
                    } else {
                        Text(
                            sei("User Credentials"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))

                        if (allAccounts.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    sei("Saved accounts"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SeiPillButton(
                                    text = if (user.isNotEmpty()) user else sei("Choose account"),
                                    onClick = { accountDropdownOpen = true },
                                    primary = false,
                                    compact = true,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        SeiField(
                            value = user,
                            onValueChange = { user = it },
                            label = sei("Username"),
                            hint = "Administrator",
                            imeAction = ImeAction.Next,
                        )

                        Spacer(Modifier.height(10.dp))
                        SeiField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = sei("Password"),
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        )

                        Spacer(Modifier.height(10.dp))
                        SeiField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = sei("Domain (optional)"),
                            hint = "WORKGROUP",
                            imeAction = ImeAction.Done,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    SeiToggleRow(
                        title = sei("Remember password"),
                        subtitle = sei("Store securely in Keystore vault"),
                        checked = rememberPass,
                        onCheckedChange = { rememberPass = it },
                        leading = SeiIcons.Lock,
                    )
                }

                // ==========================================
                // ACCORDION 1: DISPLAY
                // ==========================================
                val displaySummary = "${DisplayPrefs.resolutionOptions[resolutionIndex].en} · ${DisplayPrefs.rotations[rotationIndex]} · ${dpi}% zoom"
                SeiAccordionCard(
                    title = sei("Display"),
                    summary = displaySummary,
                    expanded = displayExpanded,
                    onToggle = { displayExpanded = !displayExpanded },
                    icon = SeiIcons.Monitor,
                ) {
                    DeviceDisplaySection(
                        resolutionIndex = resolutionIndex,
                        onResolutionIndexChange = { resolutionIndex = it },
                        rotationIndex = rotationIndex,
                        onRotationIndexChange = { rotationIndex = it },
                        dpi = dpi,
                        onDpiChange = { dpi = it },
                        options = options,
                        onOptionsChange = { options = it },
                        isPcQuickMode = isPcQuickMode,
                    )
                }

                // ==========================================
                // ACCORDION 2: AUDIO & CHANNELS
                // ==========================================
                val audioSummary = "${SeiAudioMode.entries[audioIndex].titleEn} · Mic ${if (options.microphone) "on" else "off"} · Clip ${if (options.clipboard) "on" else "off"} · Drive ${if (options.localDrive) "on" else "off"}"
                SeiAccordionCard(
                    title = sei("Audio & channels"),
                    summary = audioSummary,
                    expanded = audioExpanded,
                    onToggle = { audioExpanded = !audioExpanded },
                    icon = SeiIcons.Volume,
                ) {
                    DeviceAudioSection(
                        audioIndex = audioIndex,
                        onAudioIndexChange = { audioIndex = it },
                        options = options,
                        onOptionsChange = { options = it },
                    )
                }

                // ==========================================
                // ACCORDION 3: STREAMING & LATENCY
                // ==========================================
                val streamingSummary = if (isPcQuickMode) {
                    "${options.pcStreamEngine.titleEn} · ${options.pcTargetFps} FPS · ${options.pcBitrateMbps} Mbps"
                } else {
                    "${options.streamProfile.titleEn} · UDP ${if (options.udpTransport) "on" else "off"} · Async ${if (options.asyncUpdate) "on" else "off"}"
                }
                SeiAccordionCard(
                    title = sei("Streaming & Latency"),
                    summary = streamingSummary,
                    expanded = streamingExpanded,
                    onToggle = { streamingExpanded = !streamingExpanded },
                    icon = SeiIcons.Zap,
                ) {
                    DeviceStreamingSection(
                        options = options,
                        onOptionsChange = { options = it },
                        isPcQuickMode = isPcQuickMode,
                    )
                }

                // ==========================================
                // ACCORDION 4: SECURITY & ADMIN
                // ==========================================
                val securitySummary = "${options.securityProtocol.titleEn} · TLS ${if (options.tlsSecLevel == -1) "Auto" else options.tlsSecLevel.toString()} · Admin ${if (options.consoleAdmin) "on" else "off"}"
                SeiAccordionCard(
                    title = sei("Security & Admin Session"),
                    summary = securitySummary,
                    expanded = securityExpanded,
                    onToggle = { securityExpanded = !securityExpanded },
                    icon = SeiIcons.Shield,
                ) {
                    DeviceSecuritySection(
                        options = options,
                        onOptionsChange = { options = it },
                    )
                }

                // ==========================================
                // ACCORDION 5: WAKE-ON-LAN & SSH
                // ==========================================
                val wolSummary = if (options.macAddress.isNullOrBlank()) sei("Disabled")
                else "WOL ${options.macAddress} · ${options.wolBroadcast.ifBlank { "255.255.255.255" }}"
                SeiAccordionCard(
                    title = sei("Wake-on-LAN & SSH"),
                    summary = wolSummary,
                    expanded = wolExpanded,
                    onToggle = { wolExpanded = !wolExpanded },
                    icon = SeiIcons.Terminal,
                ) {
                    DeviceWolSection(
                        host = host,
                        options = options,
                        onOptionsChange = { options = it },
                    )
                }

                // ==========================================
                // ACCORDION 6: GATEWAY
                // ==========================================
                val gatewaySummary = if (!gatewayOn || gwHost.isBlank()) sei("Disabled")
                else "$gwHost:${gwPort.ifEmpty { "443" }}"
                SeiAccordionCard(
                    title = sei("Gateway"),
                    summary = gatewaySummary,
                    expanded = gatewayExpanded,
                    onToggle = { gatewayExpanded = !gatewayExpanded },
                    icon = SeiIcons.Lock,
                ) {
                    DeviceGatewaySection(
                        gatewayOn = gatewayOn,
                        onGatewayOnChange = { gatewayOn = it },
                        gwHost = gwHost,
                        onGwHostChange = { gwHost = it.trim() },
                        gwPort = gwPort,
                        onGwPortChange = { gwPort = it.filter(Char::isDigit).take(5) },
                        gwUser = gwUser,
                        onGwUserChange = { gwUser = it },
                        gwPass = gwPass,
                        onGwPassChange = { gwPass = it },
                        gwDomain = gwDomain,
                        onGwDomainChange = { gwDomain = it },
                    )
                }

                Spacer(Modifier.height(10.dp))
            }

            // ==========================================
            // STICKY BOTTOM BAR ABOVE KEYBOARD
            // ==========================================
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Test connection button with probe indicator
                    SeiPillButton(
                        text = when (testState) {
                            is ConnectionTestState.Testing -> sei("Testing...")
                            is ConnectionTestState.Online -> "Online · ${(testState as ConnectionTestState.Online).latencyMs} ms"
                            is ConnectionTestState.Unreachable -> sei("Unreachable")
                            ConnectionTestState.Idle -> sei("Test")
                        },
                        onClick = { testConnection() },
                        primary = false,
                        compact = true,
                        icon = when (testState) {
                            is ConnectionTestState.Online -> SeiIcons.Wifi
                            is ConnectionTestState.Unreachable -> SeiIcons.Close
                            else -> SeiIcons.Wifi
                        },
                        modifier = Modifier.weight(0.95f),
                    )

                    SeiPillButton(
                        text = if (isSubmitting) sei("Saving...") else sei("Save"),
                        onClick = { submit(false) },
                        enabled = !isSubmitting,
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )

                    SeiPillButton(
                        text = if (isSubmitting) sei("Saving...") else sei("Save & connect"),
                        onClick = { submit(true) },
                        enabled = !isSubmitting,
                        compact = true,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
        }

        // Modals & Dialogs
        if (confirmDiscard) {
            SeiDialog(
                title = sei("Discard changes?"),
                body = sei("Changes to this device configuration have not been saved. Leave without saving?"),
                confirmLabel = sei("Discard"),
                onConfirm = {
                    confirmDiscard = false
                    onCancel()
                },
                dismissLabel = sei("Keep editing"),
                onDismiss = { confirmDiscard = false },
            )
        }

        validationPopup?.let { msg ->
            SeiDialog(
                title = sei("Validation error"),
                body = msg,
                confirmLabel = sei("Got it"),
                onConfirm = { validationPopup = null },
                onDismiss = { validationPopup = null },
            )
        }

        if (lanScanModalOpen) {
            SeiOverlay(
                title = if (isPcQuickMode) sei("Scan PCs on Wi-Fi Network (LAN)")
                else sei("Scan RDP Hosts on Local Network"),
                scrollable = true,
                onDismiss = { lanScanModalOpen = false },
            ) {
                Text(
                    text = if (!lanSelfIp.isNullOrBlank()) {
                        sei("Your Phone IP: {0} · Scanning port 3389 on local subnet", lanSelfIp!!)
                    } else {
                        sei("Make sure your phone and PC are on the same Wi-Fi / Hotspot network.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lanScanning) {
                    Text(
                        text = sei("Scanning 254 local addresses in parallel..."),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (lanFoundHosts.isEmpty()) {
                    Text(
                        text = sei(
                            "No active PC found on this subnet yet. Ensure the PC is powered on and SeiDeskHost / Remote Desktop (Port 3389) is enabled.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lanFoundHosts.forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                    .clickable {
                                        host = if (isPcQuickMode) item.pcId else "${item.ip}:${item.port}"
                                        portText = item.port.toString()
                                        if (label.isBlank()) {
                                            label = if (isPcQuickMode) "PC ${item.pcId.takeLast(4)}" else "RDP ${item.ip}"
                                        }
                                        lanScanModalOpen = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPcQuickMode) "ID PC: ${item.pcId}" else "${item.ip}:${item.port}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = if (isPcQuickMode) {
                                            sei("Endpoint: {0}:{1} · Tap to use this ID", item.ip, item.port)
                                        } else {
                                            sei("PC ID: {0} · Tap to use this IP", item.pcId)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "${item.rttMs} ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiPillButton(
                        text = sei("Rescan"),
                        onClick = { triggerLanScan() },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        text = sei("Close"),
                        onClick = { lanScanModalOpen = false },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (accountDropdownOpen) {
            SeiOverlay(
                title = sei("Saved Accounts"),
                scrollable = true,
                onDismiss = {
                    accountDropdownOpen = false
                    accountSearch = ""
                },
            ) {
                SeiField(
                    value = accountSearch,
                    onValueChange = { accountSearch = it },
                    label = sei("Search"),
                    hint = sei("Filter by username..."),
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredAccounts.forEach { acc ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    user = acc.username
                                    pass = acc.password
                                    domain = acc.domain
                                    accountDropdownOpen = false
                                    accountSearch = ""
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(acc.username, style = MaterialTheme.typography.titleSmall)
                                if (acc.domain.isNotEmpty()) {
                                    Text(acc.domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
