package io.seidesk.rm.ui

import io.seidesk.rm.ui.input.SeiStickMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import android.os.SystemClock
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.SmartResolution
import io.seidesk.rm.ui.components.SeiGlassTile
import io.seidesk.rm.ui.components.SeiDialog
import io.seidesk.rm.ui.components.SeiField
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiNoticeState
import io.seidesk.rm.ui.components.SeiOverlay
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiSlider
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.components.seiSurface
import io.seidesk.rm.ui.theme.SeiPill
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Warna tombol HUD tetap gelap-transparan karena duduk di atas desktop remote;
 * panel, banner editor, dan dialog mengikuti tema app.
 */
internal fun updateHudEditorDraft(current: HudKey?, updated: HudKey): HudKey? =
    if (current?.id == updated.id) updated else current

/** Tab di panel sesi — SATU panel, empat seksi jelas. Dulu dua panel kiri/kanan tanpa label: nobody tahu isinya apa. */
private enum class PanelTab(val id: String, val en: String) {
    SCREEN("Layar", "Screen"),
    INPUT("Input", "Input"),
    BUTTONS("Tombol", "Buttons"),
    SESSION("Sesi", "Session"),
}

/**
 * Kontrol sesi.
 *
 * Model (ronde 8): satu handle di tepi kanan-atas membuka SATU panel bertab —
 * Layar (zoom/resolusi/orientasi), Input (mode/keyboard/clipboard/pointer),
 * Tombol (editor tombol HUD), Sesi (screenshot/putus/info teknis).
 * Rail kanan-bawah tetap terlihat untuk keyboard, monitor, putus, dan toggle
 * visibilitas HUD; auto-hide hanya memengaruhi tombol overlay yang dapat dipindah.
 */
@Composable
fun SessionControls(
    deviceId: String,
    hostLabel: String,
    statusText: String,
    remoteSize: String,
    zoomPercent: Int,
    remoteDpi: Int,
    telemetry: io.seidesk.rm.core.TelemetrySample = io.seidesk.rm.core.TelemetrySample.EMPTY,
    pointerScreen: Offset,
    pointerVisible: Boolean,
    remoteCursor: RemoteCursor? = null,
    zoom: Float = 1f,
    inputMode: InputMode,
    externalInputDeviceConnected: Boolean = false,
    onInputModeChange: (InputMode) -> Unit,
    keys: List<HudKey>,
    onKeysChange: (List<HudKey>) -> Unit,
    latchedKeyIds: Set<String>,
    onKeyEdited: (HudKey) -> Unit,
    onDeleteKey: (HudKey) -> Unit,
    mappingMode: Boolean,
    onMappingModeChange: (Boolean) -> Unit,
    onPhase: (HudKey, HudPhase) -> Unit,
    onScrollUnits: (Int) -> Unit,
    /** Sumbu joystick HUD (HudKind.PAD_STICK), -1..1 per sumbu. */
    onStickAxis: (Float, Float) -> Unit = { _, _ -> },
    /** Stik KANAN preset gamepad selalu jadi pointer, apa pun mode stik. */
    onStickPointer: (Float, Float) -> Unit = { _, _ -> },
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onZoomActual: () -> Unit,
    onZoomScale: (Float) -> Unit,
    onRemoteDpiChange: (Int) -> Unit,
    onDisplayRefreshPreferenceChange: (Int) -> Unit = {},
    recordingLabel: String = "Record PC",
    recordingStatus: String? = null,
    recordingActionEnabled: Boolean = true,
    recordingActive: Boolean = false,
    stickModeProvider: () -> SeiStickMode = { SeiStickMode.POINTER },
    onStickModeChange: (SeiStickMode) -> Unit = {},
    onToggleRecording: () -> Unit = {},
    onScreenshot: () -> Unit,
    onDisconnect: () -> Unit,
    onGoHome: () -> Unit = {},
    onPointerVisibilityChange: (Boolean) -> Unit,
    onResetCluster: () -> Unit,
    onRotationChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onOpenKeyboard: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    lastClipboard: String? = null,
    clipboardSyncEnabled: Boolean = false,
    onSendText: (String) -> Unit,
    onSendRemoteClipboardText: (String) -> Boolean = { false },
    activeUsername: String? = null,
    consoleAdminMode: Boolean = false,
    onSwitchConsoleMode: (Boolean) -> Unit = {},
    onSwitchUserSession: (String, String?, String?) -> Unit = { _, _, _ -> },
    swapMouseButtons: Boolean = false,
    onSwapMouseButtonsChange: (Boolean) -> Unit = {},
    onLockRemotePc: () -> Unit = {},
    onActivatePrivacyCurtain: () -> Unit = {},
    onGyroMouseChanged: (Boolean) -> Unit = {},
    onOverlayActiveChange: (Boolean) -> Unit = {},
    onRegisterBackHandler: ((() -> Boolean)?) -> Unit = {},
    coreInfo: List<String> = emptyList(),
    notice: SeiNoticeState,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val view = androidx.compose.ui.platform.LocalView.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val prefs = remember { SessionPrefs(context) }
    val spotifyPlayback by SpotifyMediaBridge.playback.collectAsState()
    val spotifyPosition by SpotifyMediaBridge.positionMs.collectAsState()
    var spotifyExpanded by remember { mutableStateOf(false) }
    var scrollPillOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var monitorGridOpen by remember { mutableStateOf(false) }
    var railMenuOpen by remember { mutableStateOf(false) }
    var drawerMode by remember { mutableStateOf(prefs.menuDrawer) }
    var tab by remember { mutableStateOf(PanelTab.SCREEN) }
    var pickerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HudKey?>(null) }
    var pointerSize by remember { mutableFloatStateOf(prefs.pointerSize) }
    var pointerStyle by remember { mutableStateOf(prefs.pointerStyle) }
    var pointerSensitivity by remember { mutableFloatStateOf(prefs.pointerSensitivity) }
    var pointerAcceleration by remember { mutableStateOf(prefs.pointerAcceleration) }
    var inertialScroll by remember { mutableStateOf(prefs.inertialScroll) }
    var edgeScrollZone by remember { mutableStateOf(prefs.edgeScrollZone) }
    var gamepadEnabled by remember { mutableStateOf(prefs.gamepadEnabled) }
    // Mode stick dibaca lewat provider karena nilai sesungguhnya hidup di
    // activity (dipakai mapper dari luar komposisi), bukan di prefs saja.
    var stickModeUi by remember { mutableStateOf(stickModeProvider()) }
    var gyroMouseEnabled by remember { mutableStateOf(prefs.gyroMouseEnabled) }
    var showTelemetryPill by remember { mutableStateOf(prefs.showTelemetryPill) }
    var hudProfile by remember(deviceId) { mutableStateOf(prefs.hudProfile(deviceId)) }
    val pcConnectMode = remember(deviceId) { RdpOptions.of(context, deviceId).pcConnectMode }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var autoFit by remember { mutableStateOf(prefs.autoFit) }
    var plate by remember { mutableStateOf(prefs.hudPlate) }
    var hudOpacity by remember { mutableFloatStateOf(prefs.hudOpacity) }
    var showHudButtons by remember(deviceId) { mutableStateOf(prefs.hudButtonsVisible(deviceId)) }
    var autoHideHudOnExternalInput by remember {
        mutableStateOf(prefs.autoHideHudOnExternalInput)
    }
    var forceShowHudForExternalInput by remember(deviceId) { mutableStateOf(false) }
    val hudAutomaticallyHidden = autoHideHudOnExternalInput && externalInputDeviceConnected &&
        !forceShowHudForExternalInput
    val hudButtonsVisibleNow = showHudButtons && !hudAutomaticallyHidden
    var resolution by remember { mutableStateOf(DisplayPrefs.resolution(context, deviceId)) }
    var rotation by remember { mutableStateOf(DisplayPrefs.rotation(context, deviceId)) }
    var custom by remember { mutableStateOf("") }
    var textOpen by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf("") }
    var layoutJsonOpen by remember { mutableStateOf(false) }
    var layoutJsonValue by remember { mutableStateOf("") }
    var confirmDeleteKey by remember { mutableStateOf<HudKey?>(null) }
    var confirmResetHud by remember { mutableStateOf(false) }
    var confirmImportLayout by remember { mutableStateOf<List<HudKey>?>(null) }
    var confirmApplyPreset by remember { mutableStateOf<HudProfilePreset?>(null) }
    var confirmLockPc by remember { mutableStateOf(false) }
    var confirmResolution by remember { mutableStateOf<String?>(null) }
    var validationAlert by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(context) { SpotifyMediaBridge.refresh(context) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                SpotifyMediaBridge.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(deviceId, externalInputDeviceConnected, autoHideHudOnExternalInput) {
        if (!externalInputDeviceConnected || !autoHideHudOnExternalInput) {
            forceShowHudForExternalInput = false
        }
    }

    val anyOverlayOpen = panelOpen || monitorGridOpen || pickerOpen || editing != null || textOpen || layoutJsonOpen ||
        confirmDeleteKey != null || confirmResetHud || confirmImportLayout != null ||
        confirmApplyPreset != null || confirmLockPc ||
        confirmResolution != null || validationAlert != null
    LaunchedEffect(anyOverlayOpen) {
        onOverlayActiveChange(anyOverlayOpen)
    }

    LaunchedEffect(
        panelOpen, monitorGridOpen, pickerOpen, editing, textOpen, layoutJsonOpen, mappingMode,
        scrollPillOpen, spotifyExpanded,
        confirmDeleteKey, confirmResetHud, confirmImportLayout, confirmApplyPreset,
        confirmLockPc, confirmResolution, validationAlert,
    ) {
        onRegisterBackHandler {
            when {
                validationAlert != null -> {
                    validationAlert = null
                    true
                }
                confirmDeleteKey != null -> {
                    confirmDeleteKey = null
                    true
                }
                confirmResetHud -> {
                    confirmResetHud = false
                    true
                }
                confirmImportLayout != null -> {
                    confirmImportLayout = null
                    true
                }
                confirmApplyPreset != null -> {
                    confirmApplyPreset = null
                    true
                }
                confirmLockPc -> {
                    confirmLockPc = false
                    true
                }
                confirmResolution != null -> {
                    confirmResolution = null
                    true
                }
                editing != null -> {
                    editing = null
                    true
                }
                pickerOpen -> {
                    pickerOpen = false
                    true
                }
                layoutJsonOpen -> {
                    layoutJsonOpen = false
                    true
                }
                textOpen -> {
                    textOpen = false
                    true
                }
                spotifyExpanded -> {
                    spotifyExpanded = false
                    true
                }
                scrollPillOpen -> {
                    scrollPillOpen = false
                    true
                }
                monitorGridOpen -> {
                    monitorGridOpen = false
                    true
                }
                panelOpen -> {
                    panelOpen = false
                    true
                }
                mappingMode -> {
                    onMappingModeChange(false)
                    true
                }
                else -> false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onOverlayActiveChange(false)
            onRegisterBackHandler(null)
        }
    }

    fun copyCoreInfo() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(
            ClipData.newPlainText("SeiDesk RM", coreInfo.joinToString("\n"))
        )
        notice.show(seiNow( "Technical info copied"))
    }

    fun clipboardText(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context).toString()
    }

    fun exportHudLayoutToClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val json = HudKey.exportLayoutJson(keys)
        cm?.setPrimaryClip(ClipData.newPlainText("SeiDesk RM HUD Layout", json))
        notice.show(seiNow( "Button layout JSON copied to clipboard"))
    }

    fun importHudLayoutFromRaw(raw: String): Boolean {
        val imported = HudKey.importLayoutJson(raw, prefs.hudButtonSize)
        if (imported == null) {
            notice.show(seiNow( "Layout JSON is invalid or empty"))
            return false
        }
        onKeysChange(imported)
        notice.show(
            seiNow(
                "Layout imported ({0} buttons)",
                imported.size,
            ),
        )
        return true
    }

    fun haptic() {
        if (haptics) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun toggleHudButtonVisibility() {
        if (autoHideHudOnExternalInput && externalInputDeviceConnected) {
            if (hudButtonsVisibleNow) {
                forceShowHudForExternalInput = false
            } else {
                showHudButtons = true
                prefs.setHudButtonsVisible(deviceId, true)
                forceShowHudForExternalInput = true
            }
        } else {
            showHudButtons = !showHudButtons
            prefs.setHudButtonsVisible(deviceId, showHudButtons)
        }
    }

    fun replace(updated: HudKey) {
        // Keep the editor's live draft in sync with the persisted per-device layout.
        editing = updateHudEditorDraft(editing, updated)
        val updatedKeys = keys.map { if (it.id == updated.id) updated else it }
        prefs.setCustomHudKeys(deviceId, updatedKeys)
        onKeyEdited(updated)
    }

    fun add(option: HudKeyOption) {
        if (keys.size >= 36) {
            notice.show(seiNow( "Maximum 36 HUD buttons"))
            return
        }
        val width = configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
        val height = configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
        val size = prefs.hudButtonSize
        val maxX = (width - size).coerceAtLeast(1f)
        val maxY = (height - size).coerceAtLeast(1f)
        val railCenters = listOf(
            androidx.compose.ui.geometry.Offset(width - 32f, height - 210f),
            androidx.compose.ui.geometry.Offset(width - 32f, height - 156f),
            androidx.compose.ui.geometry.Offset(width - 32f, height - 102f),
        )
        val candidates = buildList {
            var y = 72f
            while (y <= maxY) {
                var x = 12f
                while (x <= maxX) {
                    this.add(androidx.compose.ui.geometry.Offset(x, y))
                    x += 88f
                }
                y += 64f
            }
        }
        val position = candidates.firstOrNull { candidate ->
            val cx = candidate.x + size / 2f
            val cy = candidate.y + size / 2f
            val hitsExisting = keys.any { oldKey ->
                val oldCx = oldKey.x * (width - oldKey.size).coerceAtLeast(1f) + oldKey.size / 2f
                val oldCy = oldKey.y * (height - oldKey.size).coerceAtLeast(1f) + oldKey.size / 2f
                val dx = cx - oldCx
                val dy = cy - oldCy
                val minDistance = (size + oldKey.size) / 2f + 8f
                dx * dx + dy * dy < minDistance * minDistance
            }
            val hitsRail = railCenters.any { rail ->
                val dx = cx - rail.x
                val dy = cy - rail.y
                val minDistance = size / 2f + 22f + 8f
                dx * dx + dy * dy < minDistance * minDistance
            }
            val hitsPanelHandle = kotlin.math.abs(cx - (width - 30f)) < size / 2f + 38f &&
                kotlin.math.abs(cy - 70f) < size / 2f + 60f
            !hitsExisting && !hitsRail && !hitsPanelHandle
        }
        if (position == null) {
            notice.show(seiNow( "No free control space — move a button first"))
            return
        }
        val key = HudKey(
            id = "k${java.util.UUID.randomUUID()}",
            kind = option.kind,
            label = option.label,
            keyCode = option.keyCode,
            shift = option.shift,
            combo = option.combo,
            action = option.defaultAction.takeIf { it in HudKey.allowedActions(option.kind) } ?: HudAction.TAP,
            x = (position.x / maxX).coerceIn(0f, 1f),
            y = (position.y / maxY).coerceIn(0f, 1f),
            size = size,
            macroText = option.macroText,
            macroSendEnter = option.macroSendEnter,
        )
        val updatedKeys = keys + key
        onKeysChange(updatedKeys)
        prefs.setCustomHudKeys(deviceId, updatedKeys)
        hudProfile = HudProfilePreset.CUSTOM
        prefs.setHudProfile(deviceId, HudProfilePreset.CUSTOM)
        panelOpen = false
        onMappingModeChange(true)
        notice.show(
            seiNow(
                "Button \"{0}\" added — drag it into position or press Done.",
                key.label,
            ),
        )
    }

    Box(Modifier.fillMaxSize().zIndex(10f)) {
        if (pointerVisible && !panelOpen && !monitorGridOpen) {
            XyPointer(
                position = pointerScreen,
                sizeDp = pointerSize,
                style = pointerStyle,
                remote = remoteCursor,
                zoom = zoom,
            )
        }

        if (showTelemetryPill && !panelOpen && !monitorGridOpen && !mappingMode) {
            LiveTelemetryPill(
                telemetry = telemetry,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp)
                    .zIndex(19f),
            )
        }

        // Hanya tombol HUD yang ikut auto-hide/transparansi; pointer, menu, dan rail
        // (keyboard, monitor, putus, serta toggle HUD) tetap terjangkau.
        if (hudButtonsVisibleNow) {
            Box(Modifier.fillMaxSize().alpha(hudOpacity)) {
                HudKeyLayer(
                    keys = keys,
                    mappingMode = mappingMode,
                    plate = plate,
                    latchedKeys = latchedKeyIds,
                    onMove = { id, x, y ->
                        val moved = keys.map { if (it.id == id) it.copy(x = x, y = y) else it }
                        onKeysChange(moved)
                        prefs.setCustomHudKeys(deviceId, moved)
                    },
                    onPhase = { key, phase ->
                        if (phase != HudPhase.UP) haptic()
                        onPhase(key, phase)
                    },
                    scrollSpeed = prefs.scrollSpeed,
                    onScrollUnits = onScrollUnits,
                    onEdit = { editing = it },
                    onStickAxis = { stickKey, x, y ->
                        // Stik kanan gamepad = mouse (permintaan pemilik:
                        // "analog kok cuma wasd"); stik kiri ikut mode.
                        if (stickKey.pad && stickKey.label == "R") {
                            if (x != 0f || y != 0f) onStickPointer(x, y)
                        } else {
                            onStickAxis(x, y)
                        }
                    },
                    stickMode = stickModeUi,
                )
            }
        }

        // Indikator rekaman sengaja ditaruh DI LUAR syarat !panelOpen: status
        // "sedang merekam" tidak boleh hilang hanya karena pengguna membuka
        // menu. Tidak ada clickable, jadi tidak menyerap sentuhan layar remote.
        if (recordingActive) {
            RecordingIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 10.dp, top = 10.dp)
                    .zIndex(24f),
            )
        }

        if (!panelOpen && !monitorGridOpen && !mappingMode) {
            SpotifyFloatingPlayer(
                playback = spotifyPlayback,
                expanded = spotifyExpanded,
                onExpand = { spotifyExpanded = true },
                onMinimize = { spotifyExpanded = false },
                positionMs = spotifyPosition,
                onTogglePlayback = { SpotifyMediaBridge.playPause() },
                onPrevious = { SpotifyMediaBridge.previous() },
                onNext = { SpotifyMediaBridge.next() },
                onSeek = { SpotifyMediaBridge.seekTo(it) },
                onSeekBy = { SpotifyMediaBridge.seekBy(it) },
                onStop = { SpotifyMediaBridge.stop() },
                onToggleShuffle = { SpotifyMediaBridge.setShuffle(it) },
                onCycleRepeat = { SpotifyMediaBridge.cycleRepeat() },
                onRequestAccess = { openSpotifyNotificationAccess(context) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .zIndex(22f),
            )
        }

        if (scrollPillOpen && !panelOpen && !monitorGridOpen && !mappingMode) {
            SessionScrollPill(
                plate = plate,
                scrollSpeed = prefs.scrollSpeed,
                onScrollUnits = onScrollUnits,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 68.dp)
                    .zIndex(23f),
            )
        }

        // Rail diminimalkan atas permintaan pemilik (2026-10-10): hanya dua
        // tombol tetap — pembuka menu bubble (panah atas) dan keyboard di
        // paling bawah. Sisanya pindah ke dalam bubble popup.
        if (!panelOpen && !monitorGridOpen && !mappingMode) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 24.dp)
                    .zIndex(24f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Bubble rail "disedot" keluar dari tombol panah: scale dari
                // titik bawah (posisi tombol) + fade, masuk dan keluar.
                AnimatedVisibility(
                    visible = railMenuOpen,
                    enter = fadeIn(androidx.compose.animation.core.tween(140)) +
                        scaleIn(
                            androidx.compose.animation.core.tween(230, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            initialScale = 0.25f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                        ),
                    exit = fadeOut(androidx.compose.animation.core.tween(120)) +
                        scaleOut(
                            androidx.compose.animation.core.tween(170, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                            targetScale = 0.2f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                        ),
                ) {
                    Column(
                        Modifier
                            .seiSurface(shape = RoundedCornerShape(18.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        RailMenuItem(
                            icon = SeiIcons.ScrollSlide,
                            label = sei( "Scroll control"),
                            active = scrollPillOpen,
                        ) {
                            scrollPillOpen = !scrollPillOpen
                        }
                        RailMenuItem(
                            icon = if (hudButtonsVisibleNow) SeiIcons.EyeOff else SeiIcons.Eye,
                            label = if (hudButtonsVisibleNow) {
                                sei( "Hide overlay")
                            } else {
                                sei( "Show overlay")
                            },
                        ) {
                            toggleHudButtonVisibility()
                        }
                        RailMenuItem(
                            icon = SeiIcons.Monitor,
                            label = sei( "Monitors & sessions"),
                        ) {
                            railMenuOpen = false
                            monitorGridOpen = true
                        }
                        RailMenuItem(
                            icon = SeiIcons.Home,
                            label = sei( "Go home (session stays alive)"),
                        ) {
                            railMenuOpen = false
                            onGoHome()
                        }
                        RailMenuItem(
                            icon = SeiIcons.Power,
                            label = sei( "Disconnect"),
                            danger = true,
                        ) {
                            railMenuOpen = false
                            onDisconnect()
                        }
                    }
                }
                RailButton(
                    icon = if (railMenuOpen) SeiIcons.ChevronDown else SeiIcons.ChevronUp,
                    active = railMenuOpen,
                    description = sei( "Session menu"),
                    plate = plate,
                    onClick = { railMenuOpen = !railMenuOpen },
                )
                RailButton(
                    icon = SeiIcons.Keyboard,
                    active = false,
                    description = sei( "Open phone keyboard"),
                    plate = plate,
                ) { onOpenKeyboard() }
            }
        }

        if (mappingMode) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).zIndex(26f)) {
                HudMappingBanner(
                    keyCount = keys.size,
                    onAdd = { pickerOpen = true },
                    onReset = { confirmResetHud = true },
                    onDone = { onMappingModeChange(false) },
                )
            }
        }

        if (textOpen) {
            SeiOverlay(title = sei( "Send text"), onDismiss = { textOpen = false }) {
                Text(
                    sei(
                        "Sent as keystrokes. Use 'Send as clipboard' for Windows Paste.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                SeiField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = sei( "Text"),
                    hint = sei( "e.g. password, a URL"),
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiPillButton(
                        sei( "Paste"),
                        {
                            val paste = clipboardText()
                            textValue = paste
                            if (paste.isEmpty()) notice.show(seiNow( "Phone clipboard is empty"))
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        sei( "Type to remote"),
                        {
                            val t = textValue
                            if (t.isEmpty()) {
                                validationAlert = seiNow( "Empty text") to
                                    seiNow( "Enter some text before sending to the remote.")
                            } else {
                                onSendText(t)
                                notice.show(seiNow( "Text typed into the remote window"))
                                textOpen = false
                            }
                        },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                SeiPillButton(
                    sei( "Send as Windows clipboard"),
                    {
                        val text = textValue
                        when {
                            text.isEmpty() -> validationAlert = seiNow( "Empty text") to
                                seiNow( "Enter some text before sending to Windows clipboard.")
                            !clipboardSyncEnabled -> validationAlert = seiNow( "Clipboard channel off") to
                                seiNow( "Enable the clipboard channel in device settings and reconnect.")
                            onSendRemoteClipboardText(text) -> {
                                notice.show(seiNow( "Clipboard request sent; wait briefly before Paste"))
                                textOpen = false
                            }
                            else -> notice.show(seiNow( "Failed to queue clipboard request"))
                        }
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = clipboardSyncEnabled && textValue.isNotEmpty(),
                )
                SeiPillButton(
                    sei( "Done"),
                    { textOpen = false },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (layoutJsonOpen) {
            SeiOverlay(
                title = sei( "Button layout (JSON)"),
                onDismiss = { layoutJsonOpen = false },
            ) {
                Text(
                    sei(
                        "Copy JSON to reuse this button layout on another device, or paste JSON and press Apply.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                SeiField(
                    value = layoutJsonValue,
                    onValueChange = { layoutJsonValue = it },
                    label = sei( "Layout JSON"),
                    hint = "{\"version\":1,\"keys\":[...]}",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiPillButton(
                        sei( "Paste clipboard"),
                        {
                            val clip = clipboardText()
                            if (clip.isEmpty()) {
                                notice.show(seiNow( "Phone clipboard is empty"))
                            } else {
                                layoutJsonValue = clip
                            }
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        sei( "Apply JSON"),
                        {
                            val imported = HudKey.importLayoutJson(layoutJsonValue, prefs.hudButtonSize)
                            if (imported == null) {
                                validationAlert = seiNow( "Invalid JSON") to
                                    seiNow(
                                        "The button layout JSON format is invalid or contains no control buttons.",
                                    )
                            } else {
                                confirmImportLayout = imported
                            }
                        },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                SeiPillButton(
                    sei( "Copy current layout"),
                    {
                        exportHudLayoutToClipboard()
                        layoutJsonValue = HudKey.exportLayoutJson(keys)
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SeiPillButton(
                    sei( "Close"),
                    { layoutJsonOpen = false },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- handle panel di kanan atas, terpisah dari rail kontrol ----
        if (!panelOpen && !monitorGridOpen && !mappingMode) {
            PanelHandle(
                onClick = {
                    scrollPillOpen = false
                    spotifyExpanded = false
                    panelOpen = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 6.dp),
            )
        }

        if (monitorGridOpen) {
            MonitorAndUserGridModal(
                deviceId = deviceId,
                hostLabel = hostLabel,
                remoteSize = remoteSize,
                codecLabel = telemetry.codecLabel,
                resolution = resolution,
                pcConnectMode = pcConnectMode,
                activeUsername = activeUsername,
                consoleAdminMode = consoleAdminMode,
                onSelectMonitorResolution = { selectedRes ->
                    monitorGridOpen = false
                    resolution = selectedRes
                    DisplayPrefs.setResolution(context, deviceId, selectedRes)
                    onResolutionChange(selectedRes)
                },
                onSwitchConsoleMode = { useConsole ->
                    monitorGridOpen = false
                    onSwitchConsoleMode(useConsole)
                },
                onSwitchUserSession = { user, dom, pass ->
                    monitorGridOpen = false
                    onSwitchUserSession(user, dom, pass)
                },
                onActivatePrivacyCurtain = {
                    monitorGridOpen = false
                    onActivatePrivacyCurtain()
                },
                onLockRemotePc = {
                    monitorGridOpen = false
                    onLockRemotePc()
                },
                onOpenHome = {
                    monitorGridOpen = false
                    onOpenHome()
                },
                notice = notice,
                onDismiss = { monitorGridOpen = false },
            )
        }

        if (panelOpen) {
            SessionPanel(
                drawerMode = drawerMode,
                onDrawerModeChange = { v ->
                    drawerMode = v
                    prefs.menuDrawer = v
                },
                deviceId = deviceId,
                hostLabel = hostLabel,
                statusText = statusText,
                remoteSize = remoteSize,
                zoomPercent = zoomPercent,
                remoteDpi = remoteDpi,
                tab = tab,
                onTab = { tab = it },
                notice = notice,
                // Layar
                resolution = resolution,
                rotation = rotation,
                custom = custom,
                autoFit = autoFit,
                onAutoFitChange = { autoFit = it; prefs.autoFit = it },
                onCustomChange = { custom = it },
                onCustomApply = {
                    val parsed = DisplayPrefs.parseCustom(custom)
                    if (parsed == null) {
                        validationAlert = seiNow( "Invalid custom resolution") to
                            seiNow(
                                "Enter a valid WxH format (640..8192 x 480..8192, even width), e.g. 1920x1080.",
                            )
                    } else {
                        confirmResolution = parsed
                    }
                },
                onResolution = { selected ->
                    if (selected != resolution) {
                        confirmResolution = selected
                    }
                },
                onRotation = {
                    rotation = it
                    DisplayPrefs.setRotation(context, deviceId, it)
                    onRotationChange(it)
                },
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onFit = onFit,
                onZoomActual = onZoomActual,
                onZoomScale = onZoomScale,
                onRemoteDpiChange = onRemoteDpiChange,
                onDisplayRefreshPreferenceChange = onDisplayRefreshPreferenceChange,
                showTelemetryPill = showTelemetryPill,
                onShowTelemetryPillChange = { showTelemetryPill = it; prefs.showTelemetryPill = it },
                onOpenMonitorGrid = {
                    panelOpen = false
                    monitorGridOpen = true
                },
                pcConnectMode = pcConnectMode,
                // Input
                inputMode = inputMode,
                onInputModeChange = onInputModeChange,
                swapMouseButtons = swapMouseButtons,
                onSwapMouseButtonsChange = onSwapMouseButtonsChange,
                onSendTextClick = { textValue = ""; textOpen = true },
                lastClipboard = lastClipboard,
                clipboardSyncEnabled = clipboardSyncEnabled,
                pointerVisible = pointerVisible,
                onPointerVisibilityChange = onPointerVisibilityChange,
                pointerStyle = pointerStyle,
                onPointerStyle = { pointerStyle = it; prefs.pointerStyle = it },
                pointerSize = pointerSize,
                onPointerSize = { pointerSize = it; prefs.pointerSize = it },
                pointerSensitivity = pointerSensitivity,
                onPointerSensitivity = { pointerSensitivity = it; prefs.pointerSensitivity = it },
                pointerAcceleration = pointerAcceleration,
                onPointerAcceleration = { pointerAcceleration = it; prefs.pointerAcceleration = it },
                inertialScroll = inertialScroll,
                onInertialScroll = { inertialScroll = it; prefs.inertialScroll = it },
                edgeScrollZone = edgeScrollZone,
                onEdgeScrollZone = { edgeScrollZone = it; prefs.edgeScrollZone = it },
                gamepadEnabled = gamepadEnabled,
                onGamepadEnabled = { gamepadEnabled = it; prefs.gamepadEnabled = it },
                stickMode = stickModeUi,
                onStickModeChange = { next ->
                    stickModeUi = next
                    prefs.virtualPadStickMode = next
                    // Activity ikut diberi tahu karena field padStickMode dibaca
                    // mapper dari luar komposisi.
                    onStickModeChange(next)
                },
                gyroMouseEnabled = gyroMouseEnabled,
                onGyroMouseEnabled = {
                    gyroMouseEnabled = it
                    prefs.gyroMouseEnabled = it
                    onGyroMouseChanged(it)
                },
                // Tombol
                keys = keys,
                hudProfile = hudProfile,
                onSelectHudProfile = { selectedPreset ->
                    if (selectedPreset == HudProfilePreset.CUSTOM) {
                        hudProfile = HudProfilePreset.CUSTOM
                        prefs.setHudProfile(deviceId, HudProfilePreset.CUSTOM)
                        val savedCustom = prefs.customHudKeys(deviceId)
                        if (savedCustom != null && savedCustom.isNotEmpty()) {
                            onKeysChange(savedCustom)
                        } else {
                            prefs.setCustomHudKeys(deviceId, keys)
                        }
                        panelOpen = false
                        onMappingModeChange(true)
                        notice.show(
                            seiNow(
                                "Custom mode active — drag, add, or edit buttons to build your layout",
                            ),
                        )
                    } else if (selectedPreset != hudProfile) {
                        confirmApplyPreset = selectedPreset
                    }
                },
                onAddKey = { pickerOpen = true },
                onEditKey = { editing = it },
                onDeleteKey = { confirmDeleteKey = it },
                mappingMode = mappingMode,
                onMappingModeChange = { enabled ->
                    onMappingModeChange(enabled)
                    if (enabled) panelOpen = false
                },
                plate = plate,
                onPlate = { plate = it; prefs.hudPlate = it },
                haptics = haptics,
                onHaptics = { haptics = it; prefs.haptics = it },
                onResetCluster = { confirmResetHud = true },
                onExportLayoutJson = { exportHudLayoutToClipboard() },
                onOpenLayoutJson = {
                    layoutJsonValue = HudKey.exportLayoutJson(keys)
                    layoutJsonOpen = true
                },
                showHudButtons = showHudButtons,
                onShowHudButtonsChange = { visible ->
                    showHudButtons = visible
                    prefs.setHudButtonsVisible(deviceId, visible)
                    if (!visible) forceShowHudForExternalInput = false
                },
                autoHideHudOnExternalInput = autoHideHudOnExternalInput,
                onAutoHideHudOnExternalInputChange = { enabled ->
                    autoHideHudOnExternalInput = enabled
                    prefs.autoHideHudOnExternalInput = enabled
                    if (!enabled) forceShowHudForExternalInput = false
                },
                hudOpacity = hudOpacity,
                onHudOpacityChange = { opacity ->
                    hudOpacity = opacity.coerceIn(0f, 1f)
                    prefs.hudOpacity = hudOpacity
                },
                // Sesi
                recordingLabel = recordingLabel,
                recordingStatus = recordingStatus,
                recordingActionEnabled = recordingActionEnabled,
                recordingActive = recordingActive,
                onToggleRecording = onToggleRecording,
                onScreenshot = onScreenshot,
                onLockRemotePc = { confirmLockPc = true },
                onActivatePrivacyCurtain = {
                    panelOpen = false
                    onActivatePrivacyCurtain()
                },
                onDisconnect = {
                    panelOpen = false
                    onDisconnect()
                },
                coreInfo = coreInfo,
                onCopyCoreInfo = { copyCoreInfo() },
                onClose = { panelOpen = false },
            )
        }
    }

    if (pickerOpen) {
        HudKeyPicker(
            onPick = { add(it); pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
    editing?.let { key ->
        HudKeyEditor(
            key = key,
            onChange = { updated ->
                val persisted = if (updated.size != key.size) {
                    resizeHudKeyPreservingCenter(
                        key, updated.size,
                        configuration.screenWidthDp.toFloat(),
                        configuration.screenHeightDp.toFloat(),
                    )
                } else updated
                replace(persisted)
            },
            onDelete = {
                confirmDeleteKey = key
            },
            onDismiss = { editing = null },
        )
    }

    confirmDeleteKey?.let { key ->
        SeiDialog(
            title = sei( "Delete control button?"),
            body = sei(
                "Delete button \"{0}\" from this session layout?",
                key.label,
            ),
            confirmLabel = sei( "Delete"),
            onConfirm = {
                confirmDeleteKey = null
                if (editing?.id == key.id) editing = null
                onDeleteKey(key)
                notice.show(seiNow( "Button deleted"))
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmDeleteKey = null },
        )
    }

    if (confirmResetHud) {
        SeiDialog(
            title = sei( "Reset button layout?"),
            body = sei(
                "Restore all control buttons to the default set? Your current custom button layout will be replaced.",
            ),
            confirmLabel = sei( "Reset"),
            onConfirm = {
                confirmResetHud = false
                editing = null
                onResetCluster()
                notice.show(seiNow( "Button layout restored to default"))
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmResetHud = false },
        )
    }

    confirmImportLayout?.let { imported ->
        SeiDialog(
            title = sei( "Apply JSON layout?"),
            body = sei(
                "Replace the current button layout with {0} buttons from JSON?",
                imported.size,
            ),
            confirmLabel = sei( "Apply"),
            onConfirm = {
                confirmImportLayout = null
                layoutJsonOpen = false
                panelOpen = false
                onKeysChange(imported)
                notice.show(
                    seiNow(
                        "Layout imported ({0} buttons)",
                        imported.size,
                    ),
                )
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmImportLayout = null },
        )
    }

    confirmApplyPreset?.let { preset ->
        SeiDialog(
            title = sei( "Switch HUD button profile?"),
            body = sei(
                "Apply profile \"{0}\"? The on-screen control buttons will be updated to match this preset.",
                preset.titleEn,
            ),
            confirmLabel = sei( "Apply"),
            onConfirm = {
                confirmApplyPreset = null
                hudProfile = preset
                prefs.setHudProfile(deviceId, preset)
                onKeysChange(HudKey.presetLayout(preset, prefs.hudButtonSize))
                notice.show(
                    seiNow(
                        "Button profile switched to {0}",
                        preset.titleEn,
                    ),
                )
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmApplyPreset = null },
        )
    }

    if (confirmLockPc) {
        SeiDialog(
            title = sei( "Lock PC screen (Win+L)?"),
            body = sei(
                "Send Win+L to lock the Windows session on the remote computer now?",
            ),
            confirmLabel = sei( "Lock PC"),
            onConfirm = {
                confirmLockPc = false
                panelOpen = false
                onLockRemotePc()
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmLockPc = false },
        )
    }

    confirmResolution?.let { targetRes ->
        SeiDialog(
            title = sei( "Change remote resolution?"),
            body = sei(
                "Apply resolution \"{0}\"? If the server does not apply live resize, the session will reconnect automatically.",
                targetRes,
            ),
            confirmLabel = sei( "Apply"),
            onConfirm = {
                confirmResolution = null
                resolution = targetRes
                DisplayPrefs.setResolution(context, deviceId, targetRes)
                onResolutionChange(targetRes)
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmResolution = null },
        )
    }

    validationAlert?.let { (alertTitle, alertBody) ->
        SeiDialog(
            title = alertTitle,
            body = alertBody,
            confirmLabel = sei( "Got it"),
            onConfirm = { validationAlert = null },
            onDismiss = { validationAlert = null },
        )
    }
}

@Composable
private fun LiveTelemetryPill(
    telemetry: io.seidesk.rm.core.TelemetrySample,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rtt = telemetry.rttMs
    val rttColor = when {
        rtt <= 0 -> Color(0xFF10B981)
        rtt < 45 -> Color(0xFF10B981)
        rtt < 100 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val rttLabel = if (rtt > 0) "${rtt} ms" else "-- ms"
    val resLabel = if (telemetry.width > 0) "${telemetry.width}×${telemetry.height}" else "--"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (!expanded) {
            // Collapsed 1-line telemetry chip (min 11 sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(rttColor),
                )
                Text(
                    text = "${telemetry.fps} fps · $rttLabel · ${telemetry.codecLabel}",
                    style = TextStyle(
                        fontFamily = io.seidesk.rm.ui.theme.SeiFont,
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        } else {
            // Expanded telemetry details (min 10 sp)
            val items = listOf(
                Triple("FPS", "${telemetry.fps}", Color(0xFFF2F2F4)),
                Triple("LATENCY", rttLabel, rttColor),
                Triple("RESOLUTION", resLabel, Color(0xFFF2F2F4)),
                Triple("ENCODE", telemetry.codecLabel, Color(0xFFF2F2F4)),
                Triple("RELAY", telemetry.relayLabel, Color(0xFFF2F2F4)),
                Triple("NETWORK", telemetry.networkLabel, Color(0xFFF2F2F4)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(rttColor))
                    Text(
                        sei("TELEMETRY"),
                        style = TextStyle(
                            fontFamily = io.seidesk.rm.ui.theme.SeiFont,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                Spacer(Modifier.height(2.dp))
                items.forEach { (label, value, color) ->
                    Row(
                        modifier = Modifier.widthIn(min = 140.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontFamily = io.seidesk.rm.ui.theme.SeiFont,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                            ),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = value,
                            style = TextStyle(
                                fontFamily = io.seidesk.rm.ui.theme.SeiFont,
                                color = color,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Tombol rail sesi: accent ring + tinted obsidian/primary fill. */
@Composable
private fun RailButton(
    icon: ImageVector,
    active: Boolean,
    description: String,
    plate: HudPlate,
    onClick: () -> Unit,
) {
    val pal = hudPalette(plate)
    val ringColor = if (active) MaterialTheme.colorScheme.primary else pal.border
    val fillColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else pal.plate
    val iconTint = if (active) MaterialTheme.colorScheme.primary else pal.ink

    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(fillColor)
            .border(
                if (active) 2.dp else 1.2.dp,
                ringColor,
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RailMenuItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else ink,
            modifier = Modifier.size(17.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                danger -> MaterialTheme.colorScheme.error
                active -> MaterialTheme.colorScheme.primary
                else -> ink
            },
        )
    }
}

@Composable
private fun PanelHandle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var idleAlpha by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        delay(3000)
        idleAlpha = 0.35f
    }

    Box(
        modifier
            .size(44.dp)
            .systemGestureExclusion()
            .clickable(onClick = {
                idleAlpha = 1f
                onClick()
            })
            .zIndex(20f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = idleAlpha))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f * idleAlpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SeiIcons.Sliders,
                contentDescription = sei("Open session panel"),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = idleAlpha),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Pointer tunggal.
 *
 * Urutan prioritas:
 *  1. Bentuk kursor yang DIKIRIM SERVER (panah, tangan, I-beam, resize, ...)
 *     — digambar apa adanya, jadi bentuknya berubah selayaknya pointer desktop.
 *  2. Kalau server tidak mengirim apa-apa: panah/titik bawaan app.
 */
@Composable
private fun XyPointer(
    position: Offset,
    sizeDp: Float,
    style: PointerStyle,
    remote: RemoteCursor?,
    zoom: Float,
) {
    val remoteBitmap = remote?.bitmap
    if (remote != null && remote.visible && remoteBitmap != null) {
        val image = remember(remoteBitmap) { remoteBitmap.asImageBitmap() }
        val scale = zoom.coerceIn(0.35f, 3f)
        Canvas(Modifier.fillMaxSize().zIndex(11f)) {
            val w = (remoteBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (remoteBitmap.height * scale).roundToInt().coerceAtLeast(1)
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(remoteBitmap.width, remoteBitmap.height),
                dstOffset = IntOffset(
                    (position.x - remote.hotX * scale).roundToInt(),
                    (position.y - remote.hotY * scale).roundToInt(),
                ),
                dstSize = IntSize(w, h),
            )
        }
        return
    }

    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { sizeDp.dp.toPx() }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (position.x - sizePx / 2f).roundToInt(),
                    (position.y - sizePx / 2f).roundToInt(),
                )
            }
            .size(sizeDp.dp)
            .zIndex(11f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (style) {
                PointerStyle.DOT -> {
                    val radius = w / 2f - 1.5f
                    drawCircle(Color.White, radius = radius, center = Offset(w / 2f, h / 2f))
                    drawCircle(
                        Color.Black.copy(alpha = 0.55f),
                        radius = radius,
                        center = Offset(w / 2f, h / 2f),
                        style = Stroke(width = 3f),
                    )
                }

                PointerStyle.ARROW -> {
                    val path = Path().apply {
                        val sx = w / 24f
                        val sy = h / 24f
                        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
                        val a = p(6f, 3f)
                        val b = p(6f, 18.5f)
                        val c = p(10.2f, 14.6f)
                        val d = p(13.1f, 20.8f)
                        val e = p(15.6f, 19.6f)
                        val f = p(12.8f, 13.6f)
                        val g = p(18.4f, 13.1f)
                        moveTo(a.x, a.y)
                        lineTo(b.x, b.y)
                        lineTo(c.x, c.y)
                        lineTo(d.x, d.y)
                        lineTo(e.x, e.y)
                        lineTo(f.x, f.y)
                        lineTo(g.x, g.y)
                        close()
                    }
                    drawPath(path, Color.White)
                    drawPath(path, Color.Black.copy(alpha = 0.6f), style = Stroke(width = 2.4f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ panel

@Composable
private fun SessionPanel(
    drawerMode: Boolean = false,
    onDrawerModeChange: (Boolean) -> Unit = {},
    deviceId: String,
    hostLabel: String,
    statusText: String,
    remoteSize: String,
    zoomPercent: Int,
    remoteDpi: Int,
    tab: PanelTab,
    onTab: (PanelTab) -> Unit,
    notice: SeiNoticeState,
    onClose: () -> Unit,
    // Layar
    resolution: String,
    rotation: String,
    custom: String,
    autoFit: Boolean,
    onAutoFitChange: (Boolean) -> Unit,
    onCustomChange: (String) -> Unit,
    onCustomApply: () -> Unit,
    onResolution: (String) -> Unit,
    onRotation: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onZoomActual: () -> Unit,
    onZoomScale: (Float) -> Unit,
    onRemoteDpiChange: (Int) -> Unit,
    onDisplayRefreshPreferenceChange: (Int) -> Unit,
    showTelemetryPill: Boolean,
    onShowTelemetryPillChange: (Boolean) -> Unit,
    onOpenMonitorGrid: () -> Unit,
    pcConnectMode: Boolean = false,
    // Input
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    swapMouseButtons: Boolean,
    onSwapMouseButtonsChange: (Boolean) -> Unit,
    onSendTextClick: () -> Unit,
    lastClipboard: String?,
    clipboardSyncEnabled: Boolean,
    pointerVisible: Boolean,
    onPointerVisibilityChange: (Boolean) -> Unit,
    pointerStyle: PointerStyle,
    onPointerStyle: (PointerStyle) -> Unit,
    pointerSize: Float,
    onPointerSize: (Float) -> Unit,
    pointerSensitivity: Float,
    onPointerSensitivity: (Float) -> Unit,
    pointerAcceleration: Boolean,
    onPointerAcceleration: (Boolean) -> Unit,
    inertialScroll: Boolean,
    onInertialScroll: (Boolean) -> Unit,
    edgeScrollZone: Boolean,
    onEdgeScrollZone: (Boolean) -> Unit,
    gamepadEnabled: Boolean,
    stickMode: SeiStickMode,
    onStickModeChange: (SeiStickMode) -> Unit,
    onGamepadEnabled: (Boolean) -> Unit,
    gyroMouseEnabled: Boolean,
    onGyroMouseEnabled: (Boolean) -> Unit,
    // Tombol
    keys: List<HudKey>,
    hudProfile: HudProfilePreset,
    onSelectHudProfile: (HudProfilePreset) -> Unit,
    onAddKey: () -> Unit,
    onEditKey: (HudKey) -> Unit,
    onDeleteKey: (HudKey) -> Unit,
    mappingMode: Boolean,
    onMappingModeChange: (Boolean) -> Unit,
    plate: HudPlate,
    onPlate: (HudPlate) -> Unit,
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    onResetCluster: () -> Unit,
    onExportLayoutJson: () -> Unit,
    onOpenLayoutJson: () -> Unit,
    // Tombol overlay HUD
    showHudButtons: Boolean,
    onShowHudButtonsChange: (Boolean) -> Unit,
    autoHideHudOnExternalInput: Boolean,
    onAutoHideHudOnExternalInputChange: (Boolean) -> Unit,
    hudOpacity: Float,
    onHudOpacityChange: (Float) -> Unit,
    // Sesi
    recordingLabel: String,
    recordingStatus: String?,
    recordingActionEnabled: Boolean,
    recordingActive: Boolean,
    onToggleRecording: () -> Unit,
    onScreenshot: () -> Unit,
    onLockRemotePc: () -> Unit,
    onActivatePrivacyCurtain: () -> Unit,
    onDisconnect: () -> Unit,
    coreInfo: List<String>,
    onCopyCoreInfo: () -> Unit,
) {
    // Kunci seksi yang sedang dibuka di grid drill-in. Di-key pada `tab` supaya
    // pindah tab otomatis kembali ke daftar kotak, bukan membuka seksi lama.
    var openSection by remember(tab) { mutableStateOf<String?>(null) }
    // Panel muncul halus: membesar dari titik handle (kanan-atas) seperti
    // keluar dari tombolnya, bukan sekadar tampil seketika.
    var panelShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { panelShown = true }
    val panelScale by animateFloatAsState(
        targetValue = if (panelShown) 1f else 0.9f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (panelShown) 1f else 0f,
        animationSpec = tween(180),
    )
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(30f),
        contentAlignment = if (drawerMode) Alignment.CenterEnd else Alignment.Center,
    ) {
        // Scrim dipertahankan (lebih tipis) karena pemisahan utama sekarang
        // datang dari blur permukaan remote; di Android < 12 blur tidak jalan
        // sehingga scrim inilah yang menjaga keterbacaan.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
                .consumeBackgroundPointer(onClose),
        )
        // Dua varian menu (permintaan pemilik 2026-10-10): popup tengah gaya
        // jendela, atau drawer menempel sisi kanan setinggi layar.
        Column(
            if (drawerMode) {
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 380.dp)
            } else {
                Modifier
                    // Popup diperluas atas permintaan pemilik ("masih kurang"):
                    // hampir penuh di layar HP maupun tablet.
                    .fillMaxHeight(0.94f)
                    .fillMaxWidth(0.97f)
                    .widthIn(max = 900.dp)
            }
                .graphicsLayer {
                    scaleX = panelScale
                    scaleY = panelScale
                    alpha = panelAlpha
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                // Gaya kaca yang sama dengan pemutar musik. Opacity dinaikkan
                // (0.55 * 1.6 = 0.88) karena panel berisi banyak teks; kaca
                // setipis pemutar akan membuat tulisan sulit dibaca.
                .seiSurface(
                    shape = if (drawerMode) {
                        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                    } else {
                        RoundedCornerShape(28.dp)
                    },
                    opacity = 1.6f,
                    strength = 1f,
                )
                .pointerInput(Unit) {}
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        hostLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$statusText  ·  $zoomPercent%  ·  $remoteSize",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (tab == PanelTab.BUTTONS) {
                    PanelChip(sei( "+ Add"), onAddKey)
                }
                PanelChip(
                    if (drawerMode) sei( "Popup") else sei( "Drawer"),
                    { onDrawerModeChange(!drawerMode) },
                )
                PanelChip(sei( "Close"), onClose)
            }

            PanelTabTiles(tab = tab, onTab = onTab, modifier = Modifier.fillMaxWidth())

            val tabScroll = androidx.compose.runtime.key(tab) { rememberScrollState() }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(tabScroll),
                // Jarak antar kartu dirapatkan; kartu kini punya padding sendiri.
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                when (tab) {
                    PanelTab.SCREEN -> ScreenTab(
                        openKey = openSection,
                        onOpenKey = { openSection = it },
                        deviceId = deviceId,
                        remoteSize = remoteSize,
                        zoomPercent = zoomPercent,
                        remoteDpi = remoteDpi,
                        resolution = resolution,
                        rotation = rotation,
                        custom = custom,
                        autoFit = autoFit,
                        onAutoFitChange = onAutoFitChange,
                        onCustomChange = onCustomChange,
                        onCustomApply = onCustomApply,
                        onResolution = onResolution,
                        onRotation = onRotation,
                        onZoomIn = onZoomIn,
                        onZoomOut = onZoomOut,
                        onFit = onFit,
                        onZoomActual = onZoomActual,
                        onZoomScale = onZoomScale,
                        onRemoteDpiChange = onRemoteDpiChange,
                        onDisplayRefreshPreferenceChange = onDisplayRefreshPreferenceChange,
                        showTelemetryPill = showTelemetryPill,
                        onShowTelemetryPillChange = onShowTelemetryPillChange,
                        onOpenMonitorGrid = onOpenMonitorGrid,
                        pcConnectMode = pcConnectMode,
                        notice = notice,
                    )

                    PanelTab.INPUT -> InputTab(
                        openKey = openSection,
                        onOpenKey = { openSection = it },
                        inputMode = inputMode,
                        onInputModeChange = onInputModeChange,
                        swapMouseButtons = swapMouseButtons,
                        onSwapMouseButtonsChange = onSwapMouseButtonsChange,
                        onSendTextClick = onSendTextClick,
                        lastClipboard = lastClipboard,
                        clipboardSyncEnabled = clipboardSyncEnabled,
                        pointerVisible = pointerVisible,
                        onPointerVisibilityChange = onPointerVisibilityChange,
                        pointerStyle = pointerStyle,
                        onPointerStyle = onPointerStyle,
                        pointerSize = pointerSize,
                        onPointerSize = onPointerSize,
                        pointerSensitivity = pointerSensitivity,
                        onPointerSensitivity = onPointerSensitivity,
                        pointerAcceleration = pointerAcceleration,
                        onPointerAcceleration = onPointerAcceleration,
                        inertialScroll = inertialScroll,
                        onInertialScroll = onInertialScroll,
                        edgeScrollZone = edgeScrollZone,
                        onEdgeScrollZone = onEdgeScrollZone,
                        gamepadEnabled = gamepadEnabled,
                        stickMode = stickMode,
                        onStickModeChange = onStickModeChange,
                        onGamepadEnabled = onGamepadEnabled,
                        gyroMouseEnabled = gyroMouseEnabled,
                        onGyroMouseEnabled = onGyroMouseEnabled,
                    )

                    PanelTab.BUTTONS -> ButtonsTab(
                        openKey = openSection,
                        onOpenKey = { openSection = it },
                        keys = keys,
                        hudProfile = hudProfile,
                        onSelectHudProfile = onSelectHudProfile,
                        onAddKey = onAddKey,
                        onEditKey = onEditKey,
                        onDeleteKey = onDeleteKey,
                        mappingMode = mappingMode,
                        onMappingModeChange = onMappingModeChange,
                        plate = plate,
                        onPlate = onPlate,
                        haptics = haptics,
                        onHaptics = onHaptics,
                        onResetCluster = onResetCluster,
                        onExportLayoutJson = onExportLayoutJson,
                        onOpenLayoutJson = onOpenLayoutJson,
                        showHudButtons = showHudButtons,
                        onShowHudButtonsChange = onShowHudButtonsChange,
                        autoHideHudOnExternalInput = autoHideHudOnExternalInput,
                        onAutoHideHudOnExternalInputChange = onAutoHideHudOnExternalInputChange,
                        hudOpacity = hudOpacity,
                        onHudOpacityChange = onHudOpacityChange,
                    )

                    PanelTab.SESSION -> SessionTab(
                        deviceId = deviceId,
                        notice = notice,
                        openKey = openSection,
                        onOpenKey = { openSection = it },
                        onScreenshot = onScreenshot,
                        onLockRemotePc = onLockRemotePc,
                        onActivatePrivacyCurtain = onActivatePrivacyCurtain,
                        onDisconnect = onDisconnect,
                        recordingLabel = recordingLabel,
                        recordingStatus = recordingStatus,
                        recordingActionEnabled = recordingActionEnabled,
                        recordingActive = recordingActive,
                        onToggleRecording = onToggleRecording,
                        coreInfo = coreInfo,
                        onCopyCoreInfo = onCopyCoreInfo,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ tab: layar

@Composable
private fun ScreenTab(
    deviceId: String,
    remoteSize: String,
    zoomPercent: Int,
    remoteDpi: Int,
    resolution: String,
    rotation: String,
    custom: String,
    autoFit: Boolean,
    onAutoFitChange: (Boolean) -> Unit,
    onCustomChange: (String) -> Unit,
    onCustomApply: () -> Unit,
    onResolution: (String) -> Unit,
    onRotation: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onZoomActual: () -> Unit,
    onZoomScale: (Float) -> Unit,
    onRemoteDpiChange: (Int) -> Unit,
    onDisplayRefreshPreferenceChange: (Int) -> Unit,
    showTelemetryPill: Boolean,
    onShowTelemetryPillChange: (Boolean) -> Unit,
    onOpenMonitorGrid: () -> Unit,
    pcConnectMode: Boolean = false,
    notice: SeiNoticeState,
    openKey: String?,
    onOpenKey: (String?) -> Unit,
) {
    val context = LocalContext.current
    var pcOptions by remember(deviceId) { mutableStateOf(RdpOptions.of(context, deviceId)) }

    PanelSectionGrid(openKey = openKey, onOpenKey = onOpenKey) {
    section(
        "telemetri-kontrol-monitor-fisik-pc",
        if (pcConnectMode) sei( "Live Telemetry & Physical PC Monitor")
        else sei( "Live Telemetry & Multi-Monitor / RDP Sessions"),
        advanced = true,
    ) {
            SeiToggleRow(
                title = sei( "Live telemetry status (UI updates, latency, network)"),
                subtitle = sei( "FPS is the app display refresh rate (frames per second drawn on the phone). Show it with latency, resolution, codec, relay, and network."),
                checked = showTelemetryPill,
                onCheckedChange = onShowTelemetryPillChange,
            )
            SeiPillButton(
                text = if (pcConnectMode) {
                    sei( "1:1 Physical Monitor & Direct Stream Engine")
                } else {
                    sei( "Active Monitors & Multi-User / Console Grid")
                },
                onClick = onOpenMonitorGrid,
                icon = SeiIcons.DualMonitor,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

    section("orientasi",sei( "Orientation")) {
            // Dulu label mentah "Auto/Portrait/Landscape" — satu-satunya baris
            // Inggris di panel Indonesia.
            val labels = listOf(
                sei( "Auto"),
                sei( "Portrait"),
                sei( "Landscape"),
            )
            SeiSegmented(
                options = labels,
                selectedIndex = DisplayPrefs.rotations.indexOf(rotation).coerceAtLeast(0),
                onSelect = { onRotation(DisplayPrefs.rotations[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ------------------------------------------------------------------ tab: input

@Composable
private fun InputTab(
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    swapMouseButtons: Boolean,
    onSwapMouseButtonsChange: (Boolean) -> Unit,
    onSendTextClick: () -> Unit,
    lastClipboard: String?,
    clipboardSyncEnabled: Boolean,
    pointerVisible: Boolean,
    onPointerVisibilityChange: (Boolean) -> Unit,
    pointerStyle: PointerStyle,
    onPointerStyle: (PointerStyle) -> Unit,
    pointerSize: Float,
    onPointerSize: (Float) -> Unit,
    pointerSensitivity: Float,
    onPointerSensitivity: (Float) -> Unit,
    pointerAcceleration: Boolean,
    onPointerAcceleration: (Boolean) -> Unit,
    inertialScroll: Boolean,
    onInertialScroll: (Boolean) -> Unit,
    edgeScrollZone: Boolean,
    onEdgeScrollZone: (Boolean) -> Unit,
    gamepadEnabled: Boolean,
    stickMode: SeiStickMode,
    onStickModeChange: (SeiStickMode) -> Unit,
    onGamepadEnabled: (Boolean) -> Unit,
    gyroMouseEnabled: Boolean,
    onGyroMouseEnabled: (Boolean) -> Unit,
    openKey: String?,
    onOpenKey: (String?) -> Unit,
) {
    PanelSectionGrid(openKey = openKey, onOpenKey = onOpenKey) {
    section("mode-input-klik-mouse",sei( "Input mode & Mouse Click")) {
            SeiSegmented(
                options = InputMode.entries.map { sei( it.titleEn) },
                selectedIndex = inputMode.ordinal,
                onSelect = { onInputModeChange(InputMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            PanelHint(sei( InputMode.entries[inputMode.ordinal].detailEn))
            // Switch Ikon Mouse Kiri <-> Kanan (tanpa bergantung pada teks)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (swapMouseButtons) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    )
                    .border(
                        1.dp,
                        if (swapMouseButtons) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSwapMouseButtonsChange(!swapMouseButtons) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (swapMouseButtons) SeiIcons.ClickRight else SeiIcons.ClickLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Icon(
                        SeiIcons.MouseSwap,
                        contentDescription = sei( "Swap Left & Right Click"),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (swapMouseButtons) SeiIcons.ClickLeft else SeiIcons.ClickRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                io.seidesk.rm.ui.components.SeiSwitch(
                    checked = swapMouseButtons,
                    onCheckedChange = onSwapMouseButtonsChange,
                )
            }
            SeiPillButton(
                sei( "Send text to remote"),
                onSendTextClick,
                icon = SeiIcons.Keyboard,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

    section("fisika-trackpad-sensor",sei( "Trackpad Physics & Sensors"), advanced = true) {
            PanelHint(
                sei(
                    "Pointer sensitivity: {0}x",
                    String.format(java.util.Locale.US, "%.1f", pointerSensitivity),
                ),
            )
            SeiSlider(
                value = pointerSensitivity,
                onValueChange = onPointerSensitivity,
                valueRange = 0.4f..3.0f,
            )
            SeiToggleRow(
                title = sei( "Dynamic pointer acceleration"),
                subtitle = sei( "Fast swipes cross the screen, slow moves stay pixel-accurate"),
                checked = pointerAcceleration,
                onCheckedChange = onPointerAcceleration,
            )
            SeiToggleRow(
                title = sei( "Inertial scroll (momentum)"),
                subtitle = sei( "Two-finger scroll glides smoothly after release"),
                checked = inertialScroll,
                onCheckedChange = onInertialScroll,
            )
            SeiToggleRow(
                title = sei( "Right-edge scroll strip"),
                subtitle = sei( "Swipe 1 finger along the right screen edge for mouse wheel"),
                checked = edgeScrollZone,
                onCheckedChange = onEdgeScrollZone,
            )
            SeiToggleRow(
                title = sei( "Physical Gamepad & Joystick"),
                subtitle = sei( "Left stick WASD, right stick mouse, A/B click"),
                checked = gamepadEnabled,
                onCheckedChange = onGamepadEnabled,
            )
            PanelHint(
                sei(
                    "Left stick mode: Pointer = cursor, WASD/Arrows = keys. The gamepad's right stick is always the cursor.",
                ),
            )
            SeiSegmented(
                options = listOf(sei( "Pointer"), sei( "WASD"), sei( "Arrows")),
                selectedIndex = SeiStickMode.entries.indexOf(stickMode).coerceAtLeast(0),
                onSelect = { onStickModeChange(SeiStickMode.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            SeiToggleRow(
                title = sei( "Gyro Air-Mouse (phone motion)"),
                subtitle = sei( "Aim the cursor by tilting your phone like a laser pointer"),
                checked = gyroMouseEnabled,
                onCheckedChange = onGyroMouseEnabled,
            )
        }

    section("tombol-notifikasi-kustom", sei( "Session notification buttons"), advanced = true) {
        val notifContext = androidx.compose.ui.platform.LocalContext.current
        val notifPrefs = remember { AppPrefs(notifContext) }
        var notifHome by remember { mutableStateOf(notifPrefs.notifHomeButton) }
        var notifMusic by remember { mutableStateOf(notifPrefs.notifMusicButton) }
        fun refreshNotif() {
            runCatching {
                notifContext.startService(
                    android.content.Intent(notifContext, io.seidesk.rm.SeiSessionService::class.java),
                )
            }
        }
        SeiToggleRow(
            title = sei( "\"Home\" button in notification"),
            subtitle = sei( "Return home without disconnecting"),
            checked = notifHome,
            onCheckedChange = { v -> notifHome = v; notifPrefs.notifHomeButton = v; refreshNotif() },
        )
        SeiToggleRow(
            title = sei( "\"Music\" button in notification"),
            subtitle = sei( "Play/pause music from the notification"),
            checked = notifMusic,
            onCheckedChange = { v -> notifMusic = v; notifPrefs.notifMusicButton = v; refreshNotif() },
        )
    }

    section("clipboard-otomatis-teks-gambar",sei( "Automatic clipboard (Text & Image)"), advanced = true) {
            PanelHint(
                if (clipboardSyncEnabled) {
                    sei(
                        "Two-way sync is active automatically: text and images/screenshots copied on PC go straight to the phone clipboard, and vice versa.",
                    )
                } else {
                    sei(
                        "Clipboard channel is off in device settings. Enable it and reconnect for automatic PC and phone clipboard sync.",
                    )
                },
            )
            if (clipboardSyncEnabled && !lastClipboard.isNullOrEmpty()) {
                PanelHint(
                    sei(
                        "Status: latest remote text has been automatically copied to the phone clipboard.",
                    ),
                )
            }
            ClipboardHistoryBlock(clipboardSyncEnabled = clipboardSyncEnabled)
        }

    section("pointer",sei( "Pointer")) {
            SeiToggleRow(
                title = sei( "Show pointer"),
                checked = pointerVisible,
                onCheckedChange = onPointerVisibilityChange,
            )
            PanelHint(
                sei(
                    "Pointer shape follows the cursor sent by the server (arrow, hand, I-beam).",
                ),
            )
            SeiSegmented(
                options = PointerStyle.entries.map { sei( it.titleEn) },
                selectedIndex = pointerStyle.ordinal,
                onSelect = { onPointerStyle(PointerStyle.entries[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
            PanelHint(sei( "Pointer size: {0} dp", pointerSize.toInt()))
            SeiSlider(value = pointerSize, onValueChange = onPointerSize, valueRange = 10f..52f)
        }
    }
}

// ------------------------------------------------------------- tab: tombol

@Composable
private fun ButtonsTab(
    keys: List<HudKey>,
    hudProfile: HudProfilePreset,
    onSelectHudProfile: (HudProfilePreset) -> Unit,
    onAddKey: () -> Unit,
    onEditKey: (HudKey) -> Unit,
    onDeleteKey: (HudKey) -> Unit,
    mappingMode: Boolean,
    onMappingModeChange: (Boolean) -> Unit,
    plate: HudPlate,
    onPlate: (HudPlate) -> Unit,
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    onResetCluster: () -> Unit,
    onExportLayoutJson: () -> Unit,
    onOpenLayoutJson: () -> Unit,
    showHudButtons: Boolean,
    onShowHudButtonsChange: (Boolean) -> Unit,
    autoHideHudOnExternalInput: Boolean,
    onAutoHideHudOnExternalInputChange: (Boolean) -> Unit,
    hudOpacity: Float,
    onHudOpacityChange: (Float) -> Unit,
    openKey: String?,
    onOpenKey: (String?) -> Unit,
) {
    PanelSectionGrid(openKey = openKey, onOpenKey = onOpenKey) {
    section("tampilan-tombol-overlay",sei( "Overlay button display"), advanced = true) {
            SeiToggleRow(
                title = sei( "Show HUD buttons"),
                subtitle = sei(
                    "You can hide these any time with the eye button on the session rail.",
                ),
                checked = showHudButtons,
                onCheckedChange = onShowHudButtonsChange,
            )
            SeiToggleRow(
                title = sei( "Auto-hide when physical input is connected"),
                subtitle = sei(
                    "Detects external keyboards, mice, gamepads, and D-pads. Menu, phone keyboard, monitors, and disconnect stay available.",
                ),
                checked = autoHideHudOnExternalInput,
                onCheckedChange = onAutoHideHudOnExternalInputChange,
            )
            PanelHint(
                sei(
                    "HUD button opacity: {0}% (0% transparent, 100% opaque). Only overlay buttons change; the remote screen and rail are unaffected.",
                    (hudOpacity * 100).roundToInt(),
                ),
            )
            SeiSlider(
                value = hudOpacity,
                onValueChange = onHudOpacityChange,
                valueRange = 0f..1f,
            )
        }

    section("preset-profil-hud",sei( "HUD Profile Presets")) {
            PanelHint(
                sei(
                    "Switch button layouts instantly by activity: Standard, Coding/Terminal, Gaming/WASD, Office, or Design/Video.",
                ),
            )
            HudProfilePreset.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { preset ->
                        SeiPillButton(
                            text = sei( preset.titleEn),
                            onClick = { onSelectHudProfile(preset) },
                            primary = preset == hudProfile,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

    section("tombol-kontrol-0",sei( "Control buttons ({0})", keys.size)) {
            PanelHint(
                if (mappingMode) {
                    sei(
                        "Layout mode is ON: drag buttons where you want them, tap a " +
                            "button to change its action/size, or press + Add at the top bar.",
                    )
                } else {
                    sei(
                        "Press \"Edit layout & size\" to drag buttons or add controls from the top bar (Combos, F1-F12, Single Key, Numpad, Modifiers, Mouse).",
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiPillButton(
                    sei( "+ Add button"),
                    onAddKey,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SeiPillButton(
                    if (mappingMode) sei( "Done layout") else sei( "Edit layout"),
                    { onMappingModeChange(!mappingMode) },
                    primary = mappingMode,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            HudLayoutPreview(keys)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiPillButton(
                    sei( "Copy JSON"),
                    onExportLayoutJson,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SeiPillButton(
                    sei( "Import JSON"),
                    onOpenLayoutJson,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SeiPillButton(
                sei( "Restore default"),
                onResetCluster,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PanelHint(
                sei(
                    "The preview below uses the real button colors over a sample desktop, so it matches what you get during a session.",
                ),
            )
            HudPlatePicker(
                selected = plate,
                onSelect = onPlate,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (keys.isEmpty()) {
                    PanelHint(
                        sei(
                            "No buttons yet. Press \"+ Add button\".",
                        ),
                    )
                }
                keys.forEach { key -> KeyRow(key, onEditKey, onDeleteKey) }
            }
            SeiToggleRow(
                title = sei( "Haptic feedback on button press"),
                checked = haptics,
                onCheckedChange = onHaptics,
            )
        }
    }
}

// -------------------------------------------------------------- tab: sesi

@Composable
private fun SessionTab(
    deviceId: String,
    notice: io.seidesk.rm.ui.components.SeiNoticeState,
    onScreenshot: () -> Unit,
    onLockRemotePc: () -> Unit,
    onActivatePrivacyCurtain: () -> Unit,
    onDisconnect: () -> Unit,
    recordingLabel: String,
    recordingStatus: String?,
    recordingActionEnabled: Boolean,
    recordingActive: Boolean,
    onToggleRecording: () -> Unit,
    coreInfo: List<String>,
    onCopyCoreInfo: () -> Unit,
    openKey: String?,
    onOpenKey: (String?) -> Unit,
) {
    PanelSectionGrid(openKey = openKey, onOpenKey = onOpenKey) {
    section("keamanan-privasi-cepat",sei( "Quick Security & Privacy")) {
            PanelHint(
                sei(
                    "Remotely lock the Windows session (Win+L) or activate the Privacy Curtain to black out the phone screen temporarily.",
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiPillButton(
                    sei( "Lock PC"),
                    onLockRemotePc,
                    icon = SeiIcons.Lock,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SeiPillButton(
                    sei( "Privacy Curtain"),
                    onActivatePrivacyCurtain,
                    icon = SeiIcons.EyeOff,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

    section("perekaman-lokal-pc-ke-hp",sei( "Local recording · PC to phone"), advanced = true) {
            PanelHint(
                sei(
                    "The PC desktop and its audio are recorded into a single MP4 in Movies/SeiDesk-RM. The phone screen and controls are never captured. If merging fails, video and audio are saved as two files. Recording is blocked while FLAG_SECURE is on.",
                ),
            )
            SeiPillButton(
                text = recordingLabel,
                onClick = onToggleRecording,
                enabled = recordingActionEnabled,
                primary = recordingActive,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            recordingStatus?.let { status ->
                Text(
                    status,
                    color = if (recordingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }

    section("transfer-file",sei( "File transfer phone ↔ PC")) {
            TransferFileSection(deviceId = deviceId, notice = notice)
        }

    section("sesi",sei( "Session")) {
            PanelHint(
                sei(
                    "The bottom-right round button (power icon) also disconnects — returns straight to home after confirmation.",
                ),
            )
            SeiPillButton(
                sei( "Take screenshot"),
                onScreenshot,
                icon = SeiIcons.Shot,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SeiPillButton(
                sei( "Disconnect"),
                onDisconnect,
                icon = SeiIcons.Power,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (coreInfo.isNotEmpty()) {
            PanelSection(sei( "Technical info")) {
                coreInfo.forEach { line ->
                    Text(
                        line,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                SeiPillButton(
                    sei( "Copy technical info"),
                    onCopyCoreInfo,
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PanelChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(SeiPill)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SeiPill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
    }
}

@Composable
internal fun PanelHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

/**
 * Tab panel sebagai deretan kotak kaca, bukan strip tersegmentasi.
 *
 * Tinggi dan jejak kakinya sama dengan [SeiSegmented] yang digantikannya, jadi
 * isi panel tidak bertambah panjang dan tidak menambah scroll — yang berubah
 * hanya bentuknya jadi kotak, sesuai permintaan pengguna.
 */
@Composable
private fun PanelTabTiles(
    tab: PanelTab,
    onTab: (PanelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PanelTab.entries.forEach { entry ->
            val active = entry == tab
            val bg by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color.Transparent
                },
                animationSpec = tween(durationMillis = 150),
                label = "panelTabBg",
            )
            val fg by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(durationMillis = 150),
                label = "panelTabFg",
            )
            val label = sei( entry.en)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .seiSurface(shape = RoundedCornerShape(13.dp), strength = 0.9f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(bg)
                    .clickable { onTab(entry) }
                    .semantics { contentDescription = label }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    panelTabIcon(entry),
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    label,
                    color = fg,
                    fontSize = 10.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun panelTabIcon(entry: PanelTab): ImageVector = when (entry) {
    PanelTab.SCREEN -> SeiIcons.Fit
    PanelTab.INPUT -> SeiIcons.Keyboard
    PanelTab.BUTTONS -> SeiIcons.Grid
    PanelTab.SESSION -> SeiIcons.Gear
}

/**
 * Ikon kotak untuk satu seksi panel. Dicocokkan dari kunci seksi, bukan judul,
 * supaya perubahan terjemahan judul tidak mengubah ikonnya.
 */
private fun panelSectionIcon(key: String): ImageVector = when {
    key.startsWith("telemetri") -> SeiIcons.Monitor
    key.startsWith("ukuran-tampilan") -> SeiIcons.Fit
    key.startsWith("mesin-direct-pc-stream") -> SeiIcons.Terminal
    key.startsWith("skala-tampilan-windows") -> SeiIcons.Windows
    key.startsWith("resolusi-desktop") -> SeiIcons.DualMonitor
    key.startsWith("orientasi") -> SeiIcons.Rotate
    key.startsWith("mode-input") -> SeiIcons.Mouse
    key.startsWith("fisika-trackpad") -> SeiIcons.Sliders
    key.startsWith("clipboard") -> SeiIcons.Copy
    key.startsWith("pointer") -> SeiIcons.Cursor
    key.startsWith("tampilan-tombol") -> SeiIcons.Eye
    key.startsWith("preset-profil-hud") -> SeiIcons.Star
    key.startsWith("tombol-kontrol") -> SeiIcons.Grid
    key.startsWith("sumber-musik") -> SeiIcons.Users
    key.startsWith("kontrol-pemutar") -> SeiIcons.Sliders
    key.startsWith("antrian-musik") -> SeiIcons.Queue
    key.startsWith("pustaka-musik") -> SeiIcons.Library
    key.startsWith("transfer-file") -> SeiIcons.Swap
    key.startsWith("keamanan") -> SeiIcons.Shield
    key.startsWith("perekaman") -> SeiIcons.Shot
    key.startsWith("sesi") -> SeiIcons.Power
    key.startsWith("info-teknis") -> SeiIcons.Info
    else -> SeiIcons.ChevronRight
}

/**
 * Cakupan pembangun [PanelSectionGrid].
 *
 * Seksi direkam sebagai data dulu, tidak langsung digambar. Inilah yang membuat
 * panel bisa tampil sebagai kotak-kotak: selama belum ada seksi yang dibuka, yang
 * digambar hanya ikon + judul, jadi satu tab muat tanpa digulir. Isi seksi baru
 * disusun untuk satu seksi yang sedang terbuka.
 */
internal class PanelSectionScope {
    internal class Spec(
        val key: String,
        val title: String,
        val advanced: Boolean = false,
        val content: @Composable () -> Unit,
    )

    internal val specs: MutableList<Spec> = mutableListOf()

    /**
     * Rekam satu seksi. [content] belum dijalankan di sini.
     * [advanced] = seksi jarang dipakai; disembunyikan di balik ubin
     * "Lanjutan" supaya menu utama tetap pendek dan bersih.
     */
    fun section(
        key: String,
        title: String,
        advanced: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        specs.add(Spec(key, title, advanced, content))
    }
}

/**
 * Daftar seksi panel sebagai grid kotak dengan drill-in.
 *
 * Menggantikan accordion. Satu tab berisi 3-6 seksi dan tiap seksi memuat banyak
 * blok penjelasan, jadi accordion pun tetap menuntut scroll. Di sini tab digambar
 * sebagai dua kolom kotak; mengetuk satu kotak membuka seksi itu penuh lebar
 * dengan tombol kembali. Seksi bersarang di dalam badan seksi tetap memakai
 * [PanelSection] biasa.
 *
 * [builder] dijalankan tiap komposisi karena judul seksi memakai `xy()` yang
 * composable dan beberapa seksi bersyarat (`if (pcConnectMode)`). Scope dibuat
 * baru tiap kali supaya seksi dari komposisi sebelumnya tidak tertinggal.
 */
@Composable
internal fun PanelSectionGrid(
    openKey: String?,
    onOpenKey: (String?) -> Unit,
    builder: @Composable PanelSectionScope.() -> Unit,
) {
    val scope = PanelSectionScope()
    scope.builder()
    val allSpecs = scope.specs
    if (allSpecs.isEmpty()) return
    // Menu utama hanya seksi inti; seksi lanjutan muncul setelah ubin
    // "Lanjutan" diketuk supaya tiap tab singkat dilihat sekilas.
    val advancedCount = allSpecs.count { it.advanced }
    var showAdvanced by remember { mutableStateOf(false) }
    val specs = if (showAdvanced) allSpecs else allSpecs.filter { !it.advanced }
    if (specs.isEmpty()) return
    // Grid dua kolom, dan seksi yang terbuka MELEBAR TEPAT DI BAWAH barisnya.
    //
    // Sebelumnya versi ini memakai drill-in: mengetuk kotak mengganti seluruh
    // tab dengan satu seksi plus tombol kembali. Pengguna menolaknya —
    // "lebih gampang malah makin susah" — karena menambah satu lapis ketukan
    // dan menyembunyikan seksi lain. Di sini satu ketukan langsung membuka isi
    // di tempat, kotak lain tetap terlihat, dan ketukan kedua menutupnya lagi.
    Column(
        Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        specs.chunked(2).forEach { rowSpecs ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowSpecs.forEach { spec ->
                    val selected = spec.key == openKey
                    Box(Modifier.weight(1f)) {
                        SeiGlassTile(
                            icon = panelSectionIcon(spec.key),
                            title = spec.title,
                            onClick = { onOpenKey(if (selected) null else spec.key) },
                            selected = selected,
                            titleMaxLines = 2,
                            trailing = {
                                Icon(
                                    imageVector = if (selected) SeiIcons.ChevronUp else SeiIcons.ChevronDown,
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
                // Pengisi baris ganjil: tanpa ini ubin sendirian melebar
                // sepenuh baris dan grid terlihat berantakan.
                repeat(2 - rowSpecs.size) { Spacer(Modifier.weight(1f)) }
            }
            val open = rowSpecs.firstOrNull { it.key == openKey }
            if (open != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .seiSurface(shape = RoundedCornerShape(14.dp), opacity = 1.25f, strength = 0.9f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        open.title.uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    open.content()
                }
            }
        }
        if (advancedCount > 0) {
            SeiGlassTile(
                icon = SeiIcons.Sliders,
                title = if (showAdvanced) {
                    sei( "Hide advanced sections")
                } else {
                    sei(
                        "Advanced ({0} more sections)",
                        advancedCount,
                    )
                },
                onClick = {
                    showAdvanced = !showAdvanced
                    // Seksi lanjutan yang sedang terbuka ikut tertutup supaya
                    // isi yang disembunyikan tidak menggantung tanpa ubinnya.
                    if (!showAdvanced) onOpenKey(null)
                },
                selected = false,
                titleMaxLines = 1,
                trailing = {
                    Icon(
                        imageVector = if (showAdvanced) SeiIcons.ChevronUp else SeiIcons.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
internal fun PanelSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Setiap seksi digambar sebagai kartu yang bisa dilipat. Satu fungsi ini
    // dipakai 17 seksi di empat tab, jadi seluruh panel ikut berubah tanpa
    // menyentuh masing-masing tab.
    //
    // Alasan accordion: satu tab berisi 3-6 seksi dengan total 24 blok teks
    // penjelasan, sehingga tidak mungkin semua muat sekaligus di layar HP.
    // Dengan seksi terlipat jadi judul saja, satu tab muat tanpa scroll;
    // seksi pertama tiap tab tetap terbuka supaya isinya langsung kelihatan.
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column(
        Modifier
            .fillMaxWidth()
            // Kartu kaca: satu perubahan di sini mengubah 18 seksi di empat tab,
            // karena semua seksi digambar lewat fungsi ini.
            .seiSurface(shape = RoundedCornerShape(14.dp), opacity = 1.25f, strength = 0.9f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title.uppercase(),
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = if (expanded) SeiIcons.ChevronUp else SeiIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
        }
    }
}

@Composable
private fun KeyRow(
    key: HudKey,
    onEdit: (HudKey) -> Unit,
    onDelete: (HudKey) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .clickable { onEdit(key) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HudKeyGlyph(key = key, tint = MaterialTheme.colorScheme.onSurface, boxDp = 30f)
        }
        Column(Modifier.weight(1f)) {
            Text(
                key.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sei(
                    "{0} · {1} · {2} dp · {3}%,{4}%",
                    key.kind.titleEn,
                    key.action.titleEn,
                    key.size.toInt(),
                    (key.x * 100).toInt(),
                    (key.y * 100).toInt(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { onDelete(key) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SeiIcons.Trash,
                contentDescription = sei( "Delete"),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private data class MonitorGridItem(
    val badge: String,
    val title: String,
    val subtitle: String,
    val value: String,
    val icon: ImageVector,
    val active: Boolean,
)

/**
 * Modal Grid Monitor & Sesi User Aktif (dibuka dari tombol Monitor di atas Disconnect).
 * Mendeteksi otomatis monitor aktif (DISP / resolusi sesi) serta sesi konsol (/admin)
 * maupun akun user tersimpan untuk berpindah secara langsung saat sesi berlangsung.
 */
@Composable
private fun MonitorAndUserGridModal(
    deviceId: String,
    hostLabel: String,
    remoteSize: String,
    codecLabel: String,
    resolution: String,
    pcConnectMode: Boolean,
    activeUsername: String?,
    consoleAdminMode: Boolean,
    onSelectMonitorResolution: (String) -> Unit,
    onSwitchConsoleMode: (Boolean) -> Unit,
    onSwitchUserSession: (String, String?, String?) -> Unit,
    onActivatePrivacyCurtain: () -> Unit,
    onLockRemotePc: () -> Unit,
    onOpenHome: () -> Unit,
    notice: SeiNoticeState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pcOptions by remember(deviceId) { mutableStateOf(RdpOptions.of(context, deviceId)) }
    val repo = remember { io.seidesk.rm.sessions.SessionsRepository(context) }
    val allFavorites by repo.favorites().collectAsState(initial = emptyList())
    val savedAccounts = remember(allFavorites) {
        allFavorites
            .filter { !it.username.isNullOrBlank() && !it.username.equals("XyDesk", ignoreCase = true) }
            .distinctBy { "${it.domain.orEmpty().lowercase()}\\${it.username.orEmpty().lowercase()}" }
    }
    var customUserOpen by remember { mutableStateOf(false) }
    var customUsername by remember { mutableStateOf(activeUsername.orEmpty()) }
    var customDomain by remember { mutableStateOf("") }
    var customPassword by remember { mutableStateOf("") }

    val cleanRemoteSize = remoteSize.replace(" ", "")
    val monitorItems = listOf(
        MonitorGridItem(
            badge = "MON 1",
            title = sei( "Monitor 1 · Primary"),
            subtitle = if (cleanRemoteSize.isNotBlank() && cleanRemoteSize != "--") "$cleanRemoteSize · 16:9" else "1280x720 · 16:9",
            value = DisplayPrefs.AUTOMATIC,
            icon = SeiIcons.Monitor,
            active = resolution == DisplayPrefs.AUTOMATIC || resolution == "1280x720",
        ),
        MonitorGridItem(
            badge = "MON 2",
            title = sei( "Monitor 2 · QHD"),
            subtitle = "2560x1440 · Studio",
            value = "2560x1440",
            icon = SeiIcons.DualMonitor,
            active = resolution == "2560x1440",
        ),
        MonitorGridItem(
            badge = "SPAN",
            title = sei( "Dual-Monitor Span"),
            subtitle = "2560x1080 · 21:9",
            value = "2560x1080",
            icon = SeiIcons.DualMonitor,
            active = resolution == "2560x1080",
        ),
        MonitorGridItem(
            badge = "VIEW",
            title = sei( "Phone Native View"),
            subtitle = sei( "Follow Viewport"),
            value = DisplayPrefs.FOLLOW,
            icon = SeiIcons.Fit,
            active = resolution == DisplayPrefs.FOLLOW,
        ),
    )

    SeiOverlay(
        title = if (pcConnectMode) {
            sei( "1:1 Physical Monitor & Direct Stream Engine")
        } else {
            sei( "Active Monitors & Multi-User / Console Grid")
        },
        maxWidth = 440.dp,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Baris status deteksi otomatis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    SeiIcons.DualMonitor,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "$hostLabel · $remoteSize",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (pcConnectMode) {
                                append(sei( "PC Connect · 1:1 Direct Stream"))
                                append(" · ${pcOptions.pcStreamEngine.badge}")
                            } else {
                                append(
                                    if (consoleAdminMode) sei( "Physical / Runner Console (/admin)")
                                    else sei( "Multi-User Virtual RDP Session"),
                                )
                                if (!activeUsername.isNullOrBlank()) append(" · $activeUsername")
                            }
                            append(" · $codecLabel")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (pcConnectMode) {
                // Mode Koneksi PC: Eksklusif Direct Stream Engine (Tidak ada Multi-User RDP)
                Text(
                    text = sei( "DIRECT PC STREAM ENGINE (PC CONNECT EXCLUSIVE)"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                )
                io.seidesk.rm.core.SeiPcStreamEngine.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { engine ->
                            val isActive = pcOptions.pcStreamEngine == engine
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    )
                                    .border(
                                        if (isActive) 1.4.dp else 1.dp,
                                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(11.dp),
                                    )
                                    .clickable {
                                        val next = pcOptions.withPcStreamEngine(engine)
                                        pcOptions = next
                                        next.write(context, deviceId)
                                        notice.show(seiNow( "Direct Stream Engine: {0} active", engine.badge))
                                    }
                                    .padding(10.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            SeiIcons.Monitor,
                                            contentDescription = null,
                                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = if (isActive) sei( "ACTIVE") else engine.badge,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = sei( engine.titleEn),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = sei( engine.detailEn),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiPillButton(
                        text = sei( "Stealth Screen (Privacy)"),
                        icon = SeiIcons.EyeOff,
                        onClick = onActivatePrivacyCurtain,
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        text = sei( "Lock Physical PC (Win+L)"),
                        icon = SeiIcons.Windows,
                        onClick = onLockRemotePc,
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // Mode Koneksi RDP: Grid Monitor Virtual + Multi-User & Konsol Runner
                Text(
                    text = sei( "ACTIVE MONITORS & DISPLAYS GRID"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                )
                monitorItems.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (item.active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    )
                                    .border(
                                        if (item.active) 1.4.dp else 1.dp,
                                        if (item.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(11.dp),
                                    )
                                    .clickable {
                                        onSelectMonitorResolution(item.value)
                                    }
                                    .padding(10.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = null,
                                            tint = if (item.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(17.dp),
                                        )
                                        Text(
                                            text = if (item.active) sei( "ACTIVE") else item.badge,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = item.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.subtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Grid Sesi User & Konsol Runner Windows
                Text(
                    text = sei( "WINDOWS SESSIONS: RUNNER CONSOLE (/ADMIN) VS MULTI-USER"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                )
                val isConsole = consoleAdminMode && !activeUsername.equals("XyDesk", ignoreCase = true)
                val isVirtualUser = !isConsole
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Card 1: Sesi Konsol Runner (/admin)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (isConsole) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            )
                            .border(
                                if (isConsole) 1.4.dp else 1.dp,
                                if (isConsole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(11.dp),
                            )
                            .clickable { onSwitchConsoleMode(true) }
                            .padding(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    SeiIcons.Monitor,
                                    contentDescription = null,
                                    tint = if (isConsole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp),
                                )
                                Text(
                                    text = if (isConsole) sei( "ACTIVE") else "/ADMIN",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConsole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = sei( "Runner / Admin Console"),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                text = sei( "Session 1 Console (runneradmin)"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                            )
                        }
                    }

                    // Card 2: Sesi User RDP Virtual
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                if (isVirtualUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            )
                            .border(
                                if (isVirtualUser) 1.4.dp else 1.dp,
                                if (isVirtualUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(11.dp),
                            )
                            .clickable { onSwitchConsoleMode(false) }
                            .padding(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    SeiIcons.Users,
                                    contentDescription = null,
                                    tint = if (isVirtualUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp),
                                )
                                Text(
                                    text = if (isVirtualUser) sei( "ACTIVE") else "MULTI",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVirtualUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = sei( "Multi-User Virtual Session"),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = sei( "Separate Session 2 (XyDesk)"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Target Akun Cepat (Runneradmin vs XyDesk + Akun Tersimpan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isRunnerActive = activeUsername.equals("runneradmin", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .border(
                                1.dp,
                                if (isRunnerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                RdpOptions.of(context, deviceId).copy(consoleAdmin = true).write(context, deviceId)
                                onSwitchUserSession("runneradmin", null, null)
                            }
                            .padding(9.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "runneradmin (/admin)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = sei( "Switch to Runner Console"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                            )
                        }
                    }
                    val isXyDeskActive = activeUsername.equals("XyDesk", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .border(
                                1.dp,
                                if (isXyDeskActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                RdpOptions.of(context, deviceId).copy(consoleAdmin = false).write(context, deviceId)
                                onSwitchUserSession("XyDesk", null, null)
                            }
                            .padding(9.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "XyDesk (Multi-User)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = sei( "Switch to Virtual Session 2"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                            )
                        }
                    }
                }

                if (savedAccounts.isNotEmpty()) {
                    savedAccounts.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { acc ->
                                val accUser = acc.username.orEmpty()
                                val isCurrentAcc = !activeUsername.isNullOrBlank() &&
                                    activeUsername.equals(accUser, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                        .border(
                                            1.dp,
                                            if (isCurrentAcc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable {
                                            onSwitchUserSession(accUser, acc.domain, acc.password)
                                        }
                                        .padding(9.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        Icon(
                                            SeiIcons.Users,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = acc.label?.takeIf { it.isNotBlank() } ?: accUser,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = accUser,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 9.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                if (customUserOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SeiField(
                            value = customUsername,
                            onValueChange = { customUsername = it },
                            label = sei( "Windows Username"),
                            hint = "runneradmin / Administrator / XyDesk",
                        )
                        SeiField(
                            value = customPassword,
                            onValueChange = { customPassword = it },
                            label = sei( "Password (optional)"),
                            isPassword = true,
                        )
                        SeiPillButton(
                            text = sei( "Switch to This User Now"),
                            icon = SeiIcons.Users,
                            onClick = {
                                val u = customUsername.trim()
                                if (u.isNotEmpty()) {
                                    onSwitchUserSession(
                                        u,
                                        customDomain.trim().takeIf { it.isNotEmpty() },
                                        customPassword.takeIf { it.isNotEmpty() },
                                    )
                                }
                            },
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiPillButton(
                        text = sei( "Switch User"),
                        icon = SeiIcons.Users,
                        onClick = { customUserOpen = !customUserOpen },
                        primary = customUserOpen,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        text = sei( "Windows Lock Screen"),
                        icon = SeiIcons.Windows,
                        onClick = onLockRemotePc,
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiPillButton(
                    text = sei( "Other Devices (Home)"),
                    icon = SeiIcons.Grid,
                    onClick = onOpenHome,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SeiPillButton(
                    text = sei( "Close"),
                    icon = SeiIcons.Close,
                    onClick = onDismiss,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Badge "REC" + waktu berjalan selama perekaman aktif.
 *
 * Selalu tampil selama [recordingActive] benar, termasuk saat panel atau mode
 * pemetaan tombol terbuka, supaya pengguna tidak lupa rekaman masih jalan.
 * Komponen ini hanya digambar saat merekam, jadi `remember` di dalamnya mulai
 * dari nol tiap sesi rekaman baru.
 */
@Composable
private fun RecordingIndicator(modifier: Modifier = Modifier) {
    val startedAt = remember { SystemClock.elapsedRealtime() }
    var elapsedMs by remember { mutableStateOf(0L) }
    var dotOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            dotOn = !dotOn
            delay(500L)
        }
    }
    val totalSeconds = elapsedMs / 1000L
    val clock = "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    MaterialTheme.colorScheme.error.copy(alpha = if (dotOn) 1f else 0.25f),
                    CircleShape,
                ),
        )
        Text(
            text = "REC",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Text(
            text = clock,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
        )
    }
}

/**
 * Riwayat clipboard: 10 teks terakhir yang disalin di HP. Ketuk satu entri
 * untuk menyalinnya ulang; bila kanal clipboard sesi aktif, sinkronisasi
 * otomatis yang sudah ada langsung meneruskannya ke PC.
 */
@Composable
private fun ClipboardHistoryBlock(clipboardSyncEnabled: Boolean) {
    val context = LocalContext.current
    val history by ClipboardHistory.entries.collectAsState()
    LaunchedEffect(Unit) { ClipboardHistory.load(context) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Text(
        sei( "Clipboard history").uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.SemiBold,
    )
    if (history.isEmpty()) {
        Text(
            sei(
                "Nothing copied yet. Copy text on the phone or the PC and this list fills itself (last 10 entries).",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        history.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable {
                        val ok = ClipboardHistory.copyToPhone(context, entry)
                        feedback = when {
                            !ok -> seiNow(
                                "Could not copy to the phone clipboard.",
                            )
                            clipboardSyncEnabled -> seiNow(
                                "Copied — forwarded to the PC automatically.",
                            )
                            else -> seiNow(
                                "Copied to the phone clipboard.",
                            )
                        }
                    }
                    .padding(vertical = 7.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sei(
                            "{0} characters",
                            entry.text.length,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                    )
                }
                SeiPillButton(
                    "\u2715",
                    {
                        ClipboardHistory.remove(context, entry.id)
                        feedback = null
                    },
                    primary = false,
                    compact = true,
                )
            }
        }
    }
    feedback?.let { message ->
        Text(
            message,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 10.sp,
        )
    }
    SeiPillButton(
        sei( "Clear all history"),
        {
            ClipboardHistory.clear(context)
            feedback = null
        },
        primary = false,
        compact = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
