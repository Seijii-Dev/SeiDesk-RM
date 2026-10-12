package io.seidesk.rm.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.seidesk.rm.core.ConnectionProfile
import io.seidesk.rm.core.SeiSecurityProtocol
import io.seidesk.rm.core.SeiStreamProfile
import io.seidesk.rm.security.CredentialVault
import io.seidesk.rm.core.coreBuildInfo
import io.seidesk.rm.ui.components.SeiSlider
import io.seidesk.rm.ui.components.SeiCard
import io.seidesk.rm.ui.components.SeiDialog
import io.seidesk.rm.ui.components.SeiIconPill
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiLogo
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiRow
import io.seidesk.rm.ui.components.XySectionLabel
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.components.SeiTopBar
import io.seidesk.rm.ui.theme.SeiPill

/** Router seksi drawer (selain "Perangkat" yang punya layarnya sendiri). */
@Composable
internal fun SectionScreen(
    section: SeiSection,
    favorites: List<ConnectionProfile>,
    onMenu: () -> Unit,
    appPrefs: AppPrefs,
    onEditDevice: (ConnectionProfile) -> Unit,
    onClearAllCredentials: () -> Unit,
    onShowLog: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SeiTopBar(
            title = sectionTitle(section),
            onBack = null,
            actions = { SeiIconPill(SeiIcons.Menu, onMenu, flat = true, contentDescription = "Menu") },
        )
        when (section) {
            SeiSection.TAMPILAN -> DisplaySection(favorites, onEditDevice)
            SeiSection.KREDENSIAL -> CredentialsSection(favorites, onClearAllCredentials)
            SeiSection.UMUM -> GeneralSection(appPrefs, onShowLog)
            SeiSection.KEAMANAN -> SecuritySection(appPrefs)
            SeiSection.TENTANG -> AboutSection()
            SeiSection.PERANGKAT -> Unit
        }
    }
}

// =============================================================
// Tampilan — resolusi, orientasi, skala per perangkat
// =============================================================

@Composable
private fun DisplaySection(
    favorites: List<ConnectionProfile>,
    onEditDevice: (ConnectionProfile) -> Unit,
) {
    val context = LocalContext.current
    if (favorites.isEmpty()) {
        EmptyHint(
            title = sei( "No devices yet"),
            body = sei(
                "Add a device first, then set its display options here.",
            ),
        )
        return
    }
    var index by remember { mutableIntStateOf(0) }
    val profile = favorites[index.coerceIn(0, favorites.lastIndex)]
    var resolution by remember(profile.id) {
        mutableStateOf(DisplayPrefs.resolution(context, profile.id))
    }
    var rotation by remember(profile.id) {
        mutableStateOf(DisplayPrefs.rotation(context, profile.id))
    }
    var scale by remember(profile.id) {
        mutableIntStateOf(DisplayPrefs.dpi(context, profile.id))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(sei( "Devices"))
        SeiSegmented(
            options = favorites.map { it.label ?: it.host },
            selectedIndex = index.coerceIn(0, favorites.lastIndex),
            onSelect = { index = it },
            modifier = Modifier.fillMaxWidth(),
        )
        SeiCard {
            SeiRow(
                title = profile.label ?: profile.host,
                subtitle = "${profile.host}:${profile.port}",
                trailing = {
                    SeiIconPill(
                        SeiIcons.Gear,
                        { onEditDevice(profile) },
                        size = 38.dp,
                        contentDescription = sei( "Edit device"),
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            XySectionLabel(sei( "Remote desktop resolution"))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DisplayPrefs.resolutionOptions.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { colIndex, option ->
                            val value = option.value
                            SeiPillButton(
                                text = sei( option.en),
                                onClick = {
                                    resolution = value
                                    DisplayPrefs.setResolution(context, profile.id, value)
                                },
                                primary = value == resolution,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            XySectionLabel(sei( "Orientation"))
            Spacer(Modifier.height(8.dp))
            SeiSegmented(
                options = DisplayPrefs.rotations,
                selectedIndex = DisplayPrefs.rotations.indexOf(rotation).coerceAtLeast(0),
                onSelect = {
                    rotation = DisplayPrefs.rotations[it]
                    DisplayPrefs.setRotation(context, profile.id, rotation)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            XySectionLabel(sei( "Initial local zoom"))
            Text(
                "$scale%",
                style = MaterialTheme.typography.titleMedium,
            )
            SeiSlider(
                value = scale.toFloat(),
                onValueChange = {
                    scale = it.toInt()
                    DisplayPrefs.setDpi(context, profile.id, scale)
                },
                valueRange = 80f..200f,
                steps = 11,
            )
            Text(
                sei(
                    "Local zoom can be changed from the session panel without disconnecting. Windows " +
                        "desktop DPI and remote resolution each have their own controls.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// =============================================================
// Kredensial
// =============================================================

@Composable
private fun CredentialsSection(
    favorites: List<ConnectionProfile>,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val vault = remember { CredentialVault(context.applicationContext) }
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmForgetProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    // Flag "password tersimpan?" dihitung ulang saat `tick` naik (tombol
    // Lupakan ditekan) atau daftar perangkat berubah. Dulu ini pakai trik
    // `if (tick < 0) Text("")` di ujung layar untuk memaksa recompose.
    val rememberedFlags = remember(tick, favorites) { favorites.map { vault.has(it.id) } }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(sei( "Saved passwords"))
        SeiCard {
            Text(
                sei(
                    "Passwords are encrypted with AES-256-GCM; the key cannot leave this " +
                        "device's Android Keystore.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (favorites.isEmpty()) {
                Text(sei( "No saved devices yet."), style = MaterialTheme.typography.bodyMedium)
            } else {
                favorites.forEachIndexed { idx, profile ->
                    val remembered = rememberedFlags[idx]
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile.label ?: profile.host,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                buildString {
                                    append(profile.username ?: sei( "no username"))
                                    append(
                                        if (remembered) sei( "  ·  password saved")
                                        else sei( "  ·  password not saved"),
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (remembered) {
                            SeiPillButton(
                                text = sei( "Forget"),
                                onClick = { confirmForgetProfile = profile },
                                primary = false,
                                compact = true,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        SeiCard {
            SeiRow(
                title = sei( "Delete all credentials and devices"),
                subtitle = sei( "Vault cleared and device list wiped"),
                leading = SeiIcons.Trash,
                onClick = { confirmClear = true },
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    confirmForgetProfile?.let { target ->
        SeiDialog(
            title = sei( "Forget password?"),
            body = sei(
                "Remove the saved password for \"{0}\"? You will be asked to enter it again on the next connection.",
                target.label ?: "${target.host}:${target.port}",
            ),
            confirmLabel = sei( "Forget"),
            onConfirm = {
                confirmForgetProfile = null
                vault.remove(target.id)
                tick++
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmForgetProfile = null },
        )
    }

    if (confirmClear) {
        SeiDialog(
            title = sei( "Delete everything?"),
            body = sei( "All saved devices and their passwords will be removed."),
            confirmLabel = sei( "Delete"),
            onConfirm = {
                confirmClear = false
                onClearAll()
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmClear = false },
        )
    }
    }
}

// =============================================================
// Umum — tema, kontrol default, sesi
// =============================================================

@Composable
private fun GeneralSection(
    appPrefs: AppPrefs,
    onShowLog: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { SessionPrefs(context) }
    var hudSize by remember { mutableStateOf(prefs.hudButtonSize) }
    var autoFit by remember { mutableStateOf(prefs.autoFit) }
    var defaultUdp by remember { mutableStateOf(appPrefs.defaultUdp) }
    var defaultNetAuto by remember { mutableStateOf(appPrefs.defaultNetAuto) }
    var defaultH264 by remember { mutableStateOf(appPrefs.defaultH264) }
    var defaultDynRes by remember { mutableStateOf(appPrefs.defaultDynamicResolution) }
    var defaultClipboard by remember { mutableStateOf(appPrefs.defaultClipboard) }
    var defaultDrive by remember { mutableStateOf(appPrefs.defaultLocalDrive) }
    var defaultStreamProfile by remember {
        mutableStateOf(
            SeiStreamProfile.entries.getOrElse(appPrefs.defaultStreamProfile) { SeiStreamProfile.AUTO }
        )
    }
    var defaultAsyncUpdate by remember { mutableStateOf(appPrefs.defaultAsyncUpdate) }
    var defaultAsyncChannels by remember { mutableStateOf(appPrefs.defaultAsyncChannels) }
    var themeMode by remember { mutableIntStateOf(appPrefs.themeMode) }
    var autoDisconnect by remember { mutableStateOf(appPrefs.autoDisconnect) }
    var autoResumeLastSession by remember { mutableStateOf(appPrefs.autoResumeLastSession) }
    var keepSystemBarsHiddenOnIme by remember {
        mutableStateOf(appPrefs.keepSystemBarsHiddenWhenKeyboardOpens)
    }
    var inputMode by remember { mutableIntStateOf(prefs.inputMode.ordinal) }
    var pointerStyle by remember { mutableIntStateOf(prefs.pointerStyle.ordinal) }
    var pointerSize by remember { mutableStateOf(prefs.pointerSize) }
    var scrollSpeed by remember { mutableStateOf(prefs.scrollSpeed) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var showLeft by remember { mutableStateOf(prefs.showLeft) }
    var showRight by remember { mutableStateOf(prefs.showRight) }
    var showMiddle by remember { mutableStateOf(prefs.showMiddle) }
    var showScroll by remember { mutableStateOf(prefs.showScroll) }
    var showSwitch by remember { mutableStateOf(prefs.showSwitch) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(sei( "App appearance"))
        SeiCard {
            SeiSegmented(
                options = listOf(sei( "Follow system"), sei( "Dark"), sei( "Light")),
                selectedIndex = themeMode,
                onSelect = {
                    themeMode = it
                    // Tanpa recreate(): state tema reaktif, semua layar
                    // recompose sendiri (dulu seluruh activity di-recreate,
                    // layar kedip dan posisi scroll hilang).
                    io.seidesk.rm.ui.theme.SeiThemeState.set(appPrefs, it)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            SeiToggleRow(
                title = sei( "Disconnect when app goes background"),
                subtitle = sei(
                    "Avoid a dangling session while the phone does something else",
                ),
                checked = autoDisconnect,
                onCheckedChange = {
                    autoDisconnect = it
                    appPrefs.autoDisconnect = it
                },
            )
        }

        XySectionLabel(sei( "Last session"))
        SeiCard {
            SeiToggleRow(
                title = sei( "Automatically resume the last session"),
                subtitle = sei(
                    "Reopen the last profile within 15 minutes after an unexpected session close. If its password isn't saved, you'll be prompted. Manual Disconnect will not reconnect.",
                ),
                checked = autoResumeLastSession,
                onCheckedChange = {
                    autoResumeLastSession = it
                    appPrefs.autoResumeLastSession = it
                },
            )
        }

        XySectionLabel(sei( "Session controls"))
        SeiCard {
            Text(sei( "Default input mode"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SeiSegmented(
                options = InputMode.entries.map { it.title },
                selectedIndex = inputMode,
                onSelect = {
                    inputMode = it
                    prefs.inputMode = InputMode.entries[it]
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                InputMode.entries[inputMode].detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(sei( "Pointer"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SeiSegmented(
                options = PointerStyle.entries.map { sei( it.titleEn) },
                selectedIndex = pointerStyle,
                onSelect = {
                    pointerStyle = it
                    prefs.pointerStyle = PointerStyle.entries[it]
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                sei( "Pointer size: {0} dp", pointerSize.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeiSlider(
                value = pointerSize,
                onValueChange = {
                    pointerSize = it
                    prefs.pointerSize = it
                },
                valueRange = 10f..52f,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                sei(
                    "Scroll speed: {0}x",
                    "%.1f".format(scrollSpeed),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeiSlider(
                value = scrollSpeed,
                onValueChange = {
                    scrollSpeed = it
                    prefs.scrollSpeed = it
                },
                valueRange = 0.4f..2.5f,
            )
            SeiToggleRow(
                title = sei( "Haptic feedback on button press"),
                subtitle = sei( "Subtle haptics on the mouse cluster and keyboard"),
                checked = haptics,
                onCheckedChange = {
                    haptics = it
                    prefs.haptics = it
                },
            )
        }

        XySectionLabel(sei( "HUD buttons"))
        SeiCard {
            Text(
                sei(
                    "Every on-screen button stands alone: round, resizable, and " +
                        "with its own action (single tap / " +
                        "hold / toggle). Dragging only works while \"Edit layout\" is on, " +
                        "so buttons cannot move by accident while you use them. The " +
                        "defaults below seed devices that were never customised.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeiToggleRow(
                title = sei( "Left button"),
                checked = showLeft,
                onCheckedChange = { showLeft = it; prefs.showLeft = it },
            )
            SeiToggleRow(
                title = sei( "Right button"),
                checked = showRight,
                onCheckedChange = { showRight = it; prefs.showRight = it },
            )
            SeiToggleRow(
                title = sei( "Middle button"),
                checked = showMiddle,
                onCheckedChange = { showMiddle = it; prefs.showMiddle = it },
            )
            SeiToggleRow(
                title = sei( "Scroll up/down"),
                checked = showScroll,
                onCheckedChange = { showScroll = it; prefs.showScroll = it },
            )
            SeiToggleRow(
                title = sei( "Input-mode switch button"),
                subtitle = sei(
                    "Switch trackpad <-> direct touch from the session screen",
                ),
                checked = showSwitch,
                onCheckedChange = { showSwitch = it; prefs.showSwitch = it },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                sei(
                    "Cluster position is set by dragging it on the session screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        XySectionLabel(sei( "Keyboard"))
        SeiCard {
            Text(
                sei(
                    "Typing uses the phone keyboard (IME). There is no built-in " +
                        "virtual keyboard and no toolbar above it: every control is a " +
                        "separate overlay button on the session screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SeiToggleRow(
                title = sei( "Fit whole desktop"),
                subtitle = sei( "Taskbar and desktop edges always stay visible"),
                checked = autoFit,
                onCheckedChange = { autoFit = it; prefs.autoFit = it },
            )
            Spacer(Modifier.height(8.dp))
            SeiToggleRow(
                title = sei( "Keep system bars hidden while typing"),
                subtitle = sei(
                    "Re-hide the status bar and notch icons when the phone keyboard opens in a session. Helps on Android 8+; the system may still show them briefly.",
                ),
                checked = keepSystemBarsHiddenOnIme,
                onCheckedChange = {
                    keepSystemBarsHiddenOnIme = it
                    appPrefs.keepSystemBarsHiddenWhenKeyboardOpens = it
                },
            )
        }

        XySectionLabel(sei( "Keyboard & HUD"))
        SeiCard {
            Text(
                sei( "Default HUD button size: {0} dp", hudSize.toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sei(
                    "New buttons and restored default layouts use this size. " +
                        "Existing custom buttons keep their individual sizes.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeiSlider(
                value = hudSize,
                onValueChange = { hudSize = it; prefs.hudButtonSize = it },
                valueRange = 56f..80f,
            )
        }


        XySectionLabel(sei( "Streaming & Latency (new device defaults)"))
        SeiCard {
            Text(
                sei(
                    "These values are used when adding a new device. Existing devices " +
                        "keep their own settings on the Edit device screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(sei( "Default streaming profile"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            val streamOptions = listOf(
                SeiStreamProfile.AUTO,
                SeiStreamProfile.BALANCED,
                SeiStreamProfile.ULTRA_LOW_LATENCY,
                SeiStreamProfile.HIGH_VISUAL,
                SeiStreamProfile.DATA_SAVER,
            )
            streamOptions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { preset ->
                        SeiPillButton(
                            text = sei( preset.titleEn),
                            onClick = {
                                defaultStreamProfile = preset
                                appPrefs.defaultStreamProfile = preset.ordinal
                            },
                            primary = defaultStreamProfile == preset,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }
            SeiToggleRow(
                title = sei( "UDP transport (RDP-UDP + FEC)"),
                subtitle = sei( "Multitransport UDP channel for smoother motion"),
                checked = defaultUdp,
                onCheckedChange = { defaultUdp = it; appPrefs.defaultUdp = it },
            )
            SeiToggleRow(
                title = sei( "Automatic bandwidth detection"),
                subtitle = sei( "Quality adapts dynamically to network conditions"),
                checked = defaultNetAuto,
                onCheckedChange = { defaultNetAuto = it; appPrefs.defaultNetAuto = it },
            )
            SeiToggleRow(
                title = sei( "H.264 / RemoteFX (GFX)"),
                subtitle = sei( "H.264 hardware acceleration for 60 FPS video & motion"),
                checked = defaultH264,
                onCheckedChange = { defaultH264 = it; appPrefs.defaultH264 = it },
            )
            SeiToggleRow(
                title = sei( "Async Frame Update (+async-update)"),
                subtitle = sei( "Decouple frame rendering from input queue"),
                checked = defaultAsyncUpdate,
                onCheckedChange = { defaultAsyncUpdate = it; appPrefs.defaultAsyncUpdate = it },
            )
            SeiToggleRow(
                title = sei( "Async Input & Channels (+async-channels)"),
                subtitle = sei( "Process input & channels on dedicated threads"),
                checked = defaultAsyncChannels,
                onCheckedChange = { defaultAsyncChannels = it; appPrefs.defaultAsyncChannels = it },
            )
            SeiToggleRow(
                title = sei( "Dynamic resolution"),
                subtitle = sei( "Change remote desktop size live (DISP channel)"),
                checked = defaultDynRes,
                onCheckedChange = { defaultDynRes = it; appPrefs.defaultDynamicResolution = it },
            )
            SeiToggleRow(
                title = sei( "Two-way clipboard (Text & Image)"),
                checked = defaultClipboard,
                onCheckedChange = { defaultClipboard = it; appPrefs.defaultClipboard = it },
            )
            SeiToggleRow(
                title = sei( "Local storage (drive)"),
                subtitle = sei( "Phone storage shows up as a remote drive"),
                checked = defaultDrive,
                onCheckedChange = { defaultDrive = it; appPrefs.defaultLocalDrive = it },
            )
        }

        XySectionLabel(sei( "Connection issues"))
        SeiCard {
            Text(
                sei(
                    "Audio, microphone, clipboard, drive, camera and gateway are set " +
                        "per device on the Add/Edit device screen.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                sei( "Core: {0}", coreBuildInfo()),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            SeiPillButton(
                sei( "View last session log"),
                onShowLog,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// =============================================================
// Keamanan
// =============================================================

@Composable
private fun SecuritySection(appPrefs: AppPrefs) {
    val context = LocalContext.current
    val store = remember { CertificateTrustStore(context.applicationContext) }
    var tick by remember { mutableIntStateOf(0) }
    var showTrustedCertificates by remember { mutableStateOf(false) }
    var confirmRemoveCertHost by remember { mutableStateOf<String?>(null) }
    var confirmClearAllCerts by remember { mutableStateOf(false) }
    var screenCaptureProtection by remember { mutableStateOf(appPrefs.flagSecure) }
    var requireDeviceLock by remember { mutableStateOf(appPrefs.requireDeviceLock) }
    var autoLockRemoteOnLeave by remember { mutableStateOf(appPrefs.autoLockRemoteOnLeave) }
    var clearClipboardOnDisconnect by remember { mutableStateOf(appPrefs.clearClipboardOnDisconnect) }
    var shareActiveSessionStats by remember { mutableStateOf(appPrefs.shareActiveSessionStats) }
    var defaultSecurityProtocol by remember {
        mutableStateOf(
            SeiSecurityProtocol.entries.getOrElse(appPrefs.defaultSecurityProtocol) { SeiSecurityProtocol.AUTO }
        )
    }
    var defaultTlsSecLevel by remember { mutableIntStateOf(appPrefs.defaultTlsSecLevel) }
    val entries = remember(tick) { store.entries() }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        XySectionLabel(sei( "Screen & Session Protection"))
        SeiCard {
            SeiToggleRow(
                title = sei( "Screen capture protection (FLAG_SECURE)"),
                subtitle = sei(
                    "Hide the PC preview in Android recents & block third-party screen recording",
                ),
                checked = screenCaptureProtection,
                onCheckedChange = {
                    screenCaptureProtection = it
                    appPrefs.screenCaptureProtection = it
                },
            )
            SeiToggleRow(
                title = sei( "Require device lock (Biometric / PIN)"),
                subtitle = sei(
                    "Prompt for phone fingerprint, face, or PIN before launching a remote session",
                ),
                checked = requireDeviceLock,
                onCheckedChange = {
                    requireDeviceLock = it
                    appPrefs.requireDeviceLock = it
                },
            )
            SeiToggleRow(
                title = sei( "Auto-lock PC on session exit (Win+L)"),
                subtitle = sei(
                    "Automatically send Windows lock screen command right before disconnecting",
                ),
                checked = autoLockRemoteOnLeave,
                onCheckedChange = {
                    autoLockRemoteOnLeave = it
                    appPrefs.autoLockRemoteOnLeave = it
                },
            )
            SeiToggleRow(
                title = sei( "Clear phone clipboard on disconnect"),
                subtitle = sei(
                    "Wipe synced text/images from the phone clipboard after the session closes",
                ),
                checked = clearClipboardOnDisconnect,
                onCheckedChange = {
                    clearClipboardOnDisconnect = it
                    appPrefs.clearClipboardOnDisconnect = it
                },
            )
        }

        XySectionLabel(sei( "Active session status"))
        SeiCard {
            SeiToggleRow(
                title = sei( "Count active sessions anonymously"),
                subtitle = sei(
                    "On automatically. Turn this switch off any time to stop sending session heartbeats.",
                ),
                checked = shareActiveSessionStats,
                onCheckedChange = {
                    shareActiveSessionStats = it
                    appPrefs.shareActiveSessionStats = it
                },
            )
            Text(
                sei(
                    "Only a temporary random session ID (new for each connection) is sent—not the PC address, profile, username, password, clipboard, or desktop image. Cloudflare still processes network metadata such as IP. The public figure estimates client sessions sending heartbeats, not people; leases expire after 90 seconds. Turn this switch off any time.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }

        XySectionLabel(sei( "Default Protocol & Encryption"))
        SeiCard {
            Text(
                sei( "Default security protocol (new devices)"),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            val secProtocols = SeiSecurityProtocol.entries
            secProtocols.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { proto ->
                        SeiPillButton(
                            text = sei( proto.titleEn),
                            onClick = {
                                defaultSecurityProtocol = proto
                                appPrefs.defaultSecurityProtocol = proto.ordinal
                            },
                            primary = defaultSecurityProtocol == proto,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                sei( "Default OpenSSL TLS security level"),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            SeiSegmented(
                options = listOf(
                    sei( "Legacy (0)"),
                    sei( "Standard (1)"),
                    sei( "Strict (2)"),
                ),
                selectedIndex = defaultTlsSecLevel.coerceIn(0, 2),
                onSelect = {
                    defaultTlsSecLevel = it
                    appPrefs.defaultTlsSecLevel = it
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sei(
                    "Level 1 is the modern default. Use Level 0 for older Windows Servers, or Level 2 for strict ciphers.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        XySectionLabel(sei( "Credential storage"))
        SeiCard {
            Bullet(sei(
                "Passwords: AES-256-GCM, non-exportable key in the Android Keystore"))
            Bullet(sei(
                "Passwords never end up in app logs or in logged URIs"))
            Bullet(sei(
                "App backup & transfer are disabled (allowBackup=false)"))
            Bullet(sei(
                "No credentials are sent to any third-party service"))
        }
        XySectionLabel(sei( "Trusted server certificates"))
        SeiCard {
            Row(Modifier.fillMaxWidth().clickable { showTrustedCertificates = !showTrustedCertificates },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${if (showTrustedCertificates) "▾" else "▸"} ${sei( "Trusted list ({0})", entries.size)}",
                    style = MaterialTheme.typography.titleSmall)
            }
            if (showTrustedCertificates && entries.isEmpty()) {
                Text(
                    sei(
                        "No certificate has been trusted yet. A certificate is only " +
                            "remembered when you press \"Trust & remember\" while connecting.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (showTrustedCertificates) {
                entries.take(12).forEach { (host, fingerprint) ->
                    Row(
                        Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(host, style = MaterialTheme.typography.titleSmall)
                            Text(
                                fingerprint,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Hapus per entri — dulu satu-satunya jalan adalah
                        // menghapus SEMUA kepercayaan sertifikat.
                        Box(
                            Modifier
                                .clip(SeiPill)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SeiPill)
                                .clickable { confirmRemoveCertHost = host }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                sei( "Delete"),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SeiPillButton(
                    text = sei( "Clear all trusted certificates"),
                    onClick = { confirmClearAllCerts = true },
                    primary = false,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    confirmRemoveCertHost?.let { targetHost ->
        SeiDialog(
            title = sei( "Remove trusted certificate?"),
            body = sei(
                "Remove certificate trust for \"{0}\"? This server will prompt for verification again on the next connection.",
                targetHost,
            ),
            confirmLabel = sei( "Remove"),
            onConfirm = {
                confirmRemoveCertHost = null
                store.removeKey(targetHost)
                tick++
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmRemoveCertHost = null },
        )
    }

    if (confirmClearAllCerts) {
        SeiDialog(
            title = sei( "Clear all certificates?"),
            body = sei(
                "Clear the entire trusted server certificate list? Every server will prompt for verification again on connect.",
            ),
            confirmLabel = sei( "Clear all"),
            onConfirm = {
                confirmClearAllCerts = false
                store.clear()
                tick++
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmClearAllCerts = false },
        )
    }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(
        Modifier.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .height(5.dp)
                .clip(SeiPill)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
                .fillMaxWidth(0.014f),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// =============================================================
// Tentang
// =============================================================

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    val native = remember {
        runCatching { com.freerdp.freerdpcore.services.LibFreeRDP.getVersion() }.getOrNull()
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SeiCard {
            SeiLogo(modifier = Modifier.height(38.dp).fillMaxWidth(0.16f))
            Spacer(Modifier.height(12.dp))
            Text(sei( "SeiDesk RM"), style = MaterialTheme.typography.headlineSmall)
            Text(
                t("app.tagline"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SeiRow(title = t("about.version"), subtitle = appVersionName(context))
            SeiRow(
                title = t("about.engine"),
                subtitle = native ?: sei( "FreeRDP (loaded on connect)"),
            )
            SeiRow(
                title = t("about.developer"),
                subtitle = sei(
                    "Powered by ${AppBrand.PUBLISHER}",
                ),
            )
        }

        SupportCard()

        SeiCard {
            Text(
                sei(
                    "SeiDesk RM is built on FreeRDP (Apache License 2.0). The app " +
                        "code belongs to ${AppBrand.PUBLISHER}. The list of changes to the FreeRDP " +
                        "tree is in SEIDESK-NOTICE.md.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            SeiPillButton(t("about.license"), { showLicense = true }, primary = false)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showLicense) {
        SeiDialog(
            title = t("about.license.title"),
            body = t("about.license.body"),
            confirmLabel = "OK",
            onConfirm = { showLicense = false },
        )
    }
    }
}

/**
 * sei( "Support me") — Saweria, GitHub Sponsors, dan bintang repo.
 *
 * Semua tautan dibuka lewat browser (Intent VIEW), tanpa SDK pihak ketiga
 * dan tanpa jaringan dari dalam app sendiri.
 */
@Composable
internal fun SupportCard() {
    val context = LocalContext.current
    SeiCard {
        XySectionLabel(t("about.support.title").uppercase())
        Text(
            t("about.support.body"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportLink(
                icon = SeiIcons.Heart,
                title = t("about.support.saweria"),
                subtitle = "saweria.co/kallsptra",
                url = "https://saweria.co/kallsptra",
            )
            SupportLink(
                icon = SeiIcons.Sponsor,
                title = t("about.support.github"),
                subtitle = "github.com/sponsors/xykal",
                url = "https://github.com/sponsors/xykal",
            )
            SupportLink(
                icon = SeiIcons.Star,
                title = t("about.support.star"),
                subtitle = "github.com/Seijii-Dev/SeiDesk-RM",
                url = "https://github.com/Seijii-Dev/SeiDesk-RM/stargazers",
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            t("about.thanks"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SupportLink(icon: ImageVector, title: String, subtitle: String, url: String) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                runCatching { context.startActivity(intent) }
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            SeiIcons.ExternalLink,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(16.dp).width(16.dp),
        )
    }
}

@Composable
private fun EmptyHint(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        SeiCard(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    SeiDialog(
        title = title,
        body = body,
        confirmLabel = sei( "Close"),
        onConfirm = onDismiss,
        dismissLabel = null,
        onDismiss = onDismiss,
        mono = true,
    )
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"
