package io.seidesk.rm.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.SeiPcStreamEngine
import io.seidesk.rm.core.SeiStreamProfile
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiSlider
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.sei

@Composable
fun DeviceStreamingSection(
    options: RdpOptions,
    onOptionsChange: (RdpOptions) -> Unit,
    isPcQuickMode: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isPcQuickMode) {
            Text(
                sei("PC Stream Engine"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SeiPcStreamEngine.entries.forEach { engine ->
                SeiPillButton(
                    text = sei(engine.titleEn),
                    onClick = { onOptionsChange(options.withPcStreamEngine(engine)) },
                    primary = options.pcStreamEngine == engine,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                sei("Target Framerate: {0} FPS", options.pcTargetFps),
                style = MaterialTheme.typography.titleSmall,
            )
            val fpsValues = listOf(30, 60, 90, 120)
            SeiSegmented(
                options = fpsValues.map { "$it FPS" },
                selectedIndex = fpsValues.indexOf(options.pcTargetFps).coerceAtLeast(1),
                onSelect = { onOptionsChange(options.copy(pcTargetFps = fpsValues[it])) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                sei("Bitrate Limit: {0} Mbps", options.pcBitrateMbps),
                style = MaterialTheme.typography.titleSmall,
            )
            val bitrates = listOf(10, 25, 50, 80)
            SeiSegmented(
                options = bitrates.map { "$it Mbps" },
                selectedIndex = bitrates.indexOf(options.pcBitrateMbps).coerceAtLeast(1),
                onSelect = { onOptionsChange(options.copy(pcBitrateMbps = bitrates[it])) },
                modifier = Modifier.fillMaxWidth(),
            )

            SeiToggleRow(
                title = sei("Raw mouse input"),
                subtitle = sei("Absolute mouse position without software acceleration"),
                checked = options.rawInputMouse,
                onCheckedChange = { onOptionsChange(options.copy(rawInputMouse = it)) },
                leading = SeiIcons.Gamepad,
            )
        } else {
            Text(
                sei("Streaming & Latency Profile"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val streamPresets = listOf(
                SeiStreamProfile.AUTO,
                SeiStreamProfile.BALANCED,
                SeiStreamProfile.ULTRA_LOW_LATENCY,
                SeiStreamProfile.HIGH_VISUAL,
                SeiStreamProfile.DATA_SAVER,
            )
            streamPresets.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { profile ->
                        SeiPillButton(
                            text = sei(profile.titleEn),
                            onClick = { onOptionsChange(options.withStreamProfile(profile)) },
                            primary = options.streamProfile == profile,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            SeiToggleRow(
                title = sei("UDP Transport (RDP-UDP / RDP8)"),
                subtitle = sei("Reduces latency and stutter over packet loss; falls back to TCP"),
                checked = options.udpTransport,
                onCheckedChange = {
                    onOptionsChange(
                        options.copy(
                            udpTransport = it,
                            streamProfile = SeiStreamProfile.CUSTOM,
                        ),
                    )
                },
                leading = SeiIcons.Wifi,
            )

            SeiToggleRow(
                title = sei("Network autodetect"),
                subtitle = sei("Continuous RTT and bandwidth probing to dynamically adjust compression"),
                checked = options.networkAutoDetect,
                onCheckedChange = {
                    onOptionsChange(
                        options.copy(
                            networkAutoDetect = it,
                            streamProfile = SeiStreamProfile.CUSTOM,
                        ),
                    )
                },
                leading = SeiIcons.Zap,
            )

            SeiToggleRow(
                title = sei("H.264 AVC Hardware Codec"),
                subtitle = sei("Uses phone GPU hardware decoder for high-framerate video"),
                checked = options.h264,
                onCheckedChange = {
                    onOptionsChange(
                        options.copy(
                            h264 = it,
                            streamProfile = SeiStreamProfile.CUSTOM,
                        ),
                    )
                },
                leading = SeiIcons.Play,
            )

            SeiToggleRow(
                title = sei("Async frame update"),
                subtitle = sei("Decouples graphics decoding from network I/O loop"),
                checked = options.asyncUpdate,
                onCheckedChange = {
                    onOptionsChange(
                        options.copy(
                            asyncUpdate = it,
                            streamProfile = SeiStreamProfile.CUSTOM,
                        ),
                    )
                },
                leading = SeiIcons.Sliders,
            )

            SeiToggleRow(
                title = sei("Async virtual channels"),
                subtitle = sei("Independent background threads for clipboard, audio, and device redirection"),
                checked = options.asyncChannels,
                onCheckedChange = {
                    onOptionsChange(
                        options.copy(
                            asyncChannels = it,
                            streamProfile = SeiStreamProfile.CUSTOM,
                        ),
                    )
                },
                leading = SeiIcons.Sliders,
            )

            Text(
                sei("Color depth"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val depthValues = listOf(32, 24, 16)
            SeiSegmented(
                options = listOf(sei("32-bit (True Color)"), sei("24-bit"), sei("16-bit (Faster)")),
                selectedIndex = depthValues.indexOf(options.colorDepth).coerceAtLeast(0),
                onSelect = {
                    onOptionsChange(
                        options.copy(
                            colorDepth = depthValues[it],
                            streamProfile = SeiStreamProfile.CUSTOM,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
