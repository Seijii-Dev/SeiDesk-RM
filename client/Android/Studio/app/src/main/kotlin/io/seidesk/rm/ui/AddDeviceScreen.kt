package io.seidesk.rm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.freerdp.freerdpcore.services.LibFreeRDP
import io.seidesk.rm.core.ConnectionProfile
import io.seidesk.rm.core.DiscoveredPcHost
import io.seidesk.rm.core.LanScanner
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.WakeOnLan
import io.seidesk.rm.core.SeiGpuProfile
import io.seidesk.rm.core.SeiPcStreamEngine
import io.seidesk.rm.core.SeiSecurityProtocol
import io.seidesk.rm.core.SeiStreamProfile
import io.seidesk.rm.core.encodeIpv4ToPcId
import io.seidesk.rm.core.formatRdpEndpoint
import io.seidesk.rm.core.parsePcIdOrEndpoint
import io.seidesk.rm.core.parseRdpEndpoint
import io.seidesk.rm.core.SeiAudioMode
import io.seidesk.rm.core.SeiGateway
import io.seidesk.rm.security.CredentialVault
import io.seidesk.rm.ui.components.SeiNoticeBus
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.seidesk.rm.ui.components.SeiSlider
import io.seidesk.rm.ui.components.SeiCard
import io.seidesk.rm.ui.components.SeiDialog
import io.seidesk.rm.ui.components.SeiField
import io.seidesk.rm.ui.components.SeiIconPill
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiOverlay
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.XySectionLabel
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.components.SeiTopBar
import io.seidesk.rm.ui.theme.SeiPill

/**
 * Layar sei( "Add device") — bukan dialog. Semua yang dibutuhkan satu
 * koneksi RDP ada di sini: alamat, kredensial, tampilan, kanal yang
 * di-redirect, dan gateway.
 *
 * Preferensi (tampilan + opsi RDP) ditulis langsung ke SharedPreferences
 * dengan id = `host:port`, jadi begitu profil disimpan, sesi berikutnya
 * memakai setelan yang sama.
 */
private data class SavedAccountOption(
    val username: String,
    val domain: String,
    val password: String,
)

@Composable
fun AddDeviceScreen(
    existing: ConnectionProfile?,
    savedUsers: List<String>,
    savedProfiles: List<ConnectionProfile> = emptyList(),
    initialPcQuickMode: Boolean = false,
    onCancel: () -> Unit,
    onSubmit: (profile: ConnectionProfile, rememberPassword: Boolean, connect: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val vault = remember { CredentialVault(context.applicationContext) }
    // Kunci tetap: dipakai ulang saat mengubah perangkat supaya simpan tidak
    // menghasilkan baris kedua. Data lama (belum punya key) memakai id-nya.
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

    // "Setelan cepat" menyembunyikan bagian lanjutan supaya layar tidak panjang
    // dan tidak perlu scroll jauh saat cuma mau konek.
    var showAdvanced by remember { mutableStateOf(false) }

    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var host by remember(existing) {
        mutableStateOf(existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty())
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

    // Isi otomatis password bila host+user pernah dipakai (kredensial
    // tersimpan per host — tidak perlu input ulang tiap add koneksi).
    LaunchedEffect(host, user) {
        if (pass.isNotEmpty()) return@LaunchedEffect
        val ep = parseRdpEndpoint(host) ?: return@LaunchedEffect
        val stored = vault.get(CredentialVault.hostCredKey(ep.host, ep.port, user))
        if (!stored.isNullOrEmpty()) {
            pass = stored
            rememberPass = true
        }
    }

    val baseAccounts = remember(savedProfiles, savedUsers, existing) {
        val list = mutableListOf<SavedAccountOption>()
        savedProfiles.forEach { prof ->
            val u = prof.username?.trim().orEmpty()
            if (u.isNotEmpty() && !u.equals("XyDesk", ignoreCase = true)) {
                val p = prof.password ?: vault.get(prof.id).orEmpty()
                val d = prof.domain?.trim().orEmpty()
                if (list.none { it.username.equals(u, ignoreCase = true) && it.domain.equals(d, ignoreCase = true) }) {
                    list.add(SavedAccountOption(u, d, p))
                }
            }
        }
        savedUsers.forEach { u ->
            val clean = u.trim()
            if (clean.isNotEmpty() && !clean.equals("XyDesk", ignoreCase = true) &&
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
    // Jika sudah ada akun yang terisi (mis. saat edit perangkat atau setelah
    // user input akun), sembunyikan form dan tampilkan sebagai dropdown.
    var showCredentialForm by remember(existing) {
        mutableStateOf(existing?.username.isNullOrBlank() && baseAccounts.isEmpty())
    }
    var accountDropdownOpen by remember { mutableStateOf(false) }
    var accountSearch by remember { mutableStateOf("") }
    val filteredAccounts = remember(allAccounts, accountSearch) {
        val query = accountSearch.trim()
        if (query.isEmpty()) allAccounts else allAccounts.filter { account ->
            "${account.domain} ${account.username}".contains(query, ignoreCase = true)
        }
    }

    val scope = rememberCoroutineScope()
    val stored = remember(deviceId, existing, initialPcQuickMode) {
        if (existingStored != null) {
            existingStored
        } else {
            val app = AppPrefs(context)
            val baseProfile = if (initialPcQuickMode) {
                SeiStreamProfile.ULTRA_LOW_LATENCY
            } else {
                SeiStreamProfile.entries.getOrElse(app.defaultStreamProfile) {
                    SeiStreamProfile.AUTO
                }
            }
            val baseSecProto = if (initialPcQuickMode) {
                SeiSecurityProtocol.NLA
            } else {
                SeiSecurityProtocol.entries.getOrElse(app.defaultSecurityProtocol) {
                    SeiSecurityProtocol.AUTO
                }
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
    var wolStatusText by remember { mutableStateOf<String?>(null) }
    var gatewayOn by remember { mutableStateOf(stored.gateway != null) }
    var gwHost by remember { mutableStateOf(stored.gateway?.host.orEmpty()) }
    var gwPort by remember { mutableStateOf((stored.gateway?.port ?: 443).toString()) }
    var gwUser by remember { mutableStateOf(stored.gateway?.username.orEmpty()) }
    var gwPass by remember { mutableStateOf(stored.gateway?.password.orEmpty()) }
    var gwDomain by remember { mutableStateOf(stored.gateway?.domain.orEmpty()) }

    var resolutionIndex by remember(deviceId) {
        mutableStateOf(
            if (deviceId == null) 0
            else DisplayPrefs.resolutionOptions.indexOfFirst {
                it.value == DisplayPrefs.resolution(context, deviceId)
            }.coerceAtLeast(0)
        )
    }
    var rotationIndex by remember(deviceId) {
        mutableStateOf(
            if (deviceId == null) 0
            else DisplayPrefs.rotations.indexOf(DisplayPrefs.rotation(context, deviceId))
                .coerceAtLeast(0)
        )
    }
    var dpi by remember(deviceId) {
        mutableStateOf(if (deviceId == null) 100 else DisplayPrefs.dpi(context, deviceId))
    }
    var audioIndex by remember { mutableStateOf(options.audioMode.ordinal) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var validationPopup by remember { mutableStateOf<String?>(null) }
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

    val initialHost = remember(existing) {
        existing?.let { formatRdpEndpoint(it.host, it.port) }.orEmpty()
    }
    val hasChanges = if (existing == null) {
        host.isNotBlank() || label.isNotBlank() || pass.isNotBlank()
    } else {
        host != initialHost ||
            label != existing.label.orEmpty() ||
            user != existing.username.orEmpty() ||
            pass != existing.password.orEmpty() ||
            domain != existing.domain.orEmpty() ||
            options != stored ||
            gatewayOn != (stored.gateway != null)
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

    fun submit(connect: Boolean) {
        val endpoint = parsePcIdOrEndpoint(host)
        if (host.isBlank()) {
            val msg = if (isPcQuickMode) {
                seiNow( "PC ID or host address is required.")
            } else {
                seiNow( "Host address is required.")
            }
            error = msg
            validationPopup = msg
            return
        }
        if (endpoint == null) {
            val msg = seiNow(
                "Invalid PC ID or address/port. Enter a 10-digit PC ID (e.g. 323-223-5826) or host:port.",
            )
            error = msg
            validationPopup = msg
            return
        }
        if (gatewayOn && gwHost.isBlank()) {
            val msg = seiNow(
                "RDP Gateway is enabled, but the Gateway host address is empty.",
            )
            error = msg
            validationPopup = msg
            return
        }
        if (!options.macAddress.isNullOrBlank() && WakeOnLan.parseMacBytes(options.macAddress) == null) {
            val msg = seiNow(
                "Invalid Wake-on-LAN MAC address format. Use 6 hex pairs like AA:BB:CC:DD:EE:FF.",
            )
            error = msg
            validationPopup = msg
            return
        }
        val effectiveUser = if (isPcQuickMode) {
            if (pass.isNotBlank()) "XyDesk" else null
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
                } else {
                    null
                },
            )
        }
        finalOptions.write(context, profile.id)
        if (isPcQuickMode) {
            DisplayPrefs.setResolution(context, profile.id, DisplayPrefs.AUTOMATIC)
            DisplayPrefs.setRotation(context, profile.id, DisplayPrefs.rotations[rotationIndex])
            DisplayPrefs.setDpi(context, profile.id, 100)
        } else {
            DisplayPrefs.setResolution(
                context,
                profile.id,
                DisplayPrefs.resolutionOptions[resolutionIndex].value,
            )
            DisplayPrefs.setRotation(context, profile.id, DisplayPrefs.rotations[rotationIndex])
            DisplayPrefs.setDpi(context, profile.id, dpi)
        }
        onSubmit(profile, rememberPass, connect)
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
                existing != null && isPcQuickMode -> sei( "Edit PC Connection")
                existing != null -> sei( "Edit RDP Device")
                isPcQuickMode -> sei( "PC Connect [Experimental]")
                else -> sei( "New RDP Connection")
            },
            onBack = { requestCancel() },
            actions = {
                SeiIconPill(
                    SeiIcons.Gear,
                    { showAdvanced = true },
                    active = showAdvanced,
                    flat = true,
                    contentDescription = sei( "Advanced settings"),
                )
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SeiSegmented(
                options = listOf(
                    sei( "Quick setup"),
                    sei( "Advanced"),
                ),
                selectedIndex = if (showAdvanced) 1 else 0,
                onSelect = { showAdvanced = it == 1 },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!showAdvanced) {
                Text(
                    sei(
                        "Quick mode shows only what a connection needs: account, connection type, audio, " +
                            "clipboard, drive, and camera.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (existing == null) {
                SeiSegmented(
                    options = listOf(
                        sei( "RDP Session (Desktop / Server)"),
                        sei( "PC Connect (Direct Stream)"),
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

            XySectionLabel(
                if (isPcQuickMode) sei( "PC Identity · 1:1 Direct Stream (No RDP Account)")
                else sei( "RDP Host / Server Address")
            )
            SeiCard {
                SeiField(
                    value = label,
                    onValueChange = { label = it },
                    label = if (isPcQuickMode) sei( "PC Label (optional)")
                    else sei( "Device name"),
                    hint = if (isPcQuickMode) sei( "e.g. Gaming PC / Main Rig")
                    else sei( "e.g. Office PC"),
                )
                Spacer(Modifier.height(14.dp))
                SeiField(
                    value = host,
                    onValueChange = { host = it },
                    label = if (isPcQuickMode) {
                        sei( "PC ID (10-Digit / XY-ID)")
                    } else {
                        sei( "Host / IP (optional port)")
                    },
                    hint = if (isPcQuickMode) {
                        sei( "323-223-5826 or XY-C0A8-0132")
                    } else {
                        sei( "pc.example.com or [2001:db8::1]:3390")
                    },
                    keyboardType = KeyboardType.Uri,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                )
                Spacer(Modifier.height(10.dp))
                SeiPillButton(
                    text = if (isPcQuickMode) {
                        sei( "Auto-Scan PCs on Wi-Fi (LAN)")
                    } else {
                        sei( "Scan RDP Hosts on Local Network (LAN)")
                    },
                    onClick = { triggerLanScan() },
                    icon = SeiIcons.Wifi,
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isPcQuickMode) {
                    Spacer(Modifier.height(14.dp))
                    SeiField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = sei( "XyDeskHost Session PIN / Key (optional)"),
                        hint = sei( "6-digit PIN from XyDeskHost.exe (not Windows account)"),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                    )
                }
                val resolvedPreview = remember(host) { parsePcIdOrEndpoint(host) }
                val encodedPcId = remember(resolvedPreview) {
                    resolvedPreview?.let { encodeIpv4ToPcId(it.host) }
                }
                if (resolvedPreview != null && host.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append(sei( "Target: "))
                            append(resolvedPreview.host).append(':').append(resolvedPreview.port)
                            if (encodedPcId != null) {
                                append("  ·  ID PC: ").append(encodedPcId)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isPcQuickMode) {
                XySectionLabel(
                    sei(
                        "Native PC Agent (XyDeskHost.exe · In Development)",
                    ),
                )
                SeiCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sei( "XyDeskHost.exe + .dll + QUIC + E2EE"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Box(
                            Modifier
                                .clip(SeiPill)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SeiPill)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                sei( "EXPERIMENTAL"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sei(
                            "Unlike standard RDP: PC Connection does not use Windows account/domain credentials and locks resolution + DPI 1:1 to the physical PC monitor (DXGI Desktop Duplication). All traffic is End-to-End Encrypted (TLS 1.3 + Keystore Pinning) with an Ultra-Low Latency path for FPS games.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeiPillButton(
                            text = sei( "Copy PC Setup Command"),
                            onClick = {
                                val cmd = "irm https://www.xydeskremote.biz.id/host | iex"
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("seidesk-host-cmd", cmd))
                                SeiNoticeBus.post(
                                    seiNow(
                                        "PowerShell command copied! Run it in Admin PowerShell on your PC.",
                                    ),
                                )
                            },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        SeiPillButton(
                            text = sei( "XyDeskHost Info"),
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.xydeskremote.biz.id/host")),
                                    )
                                }
                            },
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                XySectionLabel(
                    sei(
                        "Direct PC Stream Engine (PC Connect Exclusive)",
                    ),
                )
                SeiCard {
                    Text(
                        sei( "Direct Stream Pipeline Architecture"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        sei(
                            "Dedicated streaming pipeline for PC Connect (unavailable in RDP Desktop mode). Locks 1:1 to the physical monitor with zero frame queue.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SeiPcStreamEngine.entries.forEach { engine ->
                            val selected = options.pcStreamEngine == engine
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.medium,
                                    )
                                    .clickable {
                                        options = options.withPcStreamEngine(engine)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(sei( engine.titleEn), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        sei( engine.detailEn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .clip(SeiPill)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SeiPill)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = engine.badge,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        sei( "Handset display refresh preference"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        sei(
                            "Asks Android to select a supported panel refresh mode. Remote stream FPS still depends on the host, network, and display.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val refreshChoices = listOf(30, 60, 90, 120)
                    SeiSegmented(
                        options = refreshChoices.map { "$it Hz" },
                        selectedIndex = refreshChoices.indexOf(options.pcTargetFps).coerceAtLeast(1),
                        onSelect = { idx ->
                            options = options.copy(pcTargetFps = refreshChoices[idx], pcConnectMode = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        sei( "Target Video Bitrate: {0} Mbps", options.pcBitrateMbps),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    SeiSlider(
                        value = options.pcBitrateMbps.toFloat(),
                        onValueChange = { options = options.copy(pcBitrateMbps = it.toInt().coerceIn(5, 80)) },
                        valueRange = 5f..80f,
                        steps = 14,
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        sei( "Host Hardware GPU Encoder Binding (NVENC / AMF / QuickSync)"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        sei(
                            "PC Connect Exclusive: bind the encode pipeline directly to your PC graphics card architecture.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SeiGpuProfile.entries.forEach { gpu ->
                            val active = options.gpuProfile == gpu
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.surface,
                                    )
                                    .border(
                                        1.dp,
                                        if (active) MaterialTheme.colorScheme.outlineVariant
                                        else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.medium,
                                    )
                                    .clickable { options = options.withGpuProfile(gpu).copy(pcConnectMode = true) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(sei( gpu.titleEn), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        sei( gpu.detailEn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        sei(
                            "Display Info: Resolution and DPI are locked 1:1 to the physical PC console monitor (DXGI Desktop Duplication). Virtual multi-user RDP, DISP Dynamic Resolution, and RD Gateway are disabled in this mode.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                XySectionLabel(sei( "FPS Game Input, Transport & PC Wake-on-LAN"))
                SeiCard {
                    SeiToggleRow(
                        title = sei( "RawInput Relative Mouse (360° FPS Camera)"),
                        subtitle = sei(
                            "Send pure relative mouse deltas without screen-edge clamping for shooter/3D games",
                        ),
                        checked = options.rawInputMouse,
                        onCheckedChange = { options = options.copy(rawInputMouse = it) },
                        leading = SeiIcons.Sliders,
                    )
                    SeiToggleRow(
                        title = sei( "QUIC / UDP Datagram Transport (Zero HoL Blocking)"),
                        subtitle = sei(
                            "Prioritize newest frames without TCP head-of-line blocking during jitter",
                        ),
                        checked = options.udpTransport,
                        onCheckedChange = { options = options.copy(udpTransport = it) },
                        leading = SeiIcons.Wifi,
                    )
                    SeiToggleRow(
                        title = sei( "Low-Latency Real-Time Audio"),
                        subtitle = sei(
                            "Stream game and desktop audio directly to your phone",
                        ),
                        checked = audioIndex == 0,
                        onCheckedChange = { audioIndex = if (it) 0 else 2 },
                        leading = SeiIcons.Mic,
                    )
                    SeiToggleRow(
                        title = sei( "Auto Clipboard (Long-Text Anti-Disconnect)"),
                        subtitle = sei(
                            "Two-way text & image sync with safe buffer ceiling protection",
                        ),
                        checked = options.clipboard,
                        onCheckedChange = { options = options.copy(clipboard = it) },
                        leading = SeiIcons.Clip,
                    )
                    SeiToggleRow(
                        title = sei( "Battery Saver & Cool Thermal Mode (VSYNC Pacing)"),
                        subtitle = sei(
                            "Coalesce pixel bursts per VSYNC cycle to keep the phone cool and save battery",
                        ),
                        checked = options.batterySaver,
                        onCheckedChange = { options = options.copy(batterySaver = it) },
                        leading = SeiIcons.Sliders,
                    )
                    Spacer(Modifier.height(10.dp))
                    SeiField(
                        value = options.macAddress.orEmpty(),
                        onValueChange = { options = options.copy(macAddress = it.trim().ifEmpty { null }) },
                        label = sei( "PC MAC Address for Wake-on-LAN (optional)"),
                        hint = "AA:BB:CC:DD:EE:FF",
                    )
                }
            } else {

            XySectionLabel(sei( "Credentials"))
            SeiCard {
                if (!showCredentialForm) {
                    val activeAccountLabel = if (user.isNotBlank()) {
                        if (domain.isNotBlank()) "$domain\\$user" else user
                    } else {
                        sei( "No Account (Ask on Connect)")
                    }
                    val activeAccountSub = if (user.isNotBlank()) {
                        if (pass.isNotEmpty() && rememberPass) {
                            sei( "Password saved (AES-256-GCM) · Tap to switch account")
                        } else {
                            sei( "Password asked on connect · Tap to switch account")
                        }
                    } else {
                        sei( "Tap to select a saved account or add a new account")
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                            .clickable {
                                accountDropdownOpen = !accountDropdownOpen
                                accountSearch = ""
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sei( "Active Credential Account"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    activeAccountLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    activeAccountSub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (accountDropdownOpen) "▴" else "▾",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }

                    if (accountDropdownOpen) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    sei( "Saved accounts"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    sei( "Close"),
                                    modifier = Modifier.clickable {
                                        accountDropdownOpen = false
                                        accountSearch = ""
                                    }.padding(horizontal = 8.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (allAccounts.size >= 4) {
                                SeiField(
                                    value = accountSearch,
                                    onValueChange = { accountSearch = it },
                                    label = sei( "Search accounts"),
                                    hint = sei( "Username or domain"),
                                )
                            }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 4.dp),
                            ) {
                                filteredAccounts.forEach { acc ->
                                    val itemTitle = if (acc.domain.isNotBlank()) "${acc.domain}\\${acc.username}" else acc.username
                                    val isSelected = acc.username.equals(user, ignoreCase = true) &&
                                        acc.domain.equals(domain, ignoreCase = true)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .clickable {
                                                user = acc.username
                                                domain = acc.domain
                                                if (acc.password.isNotEmpty()) {
                                                    pass = acc.password
                                                    rememberPass = true
                                                }
                                                accountDropdownOpen = false
                                                accountSearch = ""
                                            }
                                            .padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                itemTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                if (acc.password.isNotEmpty()) {
                                                    sei( "Password saved · ready to use")
                                                } else {
                                                    sei( "Saved account")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (isSelected) {
                                            Text(
                                                sei( "✓ Selected"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                }
                                if (allAccounts.isNotEmpty() && filteredAccounts.isEmpty()) {
                                    Text(
                                        sei( "No matching accounts."),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                if (user.isNotBlank()) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .clickable {
                                                accountDropdownOpen = false
                                                accountSearch = ""
                                                showCredentialForm = true
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            sei( "✎ Edit this account"),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clickable {
                                            user = ""
                                            pass = ""
                                            domain = ""
                                            accountDropdownOpen = false
                                            accountSearch = ""
                                            showCredentialForm = true
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        sei( "+ Add new account"),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clickable {
                                            user = ""
                                            pass = ""
                                            domain = ""
                                            accountDropdownOpen = false
                                            accountSearch = ""
                                            showCredentialForm = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        sei( "No account (ask on connect)"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (isPcQuickMode && pass.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        SeiField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = sei( "PC Access Password"),
                            hint = sei( "Enter PC password from XyDeskHost"),
                            isPassword = true,
                            keyboardType = KeyboardType.Password,
                        )
                    }
                } else {
                    SeiField(
                        value = user,
                        onValueChange = { user = it },
                        label = sei( "Username"),
                        hint = sei(
                            "leave empty to be asked on connect",
                        ),
                    )
                    Spacer(Modifier.height(14.dp))
                    SeiField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = sei( "Password"),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                    )
                    Spacer(Modifier.height(14.dp))
                    SeiField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = sei( "Domain (optional)"),
                        hint = "CORP",
                    )
                    Spacer(Modifier.height(6.dp))
                    SeiToggleRow(
                        title = sei( "Remember password"),
                        subtitle = sei( "Stored AES-GCM encrypted, key in Android Keystore"),
                        checked = rememberPass,
                        onCheckedChange = { rememberPass = it },
                        leading = SeiIcons.Lock,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (allAccounts.isNotEmpty()) {
                            SeiPillButton(
                                text = sei( "Pick from Dropdown ({0})", allAccounts.size),
                                onClick = {
                                    if (user.isBlank()) {
                                        val first = allAccounts.first()
                                        user = first.username
                                        domain = first.domain
                                        if (first.password.isNotEmpty()) pass = first.password
                                    }
                                    showCredentialForm = false
                                    accountSearch = ""
                                    accountDropdownOpen = true
                                },
                                primary = false,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        SeiPillButton(
                            text = sei( "Save Account to Dropdown"),
                            onClick = {
                                val u = user.trim()
                                if (u.isNotEmpty()) {
                                    extraAccounts = listOf(SavedAccountOption(u, domain.trim(), pass)) + extraAccounts
                                }
                                showCredentialForm = false
                                accountDropdownOpen = false
                                accountSearch = ""
                            },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Mode cepat menyembunyikan bagian ini; ada di tab Lanjutan.
            if (showAdvanced) {
            XySectionLabel(sei( "Display"))
            SeiCard {
                Text(
                    sei( "Remote desktop resolution"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    sei(
                        "The \"Automatic\" default is always 16:9 (right for Windows " +
                            "desktops). \"Follow phone screen\" uses the phone ratio — for video/games.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayPrefs.resolutionOptions.chunked(2).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { colIndex, option ->
                                val index = rowIndex * 2 + colIndex
                                SeiPillButton(
                                    text = sei( option.en),
                                    onClick = { resolutionIndex = index },
                                    primary = index == resolutionIndex,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    sei( "Device orientation"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SeiSegmented(
                    options = DisplayPrefs.rotations,
                    selectedIndex = rotationIndex,
                    onSelect = { rotationIndex = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    sei( "Initial local zoom: {0}%", dpi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SeiSlider(
                    value = dpi.toFloat(),
                    onValueChange = { dpi = it.toInt() },
                    valueRange = 80f..200f,
                    steps = 11,
                )
                Text(
                    sei(
                        "This local zoom is the starting value when a session opens. Windows desktop DPI " +
                            "is controlled separately in the session panel; remote resolution is separate too.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            }

            XySectionLabel(sei( "Audio & channels"))
            SeiCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeiAudioMode.entries.forEachIndexed { index, mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (index == audioIndex) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (index == audioIndex) MaterialTheme.colorScheme.outlineVariant
                                    else MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.medium,
                                )
                                .clickable { audioIndex = index }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sei( mode.titleEn), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    sei( mode.detailEn),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    val audioNote = when (SeiAudioMode.entries[audioIndex]) {
                        SeiAudioMode.DEVICE ->
                            sei(
                                "Device mode plays PC sound over the RDP audio channel (no host agent). " +
                                    "If PC 'Remote Audio' is disabled for VB-CABLE, switch to Remote mode.",
                            )
                        SeiAudioMode.REMOTE ->
                            sei(
                                "Remote mode routes PC sound through the UDP :4433 bridge, so XyDeskRemoteHost.exe " +
                                    "must run on the PC. Use it when the RDP audio channel is blocked/empty.",
                            )
                        SeiAudioMode.OFF ->
                            sei( "Audio fully off — the UDP bridge stays down.")
                    }
                    Text(
                        audioNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val bridgeRelevant = SeiAudioMode.entries[audioIndex] == SeiAudioMode.REMOTE
                SeiToggleRow(
                    title = sei( "PC audio over UDP (bridge)"),
                    subtitle = if (bridgeRelevant) {
                        sei(
                            "Needs XyDeskRemoteHost.exe on the PC (UDP 4433). Without it, PC sound never arrives.",
                        )
                    } else {
                        sei(
                            "Not used: Device mode plays sound through the RDP channel.",
                        )
                    },
                    checked = options.quicAudio && bridgeRelevant,
                    onCheckedChange = { v -> options = options.copy(quicAudio = v) },
                    leading = SeiIcons.Volume,
                    enabled = bridgeRelevant,
                )
                SeiToggleRow(
                    title = sei( "Microphone"),
                    subtitle = sei( "Send phone audio to remote (needs mic permission)"),
                    checked = options.microphone,
                    onCheckedChange = { options = options.copy(microphone = it) },
                    leading = SeiIcons.Mic,
                )
                if (options.microphone) {
                    Spacer(Modifier.height(8.dp))
                    XySectionLabel(sei( "Microphone DSP (on phone)"))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        XySliderRow(
                            label = sei( "Mic gain"),
                            value = options.micGainDb.toFloat(),
                            suffix = " dB",
                            range = -12f..24f,
                            steps = 6,
                        ) { options = options.copy(micGainDb = it.toInt()) }
                        XySliderRow(
                            label = sei( "Noise gate"),
                            value = options.micGateDb.toFloat(),
                            suffix = " dBFS",
                            range = -70f..0f,
                            steps = 6,
                        ) { options = options.copy(micGateDb = it.toInt()) }
                        SeiToggleRow(
                            title = sei( "Noise suppression"),
                            subtitle = sei( "High-pass + expander kills hiss, AC hum, wind"),
                            checked = options.micNoiseSuppression,
                            onCheckedChange = { options = options.copy(micNoiseSuppression = it) },
                            leading = SeiIcons.Mic,
                        )
                        SeiToggleRow(
                            title = sei( "Auto gain (AGC)"),
                            subtitle = sei( "Phone mic level auto-levelled while talking"),
                            checked = options.micAgc,
                            onCheckedChange = { options = options.copy(micAgc = it) },
                            leading = SeiIcons.Mic,
                        )
                        Text(
                            sei(
                                "The mic renders into the PC's 'CABLE Input / XyDesk Virtual Microphone' endpoint; " +
                                    "if missing, the Host Agent falls back to the default device.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SeiToggleRow(
                    title = sei( "Clipboard"),
                    subtitle = sei( "Two-way copy-paste"),
                    checked = options.clipboard,
                    onCheckedChange = { options = options.copy(clipboard = it) },
                    leading = SeiIcons.Clip,
                )
                SeiToggleRow(
                    title = sei( "Local storage"),
                    subtitle = sei( "Phone folder appears as the 'XyDesk' drive on the remote PC"),
                    checked = options.localDrive,
                    onCheckedChange = { options = options.copy(localDrive = it) },
                    leading = SeiIcons.Folder,
                )
                if (options.localDrive) {
                    StorageAccessRow()
                }
                SeiToggleRow(
                    title = sei( "Camera"),
                    subtitle = sei( "Phone camera as remote webcam (if the server supports it)"),
                    checked = options.camera,
                    onCheckedChange = { options = options.copy(camera = it) },
                    leading = SeiIcons.Shot,
                )
            }

            if (showAdvanced) {
            XySectionLabel(sei( "Streaming & Latency"))
            SeiCard {
                Text(
                    sei( "Streaming & Latency Profile"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    sei(
                        "Pick an instant preset or customize codec, color depth, and latency flags below.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val streamPresets = listOf(
                    SeiStreamProfile.AUTO,
                    SeiStreamProfile.BALANCED,
                    SeiStreamProfile.ULTRA_LOW_LATENCY,
                    SeiStreamProfile.HIGH_VISUAL,
                    SeiStreamProfile.DATA_SAVER,
                )
                streamPresets.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { preset ->
                            SeiPillButton(
                                text = sei( preset.titleEn),
                                onClick = { options = options.withStreamProfile(preset) },
                                primary = options.streamProfile == preset,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (options.streamProfile == SeiStreamProfile.CUSTOM) {
                    Text(
                        sei( "Active profile: Custom Manual"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
                SeiToggleRow(
                    title = sei( "Battery Saver & Cool Thermal Mode (VSYNC Pacing)"),
                    subtitle = sei(
                        "Coalesce pixel burst updates per screen cycle to keep the phone cool and save battery",
                    ),
                    checked = options.batterySaver,
                    onCheckedChange = { options = options.copy(batterySaver = it) },
                    leading = SeiIcons.Sliders,
                )
                SeiToggleRow(
                    title = sei( "UDP transport (RDP-UDP + FEC)"),
                    subtitle = sei(
                        "RDP-UDP + FEC; smoother fast motion, needs server support",
                    ),
                    checked = options.udpTransport,
                    onCheckedChange = { options = options.copy(udpTransport = it) },
                    leading = SeiIcons.Wifi,
                )
                SeiToggleRow(
                    title = sei( "Automatic bandwidth detection"),
                    subtitle = sei(
                        "FreeRDP auto profile adapts to network conditions",
                    ),
                    checked = options.networkAutoDetect,
                    onCheckedChange = {
                        options = options.copy(networkAutoDetect = it, streamProfile = SeiStreamProfile.CUSTOM)
                    },
                    leading = SeiIcons.Sliders,
                    enabled = !options.lowBandwidth,
                )
                SeiToggleRow(
                    title = sei( "Low-bandwidth mode (broadband-low / AVC420)"),
                    subtitle = sei(
                        "Use broadband-low + AVC420 hints for cellular/metered connections",
                    ),
                    checked = options.lowBandwidth,
                    onCheckedChange = {
                        options = options.copy(lowBandwidth = it, streamProfile = SeiStreamProfile.CUSTOM)
                    },
                    leading = SeiIcons.Sliders,
                )
                SeiToggleRow(
                    title = sei( "H.264 / RemoteFX (GFX)"),
                    subtitle = sei(
                        "H.264 hardware acceleration (AVC444 on normal links, AVC420 in low-bandwidth)",
                    ),
                    checked = options.h264,
                    onCheckedChange = {
                        options = options.copy(h264 = it, streamProfile = SeiStreamProfile.CUSTOM)
                    },
                    leading = SeiIcons.Grid,
                )
                SeiToggleRow(
                    title = sei( "Async Frame Update (+async-update)"),
                    subtitle = sei(
                        "Decouple frame rendering from input queue for maximum responsiveness",
                    ),
                    checked = options.asyncUpdate,
                    onCheckedChange = {
                        options = options.copy(asyncUpdate = it, streamProfile = SeiStreamProfile.CUSTOM)
                    },
                )
                SeiToggleRow(
                    title = sei( "Async Input & Channels (+async-channels)"),
                    subtitle = sei(
                        "Process input, audio, and clipboard channels on dedicated threads",
                    ),
                    checked = options.asyncChannels,
                    onCheckedChange = {
                        options = options.copy(asyncChannels = it, streamProfile = SeiStreamProfile.CUSTOM)
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    sei( "RDP packet compression level"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                SeiSegmented(
                    options = listOf(
                        sei( "Off (0)"),
                        sei( "Standard (1)"),
                        sei( "Maximum (2)"),
                    ),
                    selectedIndex = options.compressionLevel.coerceIn(0, 2),
                    onSelect = {
                        options = options.copy(
                            compressionLevel = it,
                            streamProfile = SeiStreamProfile.CUSTOM,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    sei( "Color depth (BPP)"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                val depthValues = listOf(32, 24, 16)
                SeiSegmented(
                    options = listOf("32-bit", "24-bit", "16-bit"),
                    selectedIndex = depthValues.indexOf(options.colorDepth).coerceAtLeast(0),
                    onSelect = {
                        options = options.copy(
                            colorDepth = depthValues[it],
                            streamProfile = SeiStreamProfile.CUSTOM,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Seksi "Pengalaman Visual Desktop Windows" dihapus atas permintaan
                // pemilik: toggle ini bikin wallpaper hitam & animasi Windows hilang.
                // Semua opsi visual kini selalu aktif (lihat default RdpOptions).
            }

            XySectionLabel(sei( "Security & Admin Session"))
            SeiCard {
                Text(
                    sei( "Authentication security protocol"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                SeiSecurityProtocol.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { proto ->
                            SeiPillButton(
                                text = sei( proto.titleEn),
                                onClick = { options = options.copy(securityProtocol = proto) },
                                primary = options.securityProtocol == proto,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    sei( "OpenSSL TLS Security Level"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(6.dp))
                val tlsValues = listOf(-1, 0, 1, 2)
                SeiSegmented(
                    options = listOf(
                        sei( "Auto"),
                        sei( "Legacy (0)"),
                        sei( "Standard (1)"),
                        sei( "Strict (2)"),
                    ),
                    selectedIndex = tlsValues.indexOf(options.tlsSecLevel).coerceAtLeast(0),
                    onSelect = { options = options.copy(tlsSecLevel = tlsValues[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                SeiToggleRow(
                    title = sei( "Console / Admin Session (/admin)"),
                    subtitle = sei(
                        "Connect to the physical console or Windows Server administrator session",
                    ),
                    checked = options.consoleAdmin,
                    onCheckedChange = { options = options.copy(consoleAdmin = it) },
                    leading = SeiIcons.Lock,
                )
                SeiToggleRow(
                    title = sei( "Restricted Admin Mode (/restricted-admin)"),
                    subtitle = sei(
                        "Authenticate via NLA without sending plaintext credentials to the remote host",
                    ),
                    checked = options.restrictedAdmin,
                    onCheckedChange = { options = options.copy(restrictedAdmin = it) },
                    leading = SeiIcons.Lock,
                )
            }

            XySectionLabel(sei( "Wake-on-LAN & SSH Tunnel"))
            SeiCard {
                Text(
                    sei( "Wake-on-LAN (Magic Packet)"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    sei(
                        "Wake the PC from Sleep/Shutdown over LAN or VPN before connecting via RDP.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SeiField(
                    value = options.macAddress.orEmpty(),
                    onValueChange = { options = options.copy(macAddress = it.trim().ifEmpty { null }) },
                    label = sei( "PC network card MAC Address (optional)"),
                    hint = "AA:BB:CC:DD:EE:FF",
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiField(
                        value = options.wolBroadcast,
                        onValueChange = { options = options.copy(wolBroadcast = it.trim()) },
                        label = sei( "Broadcast / target IP"),
                        hint = "255.255.255.255",
                        modifier = Modifier.weight(1.4f),
                    )
                    SeiField(
                        value = options.wolPort.toString(),
                        onValueChange = {
                            val p = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 9
                            options = options.copy(wolPort = p.coerceIn(1, 65535))
                        },
                        label = sei( "UDP Port"),
                        hint = "9",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(0.7f),
                    )
                }
                if (!options.macAddress.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    SeiPillButton(
                        text = sei( "Send Test Magic Packet Now"),
                        onClick = {
                            val mac = options.macAddress.orEmpty()
                            if (WakeOnLan.parseMacBytes(mac) == null) {
                                wolStatusText = seiNow(
                                    "Invalid MAC address (e.g. AA:BB:CC:DD:EE:FF)",
                                )
                            } else {
                                wolStatusText = seiNow( "Sending Magic Packet...")
                                scope.launch {
                                    val endpointHost = parseRdpEndpoint(host)?.host
                                    val targets = buildList {
                                        add(options.wolBroadcast.ifBlank { "255.255.255.255" })
                                        if (!endpointHost.isNullOrBlank()) add(endpointHost)
                                    }.distinct()
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            targets.forEach { target ->
                                                WakeOnLan.sendMagicPacket(mac, target, options.wolPort)
                                            }
                                        }
                                    }
                                    wolStatusText = if (result.isSuccess) {
                                        seiNow(
                                            "Magic Packet sent to ${targets.joinToString()} (UDP ${options.wolPort})",
                                        )
                                    } else {
                                        seiNow(
                                            "Failed to send Magic Packet: ${result.exceptionOrNull()?.message ?: "error"}",
                                        )
                                    }
                                }
                            }
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    wolStatusText?.let { msg ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    sei( "SSH Jump-Host / Port Forwarding Helper"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    sei(
                        "If the PC sits behind a firewall/NAT and is reached via a local SSH tunnel (e.g. Termux), save jump-host details here to generate the port-forwarding command.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SeiField(
                    value = options.sshHost.orEmpty(),
                    onValueChange = { options = options.copy(sshHost = it.trim().ifEmpty { null }) },
                    label = sei( "SSH Jump Host"),
                    hint = "ssh.kantor.com",
                )
                if (!options.sshHost.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeiField(
                            value = options.sshUser.orEmpty(),
                            onValueChange = { options = options.copy(sshUser = it.trim().ifEmpty { null }) },
                            label = sei( "SSH User"),
                            hint = "root",
                            modifier = Modifier.weight(1.3f),
                        )
                        SeiField(
                            value = options.sshPort.toString(),
                            onValueChange = {
                                val p = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 22
                                options = options.copy(sshPort = p.coerceIn(1, 65535))
                            },
                            label = sei( "SSH Port"),
                            hint = "22",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(0.7f),
                        )
                    }
                    val parsedEndpoint = parseRdpEndpoint(host)
                    val sshCmd = WakeOnLan.buildSshTunnelCommand(
                        targetHost = parsedEndpoint?.host ?: "127.0.0.1",
                        targetPort = parsedEndpoint?.port ?: 3389,
                        options = options,
                    )
                    if (!sshCmd.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            sshCmd,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        SeiPillButton(
                            text = sei( "Copy SSH Command"),
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("SSH Tunnel", sshCmd))
                                wolStatusText = seiNow( "SSH command copied to clipboard.")
                            },
                            primary = false,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            XySectionLabel(sei( "Gateway"))
            SeiCard {
                SeiToggleRow(
                    title = sei( "Use RDP Gateway"),
                    subtitle = sei( "RD Gateway for office networks / no direct port"),
                    checked = gatewayOn,
                    onCheckedChange = { gatewayOn = it },
                    leading = SeiIcons.Lock,
                )
                if (gatewayOn) {
                    Spacer(Modifier.height(6.dp))
                    SeiField(
                        value = gwHost,
                        onValueChange = { gwHost = it.trim() },
                        label = sei( "Gateway host"),
                        hint = "gw.kantor.com",
                    )
                    Spacer(Modifier.height(12.dp))
                    SeiField(
                        value = gwPort,
                        onValueChange = { gwPort = it.filter(Char::isDigit).take(5) },
                        label = sei( "Gateway port"),
                        hint = "443",
                        keyboardType = KeyboardType.Number,
                    )
                    Spacer(Modifier.height(12.dp))
                    SeiField(
                        value = gwUser,
                        onValueChange = { gwUser = it },
                        label = sei( "Gateway username (optional)"),
                    )
                    Spacer(Modifier.height(12.dp))
                    SeiField(
                        value = gwPass,
                        onValueChange = { gwPass = it },
                        label = sei( "Gateway password (optional)"),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                    )
                    Spacer(Modifier.height(12.dp))
                    SeiField(
                        value = gwDomain,
                        onValueChange = { gwDomain = it },
                        label = sei( "Gateway domain (optional)"),
                    )
                }
            }
            }

            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SeiPillButton(
                text = sei( "Save"),
                onClick = { submit(false) },
                primary = false,
                modifier = Modifier.weight(1f),
            )
            SeiPillButton(
                text = sei( "Save & connect"),
                onClick = { submit(true) },
                modifier = Modifier.weight(1.4f),
            )
        }
    }

    if (confirmDiscard) {
        SeiDialog(
            title = sei( "Discard changes?"),
            body = sei(
                "Changes to this device configuration have not been saved. Leave without saving?",
            ),
            confirmLabel = sei( "Discard"),
            onConfirm = {
                confirmDiscard = false
                onCancel()
            },
            dismissLabel = sei( "Keep editing"),
            onDismiss = { confirmDiscard = false },
        )
    }

    validationPopup?.let { msg ->
        SeiDialog(
            title = sei( "Validation error"),
            body = msg,
            confirmLabel = sei( "Got it"),
            onConfirm = { validationPopup = null },
            onDismiss = { validationPopup = null },
        )
    }

    if (lanScanModalOpen) {
        SeiOverlay(
            title = if (isPcQuickMode) {
                sei( "Scan PCs on Wi-Fi Network (LAN)")
            } else {
                sei( "Scan RDP Hosts on Local Network")
            },
            onDismiss = { lanScanModalOpen = false },
        ) {
            Text(
                text = if (!lanSelfIp.isNullOrBlank()) {
                    sei( "Your Phone IP: {0} · Scanning port 3389 on local subnet", lanSelfIp!!)
                } else {
                    sei( "Make sure your phone and PC are on the same Wi-Fi / Hotspot network.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lanScanning) {
                Text(
                    text = sei( "Scanning 254 local addresses in parallel..."),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (lanFoundHosts.isEmpty()) {
                Text(
                    text = sei(
                        "No active PC found on this subnet yet. Ensure the PC is powered on and XyDeskHost / Remote Desktop (Port 3389) is enabled.",
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
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = if (isPcQuickMode) {
                                        sei( "Endpoint: {0}:{1} · Tap to use this ID", item.ip, item.port)
                                    } else {
                                        sei( "PC ID: {0} · Tap to use this IP", item.pcId)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${item.rttMs} ms",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiPillButton(
                    text = sei( "Rescan"),
                    onClick = { triggerLanScan() },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SeiPillButton(
                    text = sei( "Close"),
                    onClick = { lanScanModalOpen = false },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    }
}

/**
 * Status akses storage untuk redirect drive.
 *
 * Android 11+ hanya mengizinkan app membaca folder miliknya sendiri; kalau
 * user mau seluruh isi /storage/emulated/0, izin "semua file" harus diminta
 * lewat halaman Setelan (tidak bisa lewat dialog runtime biasa).
 */
@Composable
private fun StorageAccessRow() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LibFreeRDP.hasAllFilesAccess()) }
    val path = remember(granted) { LibFreeRDP.appDrivePath(context) }
    // Cek ulang izin saat layar ini kembali ke depan. Dulu `granted` hanya
    // dibaca sekali tepat setelah startActivity — padahal activity asal
    // tidak pernah pause-hilang di momen itu, jadi status selalu "terbatas"
    // walaupun user baru saja memberi izin di Setelan.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = LibFreeRDP.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(
            if (granted) {
                sei( "Full access: the remote reads all phone storage")
            } else {
                sei( "Limited access: only the app folder is visible to the remote")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Path HP: $path",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            sei(
                "How to transfer files on PC: Open Windows File Explorer -> This PC -> open 'XyDesk on Android' drive (or type \\\\tsclient\\XyDesk in the address bar) for two-way file copy/paste.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!granted) {
            Spacer(Modifier.height(8.dp))
            SeiPillButton(
                text = sei( "Grant all-files access"),
                onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    } else {
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    }
                    runCatching { context.startActivity(intent) }
                    granted = LibFreeRDP.hasAllFilesAccess()
                },
                primary = false,
                compact = true,
            )
        }
    }
}

/** Baris slider berlabel untuk pengaturan DSP mic. */
@Composable
private fun XySliderRow(
    label: String,
    value: Float,
    suffix: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${value.toInt()}$suffix",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SeiSlider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}
