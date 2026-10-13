@file:OptIn(ExperimentalMaterial3Api::class)

package io.seidesk.rm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateMapOf
import io.seidesk.rm.core.LanScanner
import io.seidesk.rm.core.DiscoveredPcHost
import io.seidesk.rm.ui.theme.pressScale
import io.seidesk.rm.ui.ReachabilityProbe
import io.seidesk.rm.ui.ReachabilityResult
import io.seidesk.rm.ui.theme.SeiColors
import io.seidesk.rm.core.ConnectionLog
import io.seidesk.rm.core.ConnectionProfile
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.WakeOnLan
import io.seidesk.rm.core.SeiAudioMode
import io.seidesk.rm.core.SeiStreamProfile
import io.seidesk.rm.security.CrashLog
import io.seidesk.rm.sessions.SessionsRepository
import io.seidesk.rm.ui.components.SeiCard
import io.seidesk.rm.ui.components.SeiDialog
import io.seidesk.rm.ui.components.SeiField
import io.seidesk.rm.ui.components.SeiIconPill
import io.seidesk.rm.ui.components.SeiDivider
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiNoticeHost
import io.seidesk.rm.ui.components.SeiOverlay
import io.seidesk.rm.ui.components.rememberSeiNotice
import io.seidesk.rm.ui.components.SeiLogo
import io.seidesk.rm.ui.components.SeiGlassCard
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiRow
import io.seidesk.rm.ui.components.SeiSectionLabel
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiTopBar
import io.seidesk.rm.ui.components.SeiWordmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seksi di drawer samping. Judul dua bahasa dibaca di [sectionTitle]. */
internal enum class SeiSection(val title: String, val titleEn: String) {
    PERANGKAT("Devices", "Devices"),
    TAMPILAN("Display", "Display"),
    KREDENSIAL("Credentials", "Credentials"),
    UMUM("General", "General"),
    KEAMANAN("Security", "Security"),
    TENTANG("About", "About"),
}

/** Judul seksi yang ikut bahasa aktif. */
@Composable
internal fun sectionTitle(section: SeiSection): String = sei( section.titleEn)

/** Layar yang sedang tampil. */
private sealed interface SeiRoute {
    data object Devices : SeiRoute
    data object CreateRdp : SeiRoute
    data class EditDevice(val profile: ConnectionProfile?, val pcQuickMode: Boolean = false) : SeiRoute
    data object Section : SeiRoute

    /** Feed komunitas — layar sendiri, dipetakan ke tab nav [SeiTab.FEED]. */
    data object Feed : SeiRoute

    /** Dashboard pengaturan — dipetakan ke tab nav [SeiTab.PROFILE]. */
    data object Profile : SeiRoute
}

/**
 * Home SeiDesk RM: device list + settings drawer.
 *
 * Semua info yang ditampilkan sengaja tipis: nama perangkat, alamat, akun.
 * Preview pakai wallpaper motif OS perangkat (digambar prosedural, tanpa aset).
 */
@Composable
fun SeiDeskHome(
    onExit: () -> Unit,
    onReady: () -> Unit = {},
    sharedFileNotice: String? = null,
    autoResumeSignal: Int = 0,
    skipAutoResume: Boolean = false,
    onDismissSharedNotice: () -> Unit = {},
    onAuthenticateConnect: (() -> Unit) -> Unit = { it() },
) {
    val context = LocalContext.current
    val repo = remember { SessionsRepository(context.applicationContext) }
    val favoritesFlow = remember { repo.favorites() }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    // Data siap = daftar perangkat sudah keluar sekali dari database; dipakai
    // splash supaya dia berhenti tepat waktu, bukan menebak-nebak.
    var dataReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        favoritesFlow.first()
        dataReady = true
    }
    LaunchedEffect(dataReady) { if (dataReady) onReady() }
    val scope = rememberCoroutineScope()
    val appPrefs = remember { AppPrefs(context) }
    var section by remember { mutableStateOf(SeiSection.PERANGKAT) }
    // Tab nav bawah (3 tujuan). `section` tetap ada karena seksi dibuka dari
    // layar Profil, dan menggambar SectionScreen.
    var tab by remember { mutableStateOf(SeiTab.HOME) }
    var route by remember { mutableStateOf<SeiRoute>(SeiRoute.Devices) }
    var crashLog by remember { mutableStateOf(CrashLog.last(context.applicationContext)) }
    var showCrash by remember { mutableStateOf(false) }
    val bootTail = remember { ConnectionLog.tailFromFile(context.applicationContext, 20) }
    var showBoot by remember { mutableStateOf(false) }
    var feedbackOpen by remember { mutableStateOf(false) }
    var backupModalOpen by remember { mutableStateOf(false) }
    var backupJsonInput by remember { mutableStateOf("") }
    var confirmRestoreProfiles by remember { mutableStateOf<List<Pair<ConnectionProfile, Boolean>>?>(null) }
    var updateModalOpen by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf<String?>(null) }
    var confirmDeleteDevice by remember { mutableStateOf<ConnectionProfile?>(null) }
    var confirmExitApp by remember { mutableStateOf(false) }
    var wolWaitingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    // Pesan app sendiri (bukan Toast bawaan Android).
    val notice = rememberSeiNotice()

    fun connect(profile: ConnectionProfile) {
        onAuthenticateConnect {
            scope.launch { repo.touch(profile) }
            context.startActivity(SeiDeskSessionActivity.connectIntent(context, profile))
        }
    }

    LaunchedEffect(autoResumeSignal, skipAutoResume, dataReady) {
        if (autoResumeSignal <= 0) return@LaunchedEffect
        if (skipAutoResume) {
            // Home navigation and Share are deliberate; consume the marker so a
            // later lifecycle callback cannot unexpectedly reopen the session.
            appPrefs.clearAutoResumeSession()
            return@LaunchedEffect
        }
        if (!dataReady) return@LaunchedEffect
        if (!appPrefs.autoResumeLastSession) {
            appPrefs.clearAutoResumeSession()
            return@LaunchedEffect
        }

        val profileId = appPrefs.autoResumeProfileId
        val savedAt = appPrefs.autoResumeSavedAtMillis
        val now = System.currentTimeMillis()
        if (profileId.isNullOrBlank() || savedAt <= 0L || now < savedAt ||
            now - savedAt > AppPrefs.AUTO_RESUME_WINDOW_MS
        ) {
            appPrefs.clearAutoResumeSession(profileId)
            return@LaunchedEffect
        }

        val savedProfiles = favoritesFlow.first()
        val profile = savedProfiles.firstOrNull { it.id == profileId }
        if (profile == null) {
            appPrefs.clearAutoResumeSession(profileId)
            return@LaunchedEffect
        }

        // Consume before launching so a failed auth or Activity recreation cannot
        // create an auto-launch loop. Credentials are loaded only from the vault.
        appPrefs.clearAutoResumeSession(profileId)
        val existing = SeiSessionRegistry.list().firstOrNull { it.id == profileId }
        if (existing != null) existing.open() else connect(profile)
    }

    fun wakeAndConnect(profile: ConnectionProfile) {
        val opts = RdpOptions.of(context, profile.id)
        val mac = opts.macAddress?.trim().orEmpty()
        if (WakeOnLan.parseMacBytes(mac) == null) {
            notice.show(seiNow( "Wake-on-LAN MAC address is missing or invalid"))
            return
        }
        wolWaitingProfile = profile
        scope.launch {
            val targets = listOf(opts.wolBroadcast.ifBlank { "255.255.255.255" }, profile.host).distinct()
            val sendRes = withContext(Dispatchers.IO) {
                runCatching {
                    targets.forEach { target ->
                        WakeOnLan.sendMagicPacket(mac, target, opts.wolPort)
                    }
                }
            }
            if (sendRes.isFailure) {
                wolWaitingProfile = null
                notice.show(
                    seiNow(
                        "Failed to send Wake-on-LAN packet: ${sendRes.exceptionOrNull()?.message ?: "error"}",
                    ),
                )
                return@launch
            }
            notice.show(
                seiNow(
                    "Magic Packet sent to ${profile.label ?: profile.host}. Waiting for RDP port...",
                ),
            )
            val ready = withContext(Dispatchers.IO) {
                WakeOnLan.pollHostReady(
                    host = profile.host,
                    port = profile.port,
                    timeoutMs = 35_000L,
                    intervalMs = 1_200L,
                )
            }
            if (wolWaitingProfile?.id == profile.id) {
                wolWaitingProfile = null
                if (ready) {
                    notice.show(seiNow( "PC is awake! Launching RDP session..."))
                    connect(profile)
                } else {
                    notice.show(
                        seiNow(
                            "WoL packet sent, but port ${profile.port} did not respond within 35 seconds.",
                        ),
                    )
                }
            }
        }
    }

    fun submit(
        profile: ConnectionProfile,
        rememberPassword: Boolean,
        startNow: Boolean,
        onComplete: (Result<Boolean>) -> Unit,
    ) {
        scope.launch {
            val saved = runCatching { repo.save(profile, rememberPassword) }
            if (saved.isFailure) {
                val err = saved.exceptionOrNull()?.message ?: seiNow("storage error")
                notice.show(seiNow("Profile not saved: ") + err)
                onComplete(Result.failure(saved.exceptionOrNull() ?: Exception(err)))
                return@launch
            }
            val passSaved = saved.getOrDefault(true)
            if (!passSaved) {
                notice.show(
                    seiNow(
                        "Profile saved, but the password could not be stored securely. Re-enter it later; it was not remembered.",
                    ),
                )
            }
            onComplete(Result.success(passSaved))
            route = SeiRoute.Devices
            if (startNow) connect(profile)
        }
    }

    /**
     * Kirim masukan ke tujuan yang dipilih pengguna.
     *
     * Tidak ada tujuan yang di-hardcode ke alamat tertentu: repo ini tidak
     * menyimpan alamat dukungan, jadi pilihannya adalah pemilih aplikasi
     * Android (bebas ke aplikasi mana pun) atau salin draf ke clipboard
     * supaya pengguna menempelkannya sendiri.
     */
    fun sendFeedback(destination: FeedbackDestination, category: String, message: String) {
        feedbackOpen = false
        val draft = feedbackShareDraft(category, message)
        when (destination) {
            FeedbackDestination.COPY -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard == null) {
                    notice.show(seiNow( "Clipboard is unavailable"))
                    return
                }
                runCatching {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("SeiDesk RM feedback", draft),
                    )
                }.onSuccess {
                    notice.show(
                        seiNow(
                            "Draft copied. Paste it into any app.",
                        ),
                    )
                }.onFailure {
                    notice.show(seiNow( "Could not copy the draft"))
                }
            }

            FeedbackDestination.SHARE -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "SeiDesk RM feedback")
                    putExtra(Intent.EXTRA_TEXT, draft)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            sendIntent,
                            seiNow( "Choose an app to share"),
                        ),
                    )
                }.onFailure {
                    notice.show(seiNow( "Share menu is unavailable"))
                }
            }
        }
    }

    BackHandler {
        when {
            confirmRestoreProfiles != null -> confirmRestoreProfiles = null
            backupModalOpen -> backupModalOpen = false
            updateModalOpen -> updateModalOpen = false
            confirmDeleteDevice != null -> confirmDeleteDevice = null
            confirmExitApp -> confirmExitApp = false
            feedbackOpen -> feedbackOpen = false
            route != SeiRoute.Devices || section != SeiSection.PERANGKAT -> {
                if (section != SeiSection.PERANGKAT) {
                    section = SeiSection.PERANGKAT
                    tab = SeiTab.PROFILE
                    route = SeiRoute.Profile
                } else {
                    route = SeiRoute.Devices
                    section = SeiSection.PERANGKAT
                    tab = SeiTab.HOME
                }
            }
            else -> confirmExitApp = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Kolom: konten mendapat sisa tinggi, nav bawah punya tinggi tetap
            // sehingga tidak menutupi isi layar.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().weight(1f)) {

            when (val current = route) {
                SeiRoute.CreateRdp -> RdpFreeScreen(
                    onBack = { route = SeiRoute.Devices },
                    onConnect = { connect(it) },
                )

                is SeiRoute.EditDevice -> AddDeviceScreen(
                    existing = current.profile,
                    savedUsers = favorites.mapNotNull { it.username }.distinct(),
                    savedProfiles = favorites,
                    initialPcQuickMode = current.pcQuickMode,
                    onCancel = { route = SeiRoute.Devices },
                    onSubmit = { profile, remember, startNow, onComplete ->
                        submit(profile, remember, startNow, onComplete)
                    },
                )

                SeiRoute.Devices -> if (section == SeiSection.PERANGKAT) {
                    DevicesScreen(
                        favorites = favorites,
                        crashLog = crashLog,
                        bootTail = bootTail,
                        sharedFileNotice = sharedFileNotice,
                        onDismissSharedNotice = onDismissSharedNotice,
                        onMenu = {
                            tab = SeiTab.PROFILE
                            route = SeiRoute.Profile
                        },
                        onAddRdp = { route = SeiRoute.EditDevice(null, pcQuickMode = false) },
                        onAddPcQuick = { route = SeiRoute.EditDevice(null, pcQuickMode = true) },
                        onCreateRdp = { route = SeiRoute.CreateRdp },
                        onEdit = {
                            route = SeiRoute.EditDevice(
                                it,
                                pcQuickMode = RdpOptions.of(context, it.id).pcConnectMode,
                            )
                        },
                        onConnect = { connect(it) },
                        onWakeConnect = { wakeAndConnect(it) },
                        onDuplicate = { prof ->
                            scope.launch {
                                val newProfile = prof.copy(
                                    key = java.util.UUID.randomUUID().toString(),
                                    label = "${prof.label ?: prof.host} (Copy)",
                                )
                                repo.save(newProfile, rememberPassword = true)
                                val opts = RdpOptions.of(context, prof.id)
                                opts.write(context, newProfile.id)
                                notice.show(seiNow("Duplicated device: {0}", newProfile.label ?: newProfile.host))
                            }
                        },
                        onDelete = { profile -> confirmDeleteDevice = profile },
                        onShowCrash = { showCrash = true },
                        onShowBoot = { showBoot = true },
                        appPrefs = appPrefs,
                    )
                } else {
                    SectionScreen(
                        section = section,
                        favorites = favorites,
                        onMenu = {
                            tab = SeiTab.PROFILE
                            route = SeiRoute.Profile
                        },
                        appPrefs = appPrefs,
                        onEditDevice = {
                            route = SeiRoute.EditDevice(
                                it,
                                pcQuickMode = RdpOptions.of(context, it.id).pcConnectMode,
                            )
                        },
                        onClearAllCredentials = {
                            scope.launch {
                                repo.clear()
                                notice.show(
                                    seiNow(
                                        "Credentials and devices cleared",
                                    ),
                                )
                            }
                        },
                        onShowLog = { showBoot = true },
                    )
                }

                SeiRoute.Feed -> SeiFeedScreen(
                    onMenu = {
                        tab = SeiTab.PROFILE
                        route = SeiRoute.Profile
                    },
                )

                SeiRoute.Profile -> SeiProfileScreen(
                    onOpenSection = {
                        section = it
                        route = SeiRoute.Devices
                    },
                    appPrefs = appPrefs,
                    deviceCount = favorites.size,
                    onShowLog = { showBoot = true },
                    onBackupProfiles = {
                        backupJsonInput = exportProfilesJson(context, favorites)
                        backupModalOpen = true
                    },
                    onCheckUpdates = {
                        updateModalOpen = true
                        updateStatusText = seiNow("Checking latest release status from www.xydeskremote.biz.id...")
                        scope.launch {
                            val status = withContext(Dispatchers.IO) { fetchRemoteReleaseStatus(context) }
                            updateStatusText = status
                        }
                    },
                    onOpenFeedback = { feedbackOpen = true },
                )

                SeiRoute.Section -> Unit
            }
                }

                // Nav bawah disembunyikan saat form tambah/ubah perangkat terbuka
                // supaya layar itu tetap penuh dan tombol simpan tidak tertutup.
                if (route !is SeiRoute.EditDevice) {
                    SeiBottomNavHairline()
                    SeiBottomNav(
                        selected = tab,
                        onSelect = { next ->
                            tab = next
                            when (next) {
                                SeiTab.HOME -> {
                                    section = SeiSection.PERANGKAT
                                    route = SeiRoute.Devices
                                }
                                SeiTab.FEED -> route = SeiRoute.Feed
                                SeiTab.PROFILE -> route = SeiRoute.Profile
                            }
                        },
                    )
                }
            }
        }

    if (showCrash) {
        InfoDialog(
            title = sei( "Last error log"),
            body = crashLog.orEmpty(),
            onDismiss = {
                showCrash = false
                CrashLog.clear(context.applicationContext)
                crashLog = null
            },
        )
    }
    if (showBoot) {
        // Dua bagian: log app + log native FreeRDP (kanal audio/mikrofon,
        // clipboard, drive, DISP). Yang native muncul kalau appender file
        // aktif — dipasang XyApp sebelum library dimuat.
        val nativeTail = remember(showBoot) { ConnectionLog.nativeTail(160) }
        val body = buildString {
            if (nativeTail.isNotEmpty()) {
                appendLine("=== LOG NATIVE (FreeRDP) ===")
                appendLine(nativeTail.joinToString("\n"))
                appendLine()
            }
            appendLine("=== LOG APP (xydesk-boot.log) ===")
            append(bootTail.joinToString("\n"))
        }
        InfoDialog(
            title = sei( "Last session log"),
            body = body,
            onDismiss = { showBoot = false },
        )
    }
    if (feedbackOpen) {
        FeedbackDialog(
            onDismiss = { feedbackOpen = false },
            onSend = ::sendFeedback,
        )
    }
    confirmDeleteDevice?.let { target ->
        SeiDialog(
            title = sei( "Delete device?"),
            body = sei(
                "Delete \"{0}\" along with its saved password and settings?",
                target.label ?: "${target.host}:${target.port}",
            ),
            confirmLabel = sei( "Delete"),
            onConfirm = {
                confirmDeleteDevice = null
                scope.launch {
                    repo.remove(target.id)
                    io.seidesk.rm.core.RdpOptions.clear(context, target.id)
                    DisplayPrefs.clear(context, target.id)
                    notice.show(seiNow( "Device deleted"))
                }
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmDeleteDevice = null },
        )
    }
    wolWaitingProfile?.let { target ->
        SeiDialog(
            title = sei("Waking PC (Wake-on-LAN)"),
            body = sei(
                "Magic Packet sent to \"{0}\" ({1}:{2}). SeiDesk is automatically polling the RDP port for up to 35 seconds...",
                target.label ?: target.host,
                target.host,
                target.port,
            ),
            confirmLabel = sei("Connect Now"),
            onConfirm = {
                wolWaitingProfile = null
                connect(target)
            },
            dismissLabel = sei( "Cancel Wait"),
            onDismiss = { wolWaitingProfile = null },
        )
    }

    if (confirmExitApp) {
        SeiDialog(
            title = sei( "Exit SeiDesk RM?"),
            body = sei(
                "Close the application now? Active background sessions will stay running while keep-alive is enabled.",
            ),
            confirmLabel = sei( "Exit"),
            onConfirm = {
                confirmExitApp = false
                onExit()
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmExitApp = false },
        )
    }

    if (backupModalOpen) {
        SeiOverlay(
            title = sei( "Backup & Restore Profiles (JSON)"),
            onDismiss = { backupModalOpen = false },
        ) {
            Text(
                sei(
                    "Copy the JSON below to back up all your PC Connect & RDP profiles, or paste a backup JSON and tap Restore.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeiField(
                value = backupJsonInput,
                onValueChange = { backupJsonInput = it },
                label = sei( "Device Profiles JSON Data"),
                hint = "{\"version\":1,\"profiles\":[...]}",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiPillButton(
                    text = sei( "Copy Backup"),
                    onClick = {
                        val json = exportProfilesJson(context, favorites)
                        backupJsonInput = json
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("seidesk-profiles-backup", json))
                        notice.show(seiNow( "Backup JSON copied to clipboard ({0} devices)", favorites.size))
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SeiPillButton(
                    text = sei( "Restore JSON"),
                    onClick = {
                        val parsed = importProfilesJson(backupJsonInput)
                        if (parsed.isEmpty()) {
                            notice.show(seiNow( "Invalid or empty JSON format"))
                        } else {
                            confirmRestoreProfiles = parsed
                        }
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SeiPillButton(
                text = sei( "Close"),
                onClick = { backupModalOpen = false },
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    confirmRestoreProfiles?.let { listToRestore ->
        SeiDialog(
            title = sei( "Restore {0} device profiles?", listToRestore.size),
            body = sei(
                "Add / update {0} device profiles from the JSON backup now?",
                listToRestore.size,
            ),
            confirmLabel = sei( "Restore"),
            onConfirm = {
                val items = listToRestore
                confirmRestoreProfiles = null
                backupModalOpen = false
                scope.launch {
                    items.forEach { (prof, isPcMode) ->
                        runCatching {
                            repo.save(prof, rememberPassword = false)
                            val opts = RdpOptions.of(context, prof.id)
                            if (isPcMode) {
                                opts.withPcStreamEngine(opts.pcStreamEngine).write(context, prof.id)
                            } else {
                                opts.copy(pcConnectMode = false).write(context, prof.id)
                            }
                        }
                    }
                    notice.show(seiNow( "{0} device profiles restored", items.size))
                }
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmRestoreProfiles = null },
        )
    }

    if (updateModalOpen) {
        SeiOverlay(
            title = sei( "Updates & RilisIn Store"),
            onDismiss = { updateModalOpen = false },
        ) {
            Text(
                text = "SeiDesk RM v${appVersion(context)}  ·  ${AppBrand.PUBLISHER}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = updateStatusText ?: sei( "Checking for updates..."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiPillButton(
                    text = sei( "www.xydeskremote.biz.id Portal"),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.xydeskremote.biz.id")))
                        }
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                SeiPillButton(
                    text = sei( "Open RilisIn Store"),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rilisin.xyverse.my.id")))
                        }
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SeiPillButton(
                text = sei( "Close"),
                onClick = { updateModalOpen = false },
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Pesan app sendiri — paling atas supaya tidak ketutup drawer/panel.
    SeiNoticeHost(state = notice, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Resume hero card:
 * Displays active session status with fps/latency telemetry when a session is live,
 * or a Quick Connect shortcut to the most recent device when idle.
 */
@Composable
private fun ResumeHeroCard(
    favorites: List<ConnectionProfile>,
    onConnect: (ConnectionProfile) -> Unit,
) {
    var live by remember { mutableStateOf(SeiSessionRegistry.list()) }
    var confirmKill by remember { mutableStateOf<SeiSessionRegistry.Live?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            live = SeiSessionRegistry.list()
            kotlinx.coroutines.delay(1_500)
        }
    }

    if (live.isNotEmpty()) {
        val active = live.first()
        val cardShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), cardShape)
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                        )
                        Text(
                            sei("LIVE SESSION"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                            ),
                            color = Color(0xFF10B981),
                        )
                    }

                    // Active session badge
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            sei("CONNECTED"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = io.seidesk.rm.ui.theme.SeiMono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column {
                    Text(
                        text = active.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = active.address,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = io.seidesk.rm.ui.theme.SeiMono,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeiPillButton(
                        text = sei("Resume"),
                        onClick = { active.open() },
                        icon = SeiIcons.Play,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        text = sei("Disconnect"),
                        onClick = { confirmKill = active },
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        confirmKill?.let { target ->
            SeiDialog(
                title = sei("Disconnect active session?"),
                body = sei("Disconnect from \"{0}\" ({1}) now?", target.label, target.address),
                confirmLabel = sei("Disconnect"),
                onConfirm = {
                    confirmKill = null
                    target.kill()
                },
                dismissLabel = sei("Cancel"),
                onDismiss = { confirmKill = null },
            )
        }
    } else if (favorites.isNotEmpty()) {
        val mostRecent = favorites.first()
        val cardShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), cardShape)
                .clickable { onConnect(mostRecent) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                SeiIcons.Zap,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = sei("Quick connect"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = mostRecent.label ?: mostRecent.host,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SeiPillButton(
                text = sei("Connect"),
                onClick = { onConnect(mostRecent) },
                compact = true,
            )
        }
    }
}

/**
 * Filter chips row replacing the overflow-prone 3-way segmented control.
 */
@Composable
private fun FilterChipsRow(
    allCount: Int,
    pcCount: Int,
    rdpCount: Int,
    onlineCount: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val items = listOf(
        sei("All ({0})", allCount) to 0,
        sei("PC ({0})", pcCount) to 1,
        sei("RDP ({0})", rdpCount) to 2,
        sei("Online ({0})", onlineCount) to 3,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, index) ->
            val selected = selectedIndex == index
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 11.5.sp,
                    ),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptySearchResult(
    query: String,
    filterIndex: Int,
    onResetFilters: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SeiIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = if (query.isNotBlank()) sei("No devices match \"{0}\"", query) else sei("No devices match this filter"),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = sei("Check your search terms or reset active filters to view saved devices."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SeiPillButton(
            text = sei("Reset filters"),
            onClick = onResetFilters,
            primary = false,
            compact = true,
        )
    }
}

@Composable
private fun DevicesScreen(
    favorites: List<ConnectionProfile>,
    crashLog: String?,
    bootTail: List<String>,
    sharedFileNotice: String?,
    onDismissSharedNotice: () -> Unit,
    onMenu: () -> Unit,
    onAddRdp: () -> Unit,
    onAddPcQuick: () -> Unit,
    onCreateRdp: () -> Unit,
    onEdit: (ConnectionProfile) -> Unit,
    onConnect: (ConnectionProfile) -> Unit,
    onWakeConnect: (ConnectionProfile) -> Unit,
    onDuplicate: (ConnectionProfile) -> Unit,
    onDelete: (ConnectionProfile) -> Unit,
    onShowCrash: () -> Unit,
    onShowBoot: () -> Unit,
    appPrefs: AppPrefs,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var modeFilterIndex by remember { mutableIntStateOf(0) }
    var gridMode by remember { mutableStateOf(appPrefs.deviceListGridMode) }
    var showNewDeviceSheet by remember { mutableStateOf(false) }
    var showLanScanSheet by remember { mutableStateOf(false) }
    var lanScanning by remember { mutableStateOf(false) }
    var lanFoundHosts by remember { mutableStateOf<List<DiscoveredPcHost>>(emptyList()) }

    // Probes reachability concurrently off the main thread with periodic refresh
    val reachabilityMap = remember { mutableStateMapOf<String, ReachabilityResult>() }

    // Remove stale reachability entries when devices are deleted
    LaunchedEffect(favorites) {
        val validIds = favorites.map { it.id }.toSet()
        val staleKeys = reachabilityMap.keys.filter { it !in validIds }
        staleKeys.forEach { reachabilityMap.remove(it) }
    }

    // Refresh reachability periodically and probe devices concurrently off the main thread
    LaunchedEffect(favorites) {
        if (favorites.isEmpty()) return@LaunchedEffect
        while (true) {
            favorites.forEach { prof ->
                if (prof.id !in reachabilityMap) {
                    reachabilityMap[prof.id] = ReachabilityResult.checking()
                }
            }
            withContext(Dispatchers.IO) {
                coroutineScope {
                    favorites.map { prof ->
                        async {
                            val res = ReachabilityProbe.check(prof.host, prof.port)
                            prof.id to res
                        }
                    }.awaitAll().forEach { (id, res) ->
                        reachabilityMap[id] = res
                    }
                }
            }
            delay(30_000L)
        }
    }

    // Dynamic counts based on valid saved devices
    val pcCount = remember(favorites) {
        favorites.count { RdpOptions.of(context, it.id).pcConnectMode }
    }
    val rdpCount = remember(favorites) {
        favorites.count { !RdpOptions.of(context, it.id).pcConnectMode }
    }
    val onlineCount = remember(favorites, reachabilityMap.toMap()) {
        val validIds = favorites.map { it.id }.toSet()
        reachabilityMap.filter { it.key in validIds }.values.count { it.status == ReachabilityStatus.REACHABLE }
    }

    val filteredFavorites = remember(favorites, searchQuery, modeFilterIndex, reachabilityMap.toMap()) {
        val q = searchQuery.trim().lowercase()
        favorites.filter { p ->
            val isPc = RdpOptions.of(context, p.id).pcConnectMode
            val reach = reachabilityMap[p.id]
            val isOnline = reach?.status == ReachabilityStatus.REACHABLE
            val modeOk = when (modeFilterIndex) {
                1 -> isPc
                2 -> !isPc
                3 -> isOnline
                else -> true
            }
            val queryOk = q.isEmpty() ||
                p.label.orEmpty().lowercase().contains(q) ||
                p.host.lowercase().contains(q) ||
                p.username.orEmpty().lowercase().contains(q)
            modeOk && queryOk
        }
    }

    // Dismiss in-place search first before asking for application exit
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    fun triggerLanScan() {
        showLanScanSheet = true
        if (lanScanning) return
        lanScanning = true
        scope.launch {
            val hosts = withContext(Dispatchers.IO) { LanScanner.scanLocalSubnet() }
            lanFoundHosts = hosts
            lanScanning = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Single status banner priority queue: crash log -> interrupted session -> shared file -> PC notice
            val bootSuspect = bootTail.isNotEmpty() && bootTail.last().let { last ->
                !last.contains("manager.connect") &&
                    !last.contains("session.connect") &&
                    !last.contains("-> Connected") &&
                    !last.contains("-> Disconnected") &&
                    !last.contains("intent without valid profile")
            }

            when {
                crashLog != null -> {
                    Banner(
                        text = sei("An error happened before — tap to inspect crash log"),
                        onClick = onShowCrash,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                bootSuspect -> {
                    Banner(
                        text = sei("The last session stopped midway — tap to view logs"),
                        onClick = onShowBoot,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sharedFileNotice != null -> {
                    Banner(
                        text = sharedFileNotice,
                        onClick = onDismissSharedNotice,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                !appPrefs.dismissedPcConnectionNotice -> {
                    Banner(
                        text = sei("Direct PC connection is still in development and separate from RDP. RDP remains available."),
                        onClick = { appPrefs.dismissedPcConnectionNotice = true },
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // Top Bar: in-place search or title + icons
            if (searchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeiField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = sei("Search"),
                        hint = sei("PC name, IP, or user..."),
                        modifier = Modifier.weight(1f),
                    )
                    SeiIconPill(
                        icon = SeiIcons.Close,
                        onClick = {
                            searchQuery = ""
                            searchActive = false
                        },
                        flat = true,
                        contentDescription = sei("Close search"),
                    )
                }
            } else {
                SeiTopBar(
                    title = sei("Devices"),
                    onBack = null,
                    actions = {
                        SeiIconPill(
                            icon = SeiIcons.Search,
                            onClick = { searchActive = true },
                            flat = true,
                            contentDescription = sei("Search"),
                        )
                        SeiIconPill(
                            icon = if (gridMode) SeiIcons.List else SeiIcons.Grid,
                            onClick = {
                                gridMode = !gridMode
                                appPrefs.deviceListGridMode = gridMode
                            },
                            flat = true,
                            contentDescription = if (gridMode) sei("Switch to list view") else sei("Switch to grid view"),
                        )
                        SeiIconPill(
                            icon = SeiIcons.Menu,
                            onClick = onMenu,
                            flat = true,
                            contentDescription = sei("Menu"),
                        )
                    },
                )
            }

            if (favorites.isEmpty()) {
                // Modern empty state: SeiLogo, single headline, two option cards, scan button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SeiLogo(modifier = Modifier.size(56.dp))
                        Text(
                            text = sei("Connect to a PC or RDP workstation"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // PC Option Card
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                    .clickable { onAddPcQuick() }
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    SeiIcons.Zap,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    sei("PC Connect"),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    sei("ID & Password with GPU acceleration"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // RDP Option Card
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                    .clickable { onAddRdp() }
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    SeiIcons.Monitor,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    sei("Standard RDP"),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    sei("Direct IP, domain, or Tailscale"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        SeiPillButton(
                            text = sei("Scan local network"),
                            onClick = { triggerLanScan() },
                            icon = SeiIcons.Search,
                            primary = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                val useGrid = gridMode

                if (useGrid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(320.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            ResumeHeroCard(favorites = favorites, onConnect = onConnect)
                        }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            FilterChipsRow(
                                allCount = favorites.size,
                                pcCount = pcCount,
                                rdpCount = rdpCount,
                                onlineCount = onlineCount,
                                selectedIndex = modeFilterIndex,
                                onSelect = { modeFilterIndex = it },
                            )
                        }
                        if (filteredFavorites.isEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                EmptySearchResult(
                                    query = searchQuery,
                                    filterIndex = modeFilterIndex,
                                    onResetFilters = {
                                        searchQuery = ""
                                        modeFilterIndex = 0
                                    },
                                )
                            }
                        } else {
                            gridItems(filteredFavorites, key = { it.id }) { profile ->
                                val reach = reachabilityMap[profile.id] ?: ReachabilityProbe.getCached(profile.host, profile.port)
                                DeviceCardRich(
                                    profile = profile,
                                    reachability = reach,
                                    onConnect = { onConnect(profile) },
                                    onWakeConnect = { onWakeConnect(profile) },
                                    onEdit = { onEdit(profile) },
                                    onDuplicate = { onDuplicate(profile) },
                                    onDelete = { onDelete(profile) },
                                )
                            }
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                // Slim one-line RdpFree promo row
                                RdpFreePromoRow(onCreateRdp = onCreateRdp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { ResumeHeroCard(favorites = favorites, onConnect = onConnect) }
                        item {
                            FilterChipsRow(
                                allCount = favorites.size,
                                pcCount = pcCount,
                                rdpCount = rdpCount,
                                onlineCount = onlineCount,
                                selectedIndex = modeFilterIndex,
                                onSelect = { modeFilterIndex = it },
                            )
                        }
                        if (filteredFavorites.isEmpty()) {
                            item {
                                EmptySearchResult(
                                    query = searchQuery,
                                    filterIndex = modeFilterIndex,
                                    onResetFilters = {
                                        searchQuery = ""
                                        modeFilterIndex = 0
                                    },
                                )
                            }
                        } else {
                            items(filteredFavorites, key = { it.id }) { profile ->
                                val reach = reachabilityMap[profile.id] ?: ReachabilityProbe.getCached(profile.host, profile.port)
                                DeviceCardCompact(
                                    profile = profile,
                                    reachability = reach,
                                    onConnect = { onConnect(profile) },
                                    onWakeConnect = { onWakeConnect(profile) },
                                    onEdit = { onEdit(profile) },
                                    onDuplicate = { onDuplicate(profile) },
                                    onDelete = { onDelete(profile) },
                                )
                            }
                            item {
                                // Slim one-line RdpFree promo row
                                RdpFreePromoRow(onCreateRdp = onCreateRdp)
                            }
                        }
                    }
                }
            }
        }

        // Extended FAB 'New device' in the thumb zone (bottom end)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .pressScale(0.97f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { showNewDeviceSheet = true }
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    SeiIcons.Plus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    sei("New device"),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        // New Device modal sheet
        if (showNewDeviceSheet) {
            SeiOverlay(
                title = sei("New Device Connection"),
                onDismiss = { showNewDeviceSheet = false },
            ) {
                SeiRow(
                    title = sei("PC Connection (ID & Password)"),
                    subtitle = sei("Quick connection preset with low-latency desktop display settings"),
                    trailing = {
                        Text(
                            sei("Fast"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        showNewDeviceSheet = false
                        onAddPcQuick()
                    },
                )
                SeiRow(
                    title = sei("Standard RDP (Host / IP)"),
                    subtitle = sei("Direct RDP session via local IP, domain name, or Tailscale"),
                    onClick = {
                        showNewDeviceSheet = false
                        onAddRdp()
                    },
                )
                SeiRow(
                    title = sei("Free 6-Hour RDP (GitHub Actions)"),
                    subtitle = sei("Spin up a Windows PC from the template on your own GitHub account"),
                    trailing = {
                        Text(
                            sei("Cloud"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    onClick = {
                        showNewDeviceSheet = false
                        onCreateRdp()
                    },
                )
                SeiRow(
                    title = sei("Scan this network"),
                    subtitle = sei("Discover active RDP workstations and PCs on your local subnet"),
                    trailing = {
                        Icon(
                            SeiIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = {
                        showNewDeviceSheet = false
                        triggerLanScan()
                    },
                )
                Spacer(Modifier.height(8.dp))
                SeiPillButton(
                    text = sei("Cancel"),
                    onClick = { showNewDeviceSheet = false },
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // LAN Scan Results Sheet
        if (showLanScanSheet) {
            SeiOverlay(
                title = sei("Network PC Discovery"),
                onDismiss = { showLanScanSheet = false },
            ) {
                if (lanScanning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            sei("Scanning local subnet for RDP hosts..."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (lanFoundHosts.isEmpty()) {
                    Text(
                        sei("No open RDP hosts detected on this subnet. Make sure the remote PC is on and Remote Desktop is enabled."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        sei("Discovered {0} host(s):", lanFoundHosts.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    lanFoundHosts.forEach { host ->
                        SeiRow(
                            title = "${host.ip}:${host.port}",
                            subtitle = "RTT: ${host.rttMs} ms  ·  ID: ${host.pcId}",
                            onClick = {
                                showLanScanSheet = false
                                onConnect(ConnectionProfile(host = host.ip, port = host.port))
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiPillButton(
                        text = sei("Scan Again"),
                        onClick = { triggerLanScan() },
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        text = sei("Close"),
                        onClick = { showLanScanSheet = false },
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReachabilityIndicator(
    reachability: ReachabilityResult?,
    modifier: Modifier = Modifier,
) {
    val status = reachability?.status ?: ReachabilityStatus.UNKNOWN
    val (dotColor, labelText) = when (status) {
        ReachabilityStatus.REACHABLE -> {
            val txt = if (reachability?.latencyMs != null && reachability.latencyMs > 0) {
                "${reachability.latencyMs} ms"
            } else {
                sei("Reachable")
            }
            Color(0xFF10B981) to txt
        }
        ReachabilityStatus.CHECKING -> Color(0xFF38BDF8) to sei("Checking...")
        ReachabilityStatus.UNREACHABLE -> Color(0xFF64748B) to sei("Unreachable")
        ReachabilityStatus.FAILED -> MaterialTheme.colorScheme.error to sei("Check failed")
        ReachabilityStatus.UNKNOWN -> Color(0xFF94A3B8) to sei("Unknown")
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = if (status == ReachabilityStatus.REACHABLE && reachability?.latencyMs != null) io.seidesk.rm.ui.theme.SeiMono else FontFamily.Default,
                fontSize = 11.sp,
            ),
            color = if (status == ReachabilityStatus.REACHABLE) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Compact Device Card (~112 dp tall) for dense phone lists:
 * 96x64 preview thumb with OS badge, status dot, endpoint in mono, up to 3 chips, and trailing menu.
 * Whole card connects on click.
 */
@Composable
private fun DeviceCardCompact(
    profile: ConnectionProfile,
    reachability: ReachabilityResult?,
    onConnect: () -> Unit,
    onWakeConnect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val rdpOptions = remember(profile.id) { RdpOptions.of(context, profile.id) }
    val hasWol = !rdpOptions.macAddress.isNullOrBlank()
    val isOnline = reachability?.online == true
    val wall = remember(profile.id, profile.label, profile.host) {
        SeiWall.WIN11.forDevice(profile.label ?: profile.host)
    }
    val previewSeed = remember(profile.id, profile.label, profile.host) {
        (profile.label ?: profile.host) + "|" + profile.host
    }

    // Build chip labels
    val streamBadge = when (rdpOptions.streamProfile) {
        SeiStreamProfile.ULTRA_LOW_LATENCY -> sei("Low Latency")
        SeiStreamProfile.HIGH_VISUAL -> "AVC444"
        SeiStreamProfile.DATA_SAVER -> sei("Data Saver")
        SeiStreamProfile.CUSTOM -> sei("Custom")
        SeiStreamProfile.BALANCED, SeiStreamProfile.AUTO -> if (rdpOptions.udpTransport) "UDP+H264" else "H264"
    }
    val codecChip = if (rdpOptions.pcConnectMode) rdpOptions.activeCodecLabel() else streamBadge
    val allChips = buildList {
        add(codecChip)
        if (rdpOptions.consoleAdmin) add("Admin")
        if (hasWol) add("WoL")
        if (rdpOptions.audioMode != SeiAudioMode.OFF) add(sei("Audio"))
    }
    val visibleChips = allChips.take(3)
    val overflowCount = allChips.size - visibleChips.size

    val cardShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), cardShape)
            .clickable(onClick = onConnect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 96x64 preview thumb with OS badge and accent glow
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F1218)),
        ) {
            DevicePreviewArt(seed = previewSeed, os = wall, modifier = Modifier.fillMaxSize())
            DeviceAccentGlow(seed = previewSeed, modifier = Modifier.align(Alignment.BottomCenter))
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    if (wall == SeiWall.WIN11 || wall == SeiWall.WIN10) SeiIcons.Windows else SeiIcons.Monitor,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(10.dp),
                )
            }
        }

        // Info Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = profile.label ?: profile.host,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Reachability dot
                ReachabilityIndicator(reachability = reachability)
            }

            // Endpoint & User in mono
            Text(
                text = (if (rdpOptions.pcConnectMode) profile.host else "${profile.host}:${profile.port}") +
                    if (!profile.username.isNullOrBlank()) "  ·  ${profile.username}" else "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = io.seidesk.rm.ui.theme.SeiMono,
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visibleChips.forEach { chip ->
                    SpecChip(label = chip, highlight = chip == visibleChips.first())
                }
                if (overflowCount > 0) {
                    SpecChip(label = "+$overflowCount", highlight = false)
                }
            }
        }

        // Trailing ⋮ options menu
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            SeiIconPill(
                icon = SeiIcons.MoreVertical,
                onClick = { menuOpen = true },
                size = 36.dp,
                flat = true,
                contentDescription = sei("Options"),
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            ) {
                DropdownMenuItem(
                    text = { Text(sei("Edit")) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                    leadingIcon = { Icon(SeiIcons.Sliders, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                if (hasWol) {
                    DropdownMenuItem(
                        text = { Text(sei("Wake & connect")) },
                        onClick = {
                            menuOpen = false
                            onWakeConnect()
                        },
                        leadingIcon = { Icon(SeiIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(sei("Duplicate")) },
                    onClick = {
                        menuOpen = false
                        onDuplicate()
                    },
                    leadingIcon = { Icon(SeiIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                DropdownMenuItem(
                    text = { Text(sei("Delete"), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    leadingIcon = { Icon(SeiIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

/**
 * Rich Device Card for grid layouts or small collections:
 * 168 dp preview thumb with seeded accent glow (no fake taskbar), status badge, specs, and menu.
 */
@Composable
private fun DeviceCardRich(
    profile: ConnectionProfile,
    reachability: ReachabilityResult?,
    onConnect: () -> Unit,
    onWakeConnect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val rdpOptions = remember(profile.id) { RdpOptions.of(context, profile.id) }
    val hasWol = !rdpOptions.macAddress.isNullOrBlank()
    val isOnline = reachability?.online == true
    val wall = remember(profile.id, profile.label, profile.host) {
        SeiWall.WIN11.forDevice(profile.label ?: profile.host)
    }
    val previewSeed = remember(profile.id, profile.label, profile.host) {
        (profile.label ?: profile.host) + "|" + profile.host
    }

    val streamBadge = when (rdpOptions.streamProfile) {
        SeiStreamProfile.ULTRA_LOW_LATENCY -> sei("Low Latency")
        SeiStreamProfile.HIGH_VISUAL -> "AVC444"
        SeiStreamProfile.DATA_SAVER -> sei("Data Saver")
        SeiStreamProfile.CUSTOM -> sei("Custom")
        SeiStreamProfile.BALANCED, SeiStreamProfile.AUTO -> if (rdpOptions.udpTransport) "UDP+H264" else "H264"
    }

    val cardShape = RoundedCornerShape(20.dp)
    val cardBorder = Brush.verticalGradient(
        0f to MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        1f to MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, cardBorder, cardShape)
            .clickable(onClick = onConnect),
    ) {
        // Monitor Display Frame
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        ) {
            DevicePreviewArt(seed = previewSeed, os = wall)
            // Screen vignette
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.4f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.20f),
                            1f to Color.Black.copy(alpha = 0.60f),
                        ),
                    ),
            )

            // Top Badges (OS & Reachability)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // OS Platform Pill
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xB30B0F17))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        if (wall == SeiWall.WIN11 || wall == SeiWall.WIN10) SeiIcons.Windows else SeiIcons.Monitor,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        wall.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }

                // Reachability Pill
                val status = reachability?.status ?: ReachabilityStatus.UNKNOWN
                val (dotColor, labelText) = when (status) {
                    ReachabilityStatus.REACHABLE -> {
                        val txt = if (reachability?.latencyMs != null && reachability.latencyMs > 0) {
                            "${reachability.latencyMs} ms"
                        } else {
                            sei("Reachable")
                        }
                        Color(0xFF10B981) to txt
                    }
                    ReachabilityStatus.CHECKING -> Color(0xFF38BDF8) to sei("Checking...")
                    ReachabilityStatus.UNREACHABLE -> Color(0xFF94A3B8) to sei("Unreachable")
                    ReachabilityStatus.FAILED -> Color(0xFFF87171) to sei("Check failed")
                    ReachabilityStatus.UNKNOWN -> Color(0xFF94A3B8) to sei("Unknown")
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xB30B0F17))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        labelText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = if (status == ReachabilityStatus.REACHABLE && reachability?.latencyMs != null) io.seidesk.rm.ui.theme.SeiMono else FontFamily.Default,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (status == ReachabilityStatus.REACHABLE) Color(0xFF10B981) else Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            // Seeded accent glow replacing the fake taskbar
            DeviceAccentGlow(seed = previewSeed, modifier = Modifier.align(Alignment.BottomCenter))
        }

        // Body
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = profile.label ?: profile.host,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Trailing menu button
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    SeiIconPill(
                        icon = SeiIcons.MoreVertical,
                        onClick = { menuOpen = true },
                        size = 32.dp,
                        flat = true,
                        contentDescription = sei("Options"),
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    ) {
                        DropdownMenuItem(
                            text = { Text(sei("Edit")) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                            leadingIcon = { Icon(SeiIcons.Sliders, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        if (hasWol) {
                            DropdownMenuItem(
                                text = { Text(sei("Wake & connect")) },
                                onClick = {
                                    menuOpen = false
                                    onWakeConnect()
                                },
                                leadingIcon = { Icon(SeiIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(sei("Duplicate")) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                            leadingIcon = { Icon(SeiIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                        DropdownMenuItem(
                            text = { Text(sei("Delete"), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                            leadingIcon = { Icon(SeiIcons.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            // Endpoint in mono
            Text(
                text = (if (rdpOptions.pcConnectMode) profile.host else "${profile.host}:${profile.port}") +
                    if (!profile.username.isNullOrBlank()) "  ·  ${profile.username}" else "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = io.seidesk.rm.ui.theme.SeiMono,
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Spec chips
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpecChip(
                    label = if (rdpOptions.pcConnectMode) rdpOptions.activeCodecLabel() else streamBadge,
                    highlight = true,
                )
                if (rdpOptions.consoleAdmin) SpecChip(label = "Admin", highlight = false)
                if (hasWol) SpecChip(label = "WoL", highlight = false)
                if (rdpOptions.audioMode != SeiAudioMode.OFF) SpecChip(label = sei("Audio"), highlight = false)
            }
        }
    }
}

@Composable
private fun RdpFreePromoRow(onCreateRdp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onCreateRdp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            SeiIcons.Monitor,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            sei("Need a temporary Windows PC? Try RdpFree on GitHub"),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            SeiIcons.Play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun DeviceAccentGlow(seed: String, modifier: Modifier = Modifier) {
    val hash = Math.abs(seed.hashCode())
    val glowColor = remember(seed) {
        when (hash % 4) {
            0 -> Color(0xFF6366F1) // Indigo
            1 -> Color(0xFF38BDF8) // Sky
            2 -> Color(0xFF10B981) // Emerald
            else -> Color(0xFFA855F7) // Purple
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        glowColor.copy(alpha = 0.75f),
                        glowColor,
                        glowColor.copy(alpha = 0.75f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun SpecChip(
    label: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(6.dp)
    val bg = if (highlight) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val border = if (highlight) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val textCol = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .clip(chipShape)
            .background(bg)
            .border(1.dp, border, chipShape)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = textCol,
            maxLines = 1,
        )
    }
}

@Composable
private fun Banner(
    text: String,
    onClick: () -> Unit,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), color = content)
    }
}

private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"

private fun exportProfilesJson(
    context: Context,
    profiles: List<ConnectionProfile>,
): String {
    val arr = org.json.JSONArray()
    profiles.forEach { p ->
        val opts = RdpOptions.of(context, p.id)
        val obj = org.json.JSONObject()
            .put("host", p.host)
            .put("port", p.port)
            .put("username", p.username.orEmpty())
            .put("domain", p.domain.orEmpty())
            .put("label", p.label.orEmpty())
            .put("pcConnectMode", opts.pcConnectMode)
        arr.put(obj)
    }
    return org.json.JSONObject()
        .put("version", 1)
        .put("profiles", arr)
        .toString()
}

private fun importProfilesJson(raw: String): List<Pair<ConnectionProfile, Boolean>> = runCatching {
    val root = org.json.JSONObject(raw.trim())
    val arr = root.optJSONArray("profiles") ?: return@runCatching emptyList()
    buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val host = obj.optString("host", "").trim()
            if (host.isEmpty()) continue
            val port = obj.optInt("port", 3389).coerceIn(1, 65535)
            val username = obj.optString("username", "").trim().ifEmpty { null }
            val domain = obj.optString("domain", "").trim().ifEmpty { null }
            val label = obj.optString("label", "").trim().ifEmpty { null }
            val isPc = obj.optBoolean("pcConnectMode", false)
            add(
                ConnectionProfile(
                    host = host,
                    port = port,
                    username = username,
                    domain = domain,
                    label = label,
                ) to isPc,
            )
        }
    }
}.getOrDefault(emptyList())

private fun fetchRemoteReleaseStatus(context: Context): String {
    val current = appVersion(context)
    return runCatching {
        val conn = (java.net.URL("https://api.xydeskremote.biz.id/api/status").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(body)
        val latest = json.optString("version", "1.0.0")
        val launchDate = json.optString("launchWib", "Saturday, October 3, 2026 · 10:00 WIB")
        seiNow(
            "Installed version: v{0} · Public Release Target: v{1} ({2}). Official release portal will be available at rilisin.xyverse.my.id.",
            current,
            latest,
            launchDate,
        )
    }.getOrElse {
        seiNow(
            "Installed version: v{0} · Visit www.xydeskremote.biz.id or rilisin.xyverse.my.id for the latest release info.",
            current,
        )
    }
}
