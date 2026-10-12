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
import io.seidesk.rm.ui.components.rememberXyNotice
import io.seidesk.rm.ui.components.SeiLogo
import io.seidesk.rm.ui.components.SeiGlassCard
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiRow
import io.seidesk.rm.ui.components.XySectionLabel
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiTopBar
import io.seidesk.rm.ui.components.SeiWordmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seksi di drawer samping. Judul dua bahasa dibaca di [sectionTitle]. */
internal enum class SeiSection(val title: String, val titleEn: String) {
    PERANGKAT("Perangkat", "Devices"),
    TAMPILAN("Tampilan", "Display"),
    KREDENSIAL("Kredensial", "Credentials"),
    UMUM("Umum", "General"),
    KEAMANAN("Keamanan", "Security"),
    TENTANG("Tentang", "About"),
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
    var drawerOpen by remember { mutableStateOf(false) }
    var helpToolsOpen by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(SeiSection.PERANGKAT) }
    // Tab nav bawah (3 tujuan). `section` tetap ada karena seksi dibuka dari
    // drawer maupun dari layar Profil, dan keduanya menggambar SectionScreen.
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
    val notice = rememberXyNotice()

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

    fun submit(profile: ConnectionProfile, rememberPassword: Boolean, startNow: Boolean) {
        scope.launch {
            val saved = runCatching { repo.save(profile, rememberPassword) }
            if (saved.isFailure) {
                notice.show(
                    seiNow( "Profile not saved: ") +
                        (saved.exceptionOrNull()?.message ?: seiNow( "storage error")),
                )
            } else if (saved.getOrDefault(true) == false) {
                notice.show(
                    seiNow(
                        "Profile saved, but the password could not be stored securely. Re-enter it later; it was not remembered.",
                    ),
                )
            }
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
            drawerOpen -> drawerOpen = false
            route != SeiRoute.Devices || section != SeiSection.PERANGKAT -> {
                route = SeiRoute.Devices
                section = SeiSection.PERANGKAT
                tab = SeiTab.HOME
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
                    onSubmit = { profile, remember, startNow -> submit(profile, remember, startNow) },
                )

                SeiRoute.Devices -> if (section == SeiSection.PERANGKAT) {
                    DevicesScreen(
                        favorites = favorites,
                        crashLog = crashLog,
                        bootTail = bootTail,
                        sharedFileNotice = sharedFileNotice,
                        onDismissSharedNotice = onDismissSharedNotice,
                        onMenu = { drawerOpen = true },
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
                        onDelete = { profile -> confirmDeleteDevice = profile },
                        onShowCrash = { showCrash = true },
                        onShowBoot = { showBoot = true },
                    )
                } else {
                    SectionScreen(
                        section = section,
                        favorites = favorites,
                        onMenu = { drawerOpen = true },
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

                SeiRoute.Feed -> SeiFeedScreen(onMenu = { drawerOpen = true })

                SeiRoute.Profile -> SeiProfileScreen(
                    onMenu = { drawerOpen = true },
                    onOpenSection = {
                        section = it
                        route = SeiRoute.Devices
                    },
                    appPrefs = appPrefs,
                    deviceCount = favorites.size,
                    onShowLog = { showBoot = true },
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

    // ---- drawer kiri (custom, bukan ModalNavigationDrawer bawaan) ----
    if (drawerOpen) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .clickable { drawerOpen = false }
                .zIndex(40f),
        )
    }
    AnimatedVisibility(
        visible = drawerOpen,
        enter = slideInHorizontally { -it },
        exit = slideOutHorizontally { -it },
        modifier = Modifier.zIndex(41f),
    ) {
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {}
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SeiLogo(modifier = Modifier.size(30.dp))
                Column {
                    SeiWordmark(fontSize = 21.sp)
                    Text(
                        "SeiDesk \u2022 Remote Desktop",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SeiDivider()
            Spacer(Modifier.height(8.dp))
            SeiSection.entries.filter { it != SeiSection.TAMPILAN }.forEach { item ->
                SeiRow(
                    // Dulu title mentah (Indonesia saja) — drawer satu-satunya
                    // tempat yang tidak ikut bahasa. Sekarang dua bahasa.
                    title = sei( item.titleEn),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    leading = when (item) {
                        SeiSection.PERANGKAT -> SeiIcons.Monitor
                        SeiSection.TAMPILAN -> SeiIcons.Fit
                        SeiSection.KREDENSIAL -> SeiIcons.Lock
                        SeiSection.UMUM -> SeiIcons.Sliders
                        // Dulu Info — sama dengan Tentang, jadi dua seksi
                        // bersebelahan punya ikon identik.
                        SeiSection.KEAMANAN -> SeiIcons.Shield
                        SeiSection.TENTANG -> SeiIcons.Info
                    },
                    leadingTint = if (item == section) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        section = item
                        route = SeiRoute.Devices
                        // Tab ikut menyorot: seksi adalah bagian Profil, kecuali
                        // Perangkat yang memang isi Beranda.
                        tab = if (item == SeiSection.PERANGKAT) SeiTab.HOME else SeiTab.PROFILE
                        drawerOpen = false
                    },
                )
            }
            SeiRow(
                title = sei( "Tools & help"),
                subtitle = sei( "Backup, updates, and feedback"),
                modifier = Modifier.padding(horizontal = 8.dp),
                leading = SeiIcons.Folder,
                trailing = {
                    Text(
                        if (helpToolsOpen) "−" else "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { helpToolsOpen = !helpToolsOpen },
            )
            if (helpToolsOpen) {
                SeiPillButton(
                    text = sei( "Backup / Restore Profiles"),
                    onClick = {
                        drawerOpen = false
                        backupJsonInput = exportProfilesJson(context, favorites)
                        backupModalOpen = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    primary = false,
                    icon = SeiIcons.Folder,
                    compact = true,
                )
                Spacer(Modifier.height(6.dp))
                SeiPillButton(
                    text = sei( "Check Update & RilisIn Store"),
                    onClick = {
                        drawerOpen = false
                        updateModalOpen = true
                        updateStatusText = seiNow( "Checking latest release status from www.xydeskremote.biz.id...")
                        scope.launch {
                            val status = withContext(Dispatchers.IO) { fetchRemoteReleaseStatus(context) }
                            updateStatusText = status
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    primary = false,
                    icon = SeiIcons.Shield,
                    compact = true,
                )
                Spacer(Modifier.height(6.dp))
                SeiPillButton(
                    text = sei( "Feedback & suggestions"),
                    onClick = {
                        drawerOpen = false
                        feedbackOpen = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    primary = false,
                    icon = SeiIcons.Info,
                    compact = true,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "v${appVersion(context)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
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
            title = sei( "Waking PC (Wake-on-LAN)"),
            body = sei(
                "Magic Packet sent to \"{0}\" ({1}:{2}). XyDesk is automatically polling the RDP port for up to 35 seconds...",
                target.label ?: target.host,
                target.host,
                target.port,
            ),
            confirmLabel = sei( "Connect Now"),
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
 * Sesi yang sedang hidup di proses ini.
 *
 * Daftarnya dibaca dari [SeiSessionRegistry] dan disegarkan tiap 1,5 detik
 * selama home tampil — cukup untuk kasus "buka server kedua sambil yang
 * pertama tetap jalan".
 */
@Composable
private fun LiveSessionsCard() {
    var live by remember { mutableStateOf(SeiSessionRegistry.list()) }
    var confirmKill by remember { mutableStateOf<SeiSessionRegistry.Live?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            live = SeiSessionRegistry.list()
            kotlinx.coroutines.delay(1_500)
        }
    }
    if (live.isEmpty()) return
    SeiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
            }
            XySectionLabel(sei( "Active sessions ({0})", live.size))
        }
        Spacer(Modifier.height(10.dp))
        live.forEach { item ->
            val rowShape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), rowShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item.address,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SeiPillButton(
                    text = sei( "Open"),
                    onClick = { item.open() },
                    icon = SeiIcons.Play,
                    compact = true,
                )
                SeiPillButton(
                    text = sei( "Disconnect"),
                    onClick = { confirmKill = item },
                    primary = false,
                    compact = true,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    confirmKill?.let { target ->
        SeiDialog(
            title = sei( "Disconnect active session?"),
            body = sei(
                "Disconnect from \"{0}\" ({1}) now?",
                target.label,
                target.address,
            ),
            confirmLabel = sei( "Disconnect"),
            onConfirm = {
                confirmKill = null
                target.kill()
            },
            dismissLabel = sei( "Cancel"),
            onDismiss = { confirmKill = null },
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
    onDelete: (ConnectionProfile) -> Unit,
    onShowCrash: () -> Unit,
    onShowBoot: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var modeFilterIndex by remember { mutableStateOf(0) } // 0 = Semua, 1 = Koneksi PC, 2 = Koneksi RDP
    var showAddBubble by remember { mutableStateOf(false) }
    var showPcConnectionNotice by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (sharedFileNotice != null) {
                Banner(
                    text = sharedFileNotice,
                    onClick = onDismissSharedNotice,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (showPcConnectionNotice) {
                Banner(
                    text = sei(
                        "Direct PC connection is still in development and separate from RDP. RDP remains available.",
                    ),
                    onClick = { showPcConnectionNotice = false },
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (crashLog != null) {
                Banner(
                    text = sei(
                        "An error happened before — tap to see the log",
                    ),
                    onClick = onShowCrash,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            val bootSuspect = bootTail.isNotEmpty() && bootTail.last().let { last ->
                !last.contains("session.connect kembali") &&
                    !last.contains("-> Connected") &&
                    !last.contains("-> Disconnected") &&
                    !last.contains("intent tanpa profil")
            }
            if (bootSuspect) {
                Banner(
                    text = sei(
                        "The last session stopped midway — tap to see the log",
                    ),
                    onClick = onShowBoot,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SeiTopBar(
                title = sei( "Devices"),
                onBack = null,
                actions = {
                    SeiIconPill(SeiIcons.Menu, onMenu, flat = true, contentDescription = sei( "Menu"))
                    SeiIconPill(
                        SeiIcons.Plus,
                        { showAddBubble = !showAddBubble },
                        active = true,
                        contentDescription = sei( "Add device"),
                    )
                },
            )

            if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SeiCard(modifier = Modifier.fillMaxWidth()) {
                            Text(sei( "No devices yet"), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                sei(
                                    "Choose PC Connection (ID & Password via XyDeskHost.exe with Gaming GPU support) " +
                                        "or standard RDP Connection over a local IP, domain, or Tailscale.",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeiPillButton(
                                    sei( "PC (ID & Pass)"),
                                    onAddPcQuick,
                                    icon = SeiIcons.Plus,
                                    modifier = Modifier.weight(1f),
                                )
                                SeiPillButton(
                                    sei( "Standard RDP"),
                                    onAddRdp,
                                    primary = false,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            } else {
                val filteredFavorites = remember(favorites, searchQuery, modeFilterIndex) {
                    val q = searchQuery.trim().lowercase()
                    favorites.filter { p ->
                        val isPc = RdpOptions.of(context, p.id).pcConnectMode
                        val modeOk = when (modeFilterIndex) {
                            1 -> isPc
                            2 -> !isPc
                            else -> true
                        }
                        val queryOk = q.isEmpty() ||
                            p.label.orEmpty().lowercase().contains(q) ||
                            p.host.lowercase().contains(q) ||
                            p.username.orEmpty().lowercase().contains(q)
                        modeOk && queryOk
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item { LiveSessionsCard() }
                    item {
                        // Pemilik tidak menemukan fitur create-RDP (2026-10-10) —
                        // sebelumnya hanya muncul di dalam bubble "+". Sekarang
                        // ada kartu tetap di puncak daftar perangkat.
                        SeiGlassCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        SeiIcons.Monitor,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        sei( "RdpFree"),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                    Text(
                                        sei(
                                            "Windows PC from your own GitHub account — no Tailscale needed",
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SeiPillButton(
                                    text = sei( "Create"),
                                    onClick = onCreateRdp,
                                    modifier = Modifier.height(36.dp),
                                    compact = true,
                                )
                            }
                        }
                    }
                    item {
                        SeiSegmented(
                            options = listOf(
                                sei( "All ({0})", favorites.size),
                                sei( "PC Connect"),
                                sei( "RDP Session"),
                            ),
                            selectedIndex = modeFilterIndex,
                            onSelect = { modeFilterIndex = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (favorites.size >= 2) {
                        item {
                            SeiField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = sei( "Search devices"),
                                hint = sei( "PC name, IP address, or username..."),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(filteredFavorites, key = { it.id }) { profile ->
                        DeviceCard(
                            profile = profile,
                            onConnect = { onConnect(profile) },
                            onWakeConnect = { onWakeConnect(profile) },
                            onEdit = { onEdit(profile) },
                            onDelete = { onDelete(profile) },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }

        // Bubble Popover Menu saat tombol + ditekan
        if (showAddBubble) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { showAddBubble = false }
                    .zIndex(25f),
            )
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp)
                    .width(296.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {}
                    .padding(10.dp)
                    .zIndex(26f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    sei( "Choose Connection Type"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                SeiRow(
                    title = sei( "PC Connection (ID & Password)"),
                    subtitle = sei(
                        "E2EE + Ultra-Low Latency FPS Gaming path (NVIDIA / AMD / Intel GPU)",
                    ),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    trailing = {
                        Text(
                            sei( "Experimental"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        showAddBubble = false
                        onAddPcQuick()
                    },
                )
                SeiRow(
                    title = sei( "RDP Connection (Host / IP)"),
                    subtitle = sei(
                        "Direct connection via local IP, public domain, or Tailscale",
                    ),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    onClick = {
                        showAddBubble = false
                        onAddRdp()
                    },
                )
                SeiRow(
                    title = sei( "Free 6-Hour RDP (GitHub Actions)"),
                    subtitle = sei(
                        "Spin up a Windows PC from the XyRDP template on your own GitHub account — watch 1 ad + join to unlock",
                    ),
                    modifier = Modifier.padding(horizontal = 2.dp),
                    trailing = {
                        Text(
                            sei( "New"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        showAddBubble = false
                        onCreateRdp()
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    profile: ConnectionProfile,
    onConnect: () -> Unit,
    onWakeConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val rdpOptions = remember(profile.id) { RdpOptions.of(context, profile.id) }
    val hasWol = !rdpOptions.macAddress.isNullOrBlank()
    val streamBadge = when (rdpOptions.streamProfile) {
        SeiStreamProfile.ULTRA_LOW_LATENCY -> sei( "Low Latency")
        SeiStreamProfile.HIGH_VISUAL -> "AVC444"
        SeiStreamProfile.DATA_SAVER -> sei( "Data Saver")
        SeiStreamProfile.CUSTOM -> sei( "Custom")
        SeiStreamProfile.BALANCED, SeiStreamProfile.AUTO -> if (rdpOptions.udpTransport) "UDP+H264" else "H264"
    }
    // Art preview milik app (bukan wallpaper RDP/OS): geometris, diturunkan
    // dari nama perangkat + jenis OS sebagai penanda kecil di pojok.
    val wall = remember(profile.id, profile.label, profile.host) {
        SeiWall.WIN11.forDevice(profile.label ?: profile.host)
    }
    val previewSeed = remember(profile.id, profile.label, profile.host) {
        (profile.label ?: profile.host) + "|" + profile.host
    }

    // Identifikasi jaringan (Tailscale / LAN / Direct / E2EE)
    val networkInfo = remember(profile.host, rdpOptions.pcConnectMode) {
        val h = profile.host.lowercase()
        when {
            rdpOptions.pcConnectMode -> Pair(Color(0xFF818CF8), "PC E2EE")
            h.startsWith("100.") || h.contains(".ts.net") -> Pair(Color(0xFF38BDF8), "Tailscale")
            h.startsWith("192.168.") || h.startsWith("10.") || h.startsWith("172.") ||
                h == "localhost" || h == "127.0.0.1" -> Pair(Color(0xFF10B981), "LAN")
            else -> Pair(Color(0xFF60A5FA), "Direct")
        }
    }
    val networkColor = networkInfo.first
    val networkLabel = networkInfo.second

    val cardShape = RoundedCornerShape(22.dp)
    val cardBorder = Brush.verticalGradient(
        0f to MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        1f to MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
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
                .height(168.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
        ) {
            DevicePreviewArt(seed = previewSeed, os = wall)
            // Screen vignette
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.38f),
                            0.38f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.20f),
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )

            // Top Floating Badges (OS & Network)
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

                // Network Routing Pill
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xB30B0F17))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(networkColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(networkColor),
                        )
                    }
                    Text(
                        networkLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            // Desktop Taskbar di dasar layar monitor
            DesktopTaskbar(Modifier.align(Alignment.BottomCenter))
        }

        // Structured Card Body: Info & Specs
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        ) {
            // Header Row: Title & Connection Type Pill
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    profile.label ?: profile.host,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                val isPcMode = rdpOptions.pcConnectMode
                val modeBg = if (isPcMode) Color(0xFF241838) else MaterialTheme.colorScheme.surfaceVariant
                val modeBorder = if (isPcMode) Color(0xFF818CF8).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                val modeText = if (isPcMode) Color(0xFFC084FC) else MaterialTheme.colorScheme.primary
                val modeLabel = if (isPcMode) "PC E2EE" else "RDP"
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(modeBg)
                        .border(1.dp, modeBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                ) {
                    Text(
                        modeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = modeText,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Endpoint & User Row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (rdpOptions.pcConnectMode) profile.host else "${profile.host}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!profile.username.isNullOrBlank()) {
                    Text(
                        "  ·  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        profile.username.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Capability & Spec Badges
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SpecChip(
                    label = if (rdpOptions.pcConnectMode) rdpOptions.activeCodecLabel() else streamBadge,
                    highlight = true,
                )
                if (rdpOptions.consoleAdmin) {
                    SpecChip(label = "Admin", highlight = false)
                }
                if (hasWol) {
                    SpecChip(label = "WoL", highlight = false)
                }
                if (rdpOptions.audioMode != SeiAudioMode.OFF) {
                    SpecChip(label = sei( "Audio"), highlight = false)
                }
            }

            Spacer(Modifier.height(12.dp))
            SeiDivider()
            Spacer(Modifier.height(10.dp))

            // Action Command Footer
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeiPillButton(
                    text = sei( "Connect"),
                    onClick = onConnect,
                    icon = SeiIcons.Play,
                    compact = false,
                    modifier = Modifier.weight(1f),
                )
                if (hasWol) {
                    SeiPillButton(
                        text = sei( "WoL"),
                        onClick = onWakeConnect,
                        primary = false,
                        compact = true,
                    )
                }
                SeiIconPill(
                    icon = SeiIcons.Sliders,
                    onClick = onEdit,
                    size = 42.dp,
                    flat = false,
                    contentDescription = sei( "RDP settings"),
                )
                SeiIconPill(
                    icon = SeiIcons.Trash,
                    onClick = onDelete,
                    size = 42.dp,
                    flat = false,
                    contentDescription = sei( "Delete"),
                )
            }
        }
    }
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
                fontSize = 10.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = textCol,
            maxLines = 1,
        )
    }
}

/** Strip taskbar tipis di dasar preview supaya terbaca sebagai desktop otentik. */
@Composable
private fun DesktopTaskbar(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(Color(0xE6080B10))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Windows Start Button icon
            Box(
                Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SeiIcons.Windows,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(9.dp),
                )
            }
            // Search bar miniature
            Box(
                Modifier
                    .size(width = 24.dp, height = 12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.30f)),
                )
            }
            // Micro app icons
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.75f)), // Explorer
            )
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF38BDF8).copy(alpha = 0.75f)), // Browser
            )
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.75f)), // Terminal
            )
            Spacer(Modifier.weight(1f))
            // Tray: Wifi / Time miniature
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 10.dp, height = 3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.40f)),
                )
                Text(
                    "12:00",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White.copy(alpha = 0.60f),
                )
            }
        }
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
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
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
        val launchDate = json.optString("launchWib", "Sabtu, 3 Oktober 2026 · 10:00 WIB")
        seiNow(
            "Installed version: v{0} · Public Release Target: v{1} ({2}). Official XyVerse app store will be available at rilisin.xyverse.my.id.",
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
