package io.seidesk.rm.ui.device

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.freerdp.freerdpcore.services.LibFreeRDP
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.SeiAudioMode
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiSectionLabel
import io.seidesk.rm.ui.components.SeiSlider
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.sei

@Composable
fun DeviceAudioSection(
    audioIndex: Int,
    onAudioIndexChange: (Int) -> Unit,
    options: RdpOptions,
    onOptionsChange: (RdpOptions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            sei("Audio output mode"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SeiAudioMode.entries.forEachIndexed { index, mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (index == audioIndex) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                        )
                        .border(
                            1.dp,
                            if (index == audioIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            MaterialTheme.shapes.medium,
                        )
                        .clickable { onAudioIndexChange(index) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sei(mode.titleEn), style = MaterialTheme.typography.titleSmall)
                        Text(
                            sei(mode.detailEn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val audioNote = when (SeiAudioMode.entries[audioIndex]) {
                SeiAudioMode.DEVICE ->
                    sei(
                        "Device mode plays PC sound over the RDP audio channel (no host agent). If PC 'Remote Audio' is disabled for VB-CABLE, switch to Remote mode.",
                    )
                SeiAudioMode.REMOTE ->
                    sei(
                        "Remote mode routes PC sound through the UDP :4433 bridge, so SeiDeskHost / remote host agent must run on the PC.",
                    )
                SeiAudioMode.OFF ->
                    sei("Audio fully off — the audio bridge stays down.")
            }
            Text(
                audioNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val bridgeRelevant = SeiAudioMode.entries[audioIndex] == SeiAudioMode.REMOTE
        SeiToggleRow(
            title = sei("PC audio over UDP (bridge)"),
            subtitle = if (bridgeRelevant) {
                sei("Uses UDP 4433 audio subprotocol bridge from host agent.")
            } else {
                sei("Not used: Device mode plays sound through the RDP channel.")
            },
            checked = options.quicAudio && bridgeRelevant,
            onCheckedChange = { v -> onOptionsChange(options.copy(quicAudio = v)) },
            leading = SeiIcons.Volume,
            enabled = bridgeRelevant,
        )

        SeiToggleRow(
            title = sei("Microphone redirection"),
            subtitle = sei("Send phone microphone audio to remote desktop"),
            checked = options.microphone,
            onCheckedChange = { onOptionsChange(options.copy(microphone = it)) },
            leading = SeiIcons.Mic,
        )

        if (options.microphone) {
            SeiSectionLabel(sei("Microphone DSP (on phone)"))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SeiSliderRow(
                    label = sei("Mic gain"),
                    value = options.micGainDb.toFloat(),
                    suffix = " dB",
                    range = -12f..24f,
                    steps = 6,
                ) { onOptionsChange(options.copy(micGainDb = it.toInt())) }

                SeiSliderRow(
                    label = sei("Noise gate"),
                    value = options.micGateDb.toFloat(),
                    suffix = " dBFS",
                    range = -70f..0f,
                    steps = 6,
                ) { onOptionsChange(options.copy(micGateDb = it.toInt())) }

                SeiToggleRow(
                    title = sei("Noise suppression"),
                    subtitle = sei("High-pass filter and expander to reduce background noise and hiss"),
                    checked = options.micNoiseSuppression,
                    onCheckedChange = { onOptionsChange(options.copy(micNoiseSuppression = it)) },
                    leading = SeiIcons.Mic,
                )

                SeiToggleRow(
                    title = sei("Auto gain (AGC)"),
                    subtitle = sei("Automatically level phone microphone volume while speaking"),
                    checked = options.micAgc,
                    onCheckedChange = { onOptionsChange(options.copy(micAgc = it)) },
                    leading = SeiIcons.Mic,
                )
            }
        }

        SeiToggleRow(
            title = sei("Clipboard redirection"),
            subtitle = sei("Two-way text and image copy-paste"),
            checked = options.clipboard,
            onCheckedChange = { onOptionsChange(options.copy(clipboard = it)) },
            leading = SeiIcons.Clip,
        )

        SeiToggleRow(
            title = sei("Local drive storage"),
            subtitle = sei("Phone storage folder appears as 'SeiDesk' drive on remote PC"),
            checked = options.localDrive,
            onCheckedChange = { onOptionsChange(options.copy(localDrive = it)) },
            leading = SeiIcons.Folder,
        )

        if (options.localDrive) {
            StorageAccessRow()
        }

        SeiToggleRow(
            title = sei("Camera redirection"),
            subtitle = sei("Phone camera as remote webcam (if supported by server)"),
            checked = options.camera,
            onCheckedChange = { onOptionsChange(options.copy(camera = it)) },
            leading = SeiIcons.Shot,
        )
    }
}

@Composable
fun StorageAccessRow() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LibFreeRDP.hasAllFilesAccess()) }
    val path = remember(granted) { LibFreeRDP.appDrivePath(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = LibFreeRDP.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (granted) {
                sei("Full access: remote PC can access shared phone storage")
            } else {
                sei("Limited access: only app folder is visible to remote PC")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Path: $path",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            sei(
                "How to transfer files: Open Windows File Explorer -> This PC -> open 'SeiDesk on Android' (or \\\\tsclient\\SeiDesk) to copy files.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!granted) {
            SeiPillButton(
                text = sei("Grant all-files access"),
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

@Composable
fun SeiSliderRow(
    label: String,
    value: Float,
    suffix: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${value.toInt()}$suffix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SeiSlider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}
