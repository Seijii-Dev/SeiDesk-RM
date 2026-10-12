package io.seidesk.rm.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.SeiSecurityProtocol
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.components.SeiSegmented
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.sei

@Composable
fun DeviceSecuritySection(
    options: RdpOptions,
    onOptionsChange: (RdpOptions) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            sei("Authentication security protocol"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SeiSecurityProtocol.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { proto ->
                    SeiPillButton(
                        text = sei(proto.titleEn),
                        onClick = { onOptionsChange(options.copy(securityProtocol = proto)) },
                        primary = options.securityProtocol == proto,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            sei("OpenSSL TLS Security Level"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val tlsValues = listOf(-1, 0, 1, 2)
        SeiSegmented(
            options = listOf(
                sei("Auto"),
                sei("Legacy (0)"),
                sei("Standard (1)"),
                sei("Strict (2)"),
            ),
            selectedIndex = tlsValues.indexOf(options.tlsSecLevel).coerceAtLeast(0),
            onSelect = { onOptionsChange(options.copy(tlsSecLevel = tlsValues[it])) },
            modifier = Modifier.fillMaxWidth(),
        )

        SeiToggleRow(
            title = sei("Console / Admin Session (/admin)"),
            subtitle = sei("Connect to the physical console or Windows Server administrator session"),
            checked = options.consoleAdmin,
            onCheckedChange = { onOptionsChange(options.copy(consoleAdmin = it)) },
            leading = SeiIcons.Lock,
        )

        SeiToggleRow(
            title = sei("Restricted Admin Mode (/restricted-admin)"),
            subtitle = sei("Authenticate via NLA without sending plaintext credentials to remote host"),
            checked = options.restrictedAdmin,
            onCheckedChange = { onOptionsChange(options.copy(restrictedAdmin = it)) },
            leading = SeiIcons.Lock,
        )
    }
}
