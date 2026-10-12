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
import io.seidesk.rm.ui.DisplayPrefs
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiSlider
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.sei

@Composable
fun DeviceDisplaySection(
    resolutionIndex: Int,
    onResolutionIndexChange: (Int) -> Unit,
    rotationIndex: Int,
    onRotationIndexChange: (Int) -> Unit,
    dpi: Int,
    onDpiChange: (Int) -> Unit,
    options: RdpOptions,
    onOptionsChange: (RdpOptions) -> Unit,
    isPcQuickMode: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isPcQuickMode) {
            Text(
                sei("Remote desktop resolution"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                sei(
                    "The \"Automatic\" default is always 16:9 (right for Windows desktops). \"Follow phone screen\" uses the phone ratio — for video/games.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DisplayPrefs.resolutionOptions.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { colIndex, option ->
                            val index = rowIndex * 2 + colIndex
                            SeiPillButton(
                                text = sei(option.en),
                                onClick = { onResolutionIndexChange(index) },
                                primary = index == resolutionIndex,
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SeiToggleRow(
                title = sei("Dynamic resolution (Smart sizing)"),
                subtitle = sei("Automatically resize remote desktop when phone rotates or window changes size"),
                checked = options.dynamicResolution,
                onCheckedChange = { onOptionsChange(options.copy(dynamicResolution = it)) },
            )
        }

        Text(
            sei("Device orientation"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SeiSegmented(
            options = DisplayPrefs.rotations,
            selectedIndex = rotationIndex,
            onSelect = onRotationIndexChange,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!isPcQuickMode) {
            Text(
                sei("Initial local zoom: {0}%", dpi),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SeiSlider(
                value = dpi.toFloat(),
                onValueChange = { onDpiChange(it.toInt()) },
                valueRange = 80f..200f,
                steps = 11,
            )
            Text(
                sei(
                    "This local zoom is the starting value when a session opens. Windows desktop DPI is controlled separately in the session panel.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
