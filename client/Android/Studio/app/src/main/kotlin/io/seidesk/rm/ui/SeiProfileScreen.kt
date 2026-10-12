package io.seidesk.rm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seidesk.rm.ui.components.SeiGlassCard
import io.seidesk.rm.ui.components.SeiGlassRow
import io.seidesk.rm.ui.components.SeiGlassSectionLabel
import io.seidesk.rm.ui.components.SeiGlassTile
import io.seidesk.rm.ui.components.SeiIconPill
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiLogo
import io.seidesk.rm.ui.components.SeiOverlay
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiTileGrid
import io.seidesk.rm.ui.components.SeiTopBar
import io.seidesk.rm.ui.components.SeiWordmark
import kotlinx.coroutines.launch
import io.seidesk.rm.ui.theme.SeiThemeState

/**
 * Layar Profil — dashboard kartu.
 *
 * Enam seksi yang dulu berdesakan di nav bawah sekarang jadi ubin di grid,
 * dikelompokkan dengan judul seksi supaya hierarkinya jelas tanpa perlu
 * menggulir jauh. Gaya kartunya sama dengan pemutar musik (lihat `XyGlass`),
 * sesuai permintaan pengguna agar seluruh app satu bahasa visual.
 */
@Composable
internal fun SeiProfileScreen(
    onOpenSection: (SeiSection) -> Unit,
    appPrefs: AppPrefs,
    deviceCount: Int,
    onShowLog: () -> Unit,
    onBackupProfiles: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onOpenFeedback: () -> Unit,
) {
    val context = LocalContext.current
    // BuildConfig tidak dipakai di lapisan UI; versi dibaca dari PackageManager
    // supaya tetap benar tanpa menambah konfigurasi build.
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    // Mode tema sebagai state lokal supaya segmen langsung berpindah; nilai
    // sebenarnya tetap disimpan lewat SeiThemeState.set (prefs + state global).
    var themeMode by remember {
        mutableIntStateOf(
            if (SeiThemeState.mode == Int.MIN_VALUE) SeiThemeState.FOLLOW_SYSTEM else SeiThemeState.mode,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SeiTopBar(
            title = sei("Profile"),
            actions = {},
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- akun komunitas (guest / GitHub) ----
            CommunityAccountCard()

            // ---- identitas app ----
            SeiGlassCard(padding = 16.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        SeiLogo(modifier = Modifier.size(26.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SeiWordmark(fontSize = 20.sp)
                        Text(
                            buildString {
                                append(sei( "Version"))
                                append(" ")
                                append(versionName ?: "-")
                                append("  ·  ")
                                append(
                                    sei(
                                        "{0} saved devices",
                                        deviceCount,
                                    ),
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- seksi sebagai grid kartu ----
            SeiGlassSectionLabel(sei("Settings"))
            SeiTileGrid(columns = 2, spacing = 10.dp) {
                SeiSection.entries.forEach { section ->
                    val title = sectionTitle(section)
                    val summary = when (section) {
                        SeiSection.TAMPILAN -> "1920×1080 · 60 Hz"
                        SeiSection.KEAMANAN -> if (appPrefs.requireDeviceLock) sei("Biometric on") else sei("PIN / Biometric")
                        SeiSection.KREDENSIAL -> sei("{0} saved", deviceCount)
                        SeiSection.UMUM -> sei("Audio & input")
                        SeiSection.PERANGKAT -> sei("{0} devices", deviceCount)
                        SeiSection.TENTANG -> "v${versionName ?: "1.0.0"}"
                    }
                    item {
                        SeiGlassTile(
                            icon = sectionIcon(section),
                            title = title,
                            subtitle = summary,
                            onClick = { onOpenSection(section) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ---- tema ----
            SeiGlassSectionLabel(sei("Appearance"))
            SeiGlassCard(padding = 14.dp) {
                Text(
                    sei("Theme mode"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    sei(
                        "Applies instantly on every screen, without reloading the app.",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                SeiSegmented(
                    options = listOf(
                        sei("Follow system"),
                        sei("Dark"),
                        sei("Light"),
                    ),
                    selectedIndex = themeMode,
                    onSelect = { index ->
                        themeMode = index
                        SeiThemeState.set(appPrefs, index)
                    },
                )
            }

            // ---- diagnostik ----
            SeiGlassSectionLabel(sei("Diagnostics & Tools"))
            SeiGlassRow(
                icon = SeiIcons.Terminal,
                title = sei("Connection log"),
                subtitle = sei(
                    "Last 20 lines of the native boot log.",
                ),
                onClick = onShowLog,
            )
            SeiGlassRow(
                icon = SeiIcons.Folder,
                title = sei("Backup & Restore Profiles"),
                subtitle = sei(
                    "Export or import device profiles via JSON.",
                ),
                onClick = onBackupProfiles,
            )
            SeiGlassRow(
                icon = SeiIcons.Shield,
                title = sei("Check updates"),
                subtitle = sei(
                    "Check release status and store updates.",
                ),
                onClick = onCheckUpdates,
            )
            SeiGlassRow(
                icon = SeiIcons.Heart,
                title = sei("Feedback & suggestions"),
                subtitle = sei(
                    "Share to any app, or copy the draft.",
                ),
                onClick = onOpenFeedback,
            )
        }
    }
}

/** Ikon per seksi — sama dengan pemetaan lama di nav bawah enam slot. */
private fun sectionIcon(section: SeiSection): ImageVector = when (section) {
    SeiSection.PERANGKAT -> SeiIcons.Monitor
    SeiSection.TAMPILAN -> SeiIcons.Fit
    SeiSection.KREDENSIAL -> SeiIcons.Lock
    SeiSection.UMUM -> SeiIcons.Sliders
    SeiSection.KEAMANAN -> SeiIcons.Shield
    SeiSection.TENTANG -> SeiIcons.Info
}

/**
 * Kartu akun komunitas.
 *
 * Tanpa login: otomatis jadi tamu dengan ID unik (SEI-XXXXXX) yang bisa
 * dipulihkan setelah pasang ulang lewat kode pemulihan. Login GitHub (PAT di
 * fitur RDP Gratis) otomatis mengisi nama tampilan. Nama bisa dikustom.
 */
@Composable
private fun CommunityAccountCard() {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var name by remember { mutableStateOf(api.displayName()) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var restoreDraft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val accountId = remember { api.friendlyAccountId() }

    // Foto profil: cache lokal + unggah ke worker biar muncul di feed.
    val avatarFile = remember {
        val seiFile = java.io.File(context.filesDir, "sei_avatar.webp")
        if (seiFile.exists()) seiFile else {
            val legacy = java.io.File(context.filesDir, "xy_avatar.webp")
            if (legacy.exists()) legacy else seiFile
        }
    }
    var avatarBitmap by remember {
        mutableStateOf(
            runCatching {
                if (avatarFile.exists()) {
                    android.graphics.BitmapFactory.decodeFile(avatarFile.absolutePath)
                } else null
            }.getOrNull(),
        )
    }
    var avatarBusy by remember { mutableStateOf(false) }
    var githubPatDraft by remember { mutableStateOf("") }
    var githubOpen by remember { mutableStateOf(false) }
    var githubBusy by remember { mutableStateOf(false) }
    var githubUser by remember { mutableStateOf<String?>(null) }
    val pickAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                avatarBusy = true
                try {
                    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val src = raw?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                    if (src == null) {
                        message = seiNow( "Couldn't read the image.")
                    } else {
                        val side = 256
                        val scale = minOf(1f, side / maxOf(src.width, src.height).toFloat())
                        val bmp = if (scale < 1f) {
                            android.graphics.Bitmap.createScaledBitmap(
                                src, (src.width * scale).toInt().coerceAtLeast(1),
                                (src.height * scale).toInt().coerceAtLeast(1), true,
                            )
                        } else src
                        val stream = java.io.ByteArrayOutputStream()
                        val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) {
                            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            android.graphics.Bitmap.CompressFormat.WEBP
                        }
                        bmp.compress(fmt, 85, stream)
                        val bytes = stream.toByteArray()
                        if (bytes.size > 200_000) {
                            message = seiNow( "Photo too large. Try another image.")
                        } else {
                            avatarFile.writeBytes(bytes)
                            avatarBitmap = bmp
                            runCatching { api.uploadAvatar(bytes, "image/webp") }
                            message = seiNow( "Profile photo saved.")
                        }
                    }
                } finally {
                    avatarBusy = false
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        githubUser = runCatching {
            val vault = io.seidesk.rm.security.CredentialVault(context.applicationContext)
            val pat = vault.get(io.seidesk.rm.ui.rdp.SeiRdpConfig.VAULT_PAT_ID)
                ?: vault.get(io.seidesk.rm.ui.rdp.SeiRdpConfig.VAULT_PAT_ID_LEGACY)
            if (pat != null) io.seidesk.rm.ui.rdp.SeiRdpClient.whoami(pat) else null
        }.getOrNull()
    }

    // Login GitHub (bila PAT tersimpan) otomatis jadi nama tampilan,
    // kecuali pengguna sudah memilih nama sendiri.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (name == null) {
            val vault = io.seidesk.rm.security.CredentialVault(context.applicationContext)
            val pat = vault.get(io.seidesk.rm.ui.rdp.SeiRdpConfig.VAULT_PAT_ID)
                ?: vault.get(io.seidesk.rm.ui.rdp.SeiRdpConfig.VAULT_PAT_ID_LEGACY)
            if (pat != null) {
                val gh = runCatching { io.seidesk.rm.ui.rdp.SeiRdpClient.whoami(pat) }.getOrNull()
                if (gh != null && api.displayName() == null) {
                    runCatching { api.setDisplayName(gh) }
                    name = api.displayName() ?: gh
                }
            }
        }
    }

    SeiGlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = avatarBitmap
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = sei( "Profile photo"),
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Text(
                        (name?.take(1) ?: "T").uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name ?: sei( "Guest $accountId"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "ID: $accountId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SeiIconPill(
                SeiIcons.Clip,
                {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(api.restoreCode()))
                    message = seiNow( "Recovery code copied. Keep it to restore this account after a reinstall.")
                },
                contentDescription = sei( "Copy recovery code"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SeiPillButton(
                text = sei( "Change name"),
                onClick = { editing = !editing; draft = name.orEmpty() },
                primary = false,
                compact = true,
            )
            SeiPillButton(
                text = if (avatarBusy) sei( "Saving…") else sei( "Change photo"),
                onClick = {
                    pickAvatar.launch("image/*")
                },
                primary = false,
                compact = true,
                enabled = !avatarBusy,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ghUser = githubUser
            if (ghUser != null) {
                SeiPillButton(
                    text = sei( "GitHub: $ghUser"),
                    onClick = {},
                    primary = false,
                    compact = true,
                    enabled = false,
                )
                SeiPillButton(
                    text = sei( "Sign out"),
                    onClick = {
                        scope.launch {
                            runCatching {
                                io.seidesk.rm.security.CredentialVault(context.applicationContext)
                                    .remove(io.seidesk.rm.ui.rdp.SeiRdpConfig.VAULT_PAT_ID)
                            }
                            githubUser = null
                            message = seiNow( "GitHub account removed.")
                        }
                    },
                    primary = false,
                    compact = true,
                )
            } else {
                SeiPillButton(
                    text = sei( "Sign in with GitHub"),
                    onClick = { githubOpen = true },
                    primary = false,
                    compact = true,
                )
            }
        }
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 24) draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                sei( "Display name (max 24)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                SeiPillButton(
                    text = if (busy) "…" else sei( "Save"),
                    onClick = {
                        if (!busy && draft.trim().isNotEmpty()) {
                            busy = true
                            scope.launch {
                                val ok = runCatching { api.setDisplayName(draft) }.getOrDefault(false)
                                message = if (ok) {
                                    name = api.displayName()
                                    seiNow( "Name saved.")
                                } else {
                                    seiNow( "Couldn't save. Check network and retry.")
                                }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && draft.trim().isNotEmpty(),
                    compact = true,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(
                value = restoreDraft,
                onValueChange = { restoreDraft = it },
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { inner ->
                    if (restoreDraft.isEmpty()) {
                        Text(
                            sei( "Old account recovery code…"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
            SeiPillButton(
                text = sei( "Restore"),
                onClick = {
                    val ok = api.restoreClientId(restoreDraft)
                    message = if (ok) {
                        editing = false
                        scope.launch {
                            name = runCatching { api.fetchServerDisplayName() }.getOrNull() ?: api.displayName()
                            name?.let { api.setDisplayNameLocally(it) }
                        }
                        seiNow( "Account restored. Reloading your old name.")
                    } else {
                        seiNow( "Invalid code. Paste the recovery code you copied before.")
                    }
                },
                primary = false,
                compact = true,
                enabled = restoreDraft.trim().isNotEmpty(),
            )
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (githubOpen) {
        SeiOverlay(
            title = sei( "Sign in with GitHub"),
            onDismiss = { if (!githubBusy) githubOpen = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    sei(
                        "Create a Personal Access Token (scope: read:user) on GitHub and paste it here. " +
                            "The token stays in device Keystore and is only used for your profile name.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SeiPillButton(
                    text = sei( "Open github.com/settings/tokens"),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/settings/tokens/new?description=SeiDesk+RM&scope=read:user"),
                                ),
                            )
                        }
                    },
                    primary = false,
                    compact = true,
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = githubPatDraft,
                    onValueChange = { githubPatDraft = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (githubPatDraft.isEmpty()) {
                            Text(
                                sei( "Paste token (ghp_… / github_pat_…)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeiPillButton(
                        text = sei( "Cancel"),
                        onClick = { githubOpen = false },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    SeiPillButton(
                        text = if (githubBusy) sei( "Checking…") else sei( "Sign in"),
                        onClick = {
                            scope.launch {
                                githubBusy = true
                                try {
                                    val token = githubPatDraft.trim()
                                    val user = io.seidesk.rm.ui.rdp.SeiRdpClient.whoami(token)
                                    if (user == null) {
                                        message = seiNow( "Invalid token or missing scope (read:user).")
                                    } else {
                                        val saved = runCatching {
                                            io.seidesk.rm.security.CredentialVault(context.applicationContext)
                                                .put(io.seidesk.rm.ui.rdp.SeiRdpConfig.VAULT_PAT_ID, token)
                                        }.getOrDefault(false)
                                        if (saved) {
                                            githubUser = user
                                            githubOpen = false
                                            githubPatDraft = ""
                                            if (api.displayName() == null) {
                                                runCatching { api.setDisplayName(user) }
                                                name = api.displayName() ?: user
                                            }
                                            message = seiNow( "Signed in as $user.")
                                        } else {
                                            message = seiNow( "Keystore unavailable on this device.")
                                        }
                                    }
                                } finally {
                                    githubBusy = false
                                }
                            }
                        },
                        enabled = !githubBusy && githubPatDraft.trim().isNotEmpty(),
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
