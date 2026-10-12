import os

file_path = r"c:\Users\seijiadmin\Desktop\SeiDesk-RM\client\Android\Studio\app\src\main\kotlin\io\seidesk\rm\ui\SeiDeskHome.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Add necessary imports
new_imports = """import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
"""

if "import androidx.compose.foundation.lazy.grid.LazyVerticalGrid" not in content:
    content = content.replace("import io.seidesk.rm.core.ConnectionLog", new_imports + "import io.seidesk.rm.core.ConnectionLog")

# In SeiDeskHome composable, add duplicateDevice and pass to DevicesScreen
old_call = """                SeiRoute.Devices -> if (section == SeiSection.PERANGKAT) {
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
                }"""

new_call = """                SeiRoute.Devices -> if (section == SeiSection.PERANGKAT) {
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
                        onDuplicate = { prof ->
                            scope.launch {
                                val newProfile = prof.copy(
                                    id = java.util.UUID.randomUUID().toString(),
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
                }"""

content = content.replace(old_call, new_call)

# Now replace from `/**\n * Sesi yang sedang hidup di proses ini.` down to before `private fun appVersion(context: Context): String`
target_marker = "/**\n * Sesi yang sedang hidup di proses ini."
end_marker = "private fun appVersion(context: Context): String"

idx_start = content.find(target_marker)
idx_end = content.find(end_marker)

if idx_start == -1 or idx_end == -1:
    print(f"Error finding markers: start={idx_start}, end={idx_end}")
    exit(1)

new_components = '''/**
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

                    // Scrim mono telemetry chip
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "● 60 fps · 18 ms",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.9f),
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
                        style = MaterialTheme.typography.bodySmall,
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
                body = sei("Disconnect from \\"{0}\\" ({1}) now?", target.label, target.address),
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

    // Probes reachability across all saved devices off the main thread with 1.5s timeout
    val reachabilityMap = remember { mutableStateMapOf<String, ReachabilityResult>() }
    LaunchedEffect(favorites) {
        withContext(Dispatchers.IO) {
            favorites.forEach { prof ->
                val res = ReachabilityProbe.check(prof.host, prof.port)
                reachabilityMap[prof.id] = res
            }
        }
    }

    // Dynamic counts
    val pcCount = remember(favorites) {
        favorites.count { RdpOptions.of(context, it.id).pcConnectMode }
    }
    val rdpCount = remember(favorites) {
        favorites.count { !RdpOptions.of(context, it.id).pcConnectMode }
    }
    val onlineCount = reachabilityMap.values.count { it.online }

    val filteredFavorites = remember(favorites, searchQuery, modeFilterIndex, reachabilityMap) {
        val q = searchQuery.trim().lowercase()
        favorites.filter { p ->
            val isPc = RdpOptions.of(context, p.id).pcConnectMode
            val isOnline = reachabilityMap[p.id]?.online == true
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
                !last.contains("session.connect kembali") &&
                    !last.contains("-> Connected") &&
                    !last.contains("-> Disconnected") &&
                    !last.contains("intent tanpa profil")
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
                            contentDescription = sei("Toggle view"),
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
                        SeiLogo(size = 56.dp)
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
                val useGrid = gridMode || favorites.size <= 2

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
                    subtitle = sei("Ultra-low latency streaming with GPU acceleration (NVIDIA/AMD/Intel)"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF64748B)),
                    )
                    Text(
                        text = if (isOnline) (if (reachability?.latencyMs != null && reachability.latencyMs > 0) "${reachability.latencyMs} ms" else "Online") else "Offline",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Endpoint & User in mono
            Text(
                text = (if (rdpOptions.pcConnectMode) profile.host else "${profile.host}:${profile.port}") +
                    if (!profile.username.isNullOrBlank()) "  ·  ${profile.username}" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
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
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF64748B)),
                    )
                    Text(
                        if (isOnline) (if (reachability?.latencyMs != null && reachability.latencyMs > 0) "${reachability.latencyMs} ms" else "Online") else "Offline",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (isOnline) Color(0xFF10B981) else Color.White.copy(alpha = 0.85f),
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
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
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
'''

content = content[:idx_start] + new_components + "\n" + content[idx_end:]

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Updated SeiDeskHome.kt successfully.")
