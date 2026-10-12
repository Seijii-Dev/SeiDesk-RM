package io.seidesk.rm.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.seidesk.rm.core.RdpOptions
import io.seidesk.rm.core.WakeOnLan
import io.seidesk.rm.core.parseRdpEndpoint
import io.seidesk.rm.ui.components.SeiField
import io.seidesk.rm.ui.components.SeiPillButton
import io.seidesk.rm.ui.sei
import io.seidesk.rm.ui.seiNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeviceWolSection(
    host: String,
    options: RdpOptions,
    onOptionsChange: (RdpOptions) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wolStatusText by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            sei("Wake-on-LAN (Magic Packet)"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            sei("Wake the PC from Sleep/Shutdown over LAN or VPN before connecting via RDP."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SeiField(
            value = options.macAddress.orEmpty(),
            onValueChange = { onOptionsChange(options.copy(macAddress = it.trim().ifEmpty { null })) },
            label = sei("PC network card MAC Address (optional)"),
            hint = "AA:BB:CC:DD:EE:FF",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeiField(
                value = options.wolBroadcast,
                onValueChange = { onOptionsChange(options.copy(wolBroadcast = it.trim())) },
                label = sei("Broadcast / target IP"),
                hint = "255.255.255.255",
                modifier = Modifier.weight(1.4f),
            )
            SeiField(
                value = options.wolPort.toString(),
                onValueChange = {
                    val p = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 9
                    onOptionsChange(options.copy(wolPort = p.coerceIn(1, 65535)))
                },
                label = sei("UDP Port"),
                hint = "9",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(0.7f),
            )
        }

        if (!options.macAddress.isNullOrBlank()) {
            SeiPillButton(
                text = sei("Send Test Magic Packet Now"),
                onClick = {
                    val mac = options.macAddress.orEmpty()
                    if (WakeOnLan.parseMacBytes(mac) == null) {
                        wolStatusText = seiNow("Invalid MAC address (e.g. AA:BB:CC:DD:EE:FF)")
                    } else {
                        wolStatusText = seiNow("Sending Magic Packet...")
                        scope.launch {
                            val endpointHost = parseRdpEndpoint(host)?.host
                            val targets = buildList {
                                add(options.wolBroadcast.ifBlank { "255.255.255.255" })
                                if (!endpointHost.isNullOrBlank()) add(endpointHost)
                            }.distinct()
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    targets.forEach { target ->
                                        WakeOnLan.sendMagicPacket(mac, target, options.wolPort)
                                    }
                                }
                            }
                            wolStatusText = if (result.isSuccess) {
                                seiNow("Magic Packet sent to ${targets.joinToString()} (UDP ${options.wolPort})")
                            } else {
                                seiNow("Failed to send Magic Packet: ${result.exceptionOrNull()?.message ?: "error"}")
                            }
                        }
                    }
                },
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            wolStatusText?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            sei("SSH Jump-Host / Port Forwarding Helper"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            sei(
                "If the PC sits behind a firewall/NAT and is reached via a local SSH tunnel, save jump-host details here to generate the command.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SeiField(
            value = options.sshHost.orEmpty(),
            onValueChange = { onOptionsChange(options.copy(sshHost = it.trim().ifEmpty { null })) },
            label = sei("SSH Jump Host"),
            hint = "ssh.kantor.com",
        )

        if (!options.sshHost.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeiField(
                    value = options.sshUser.orEmpty(),
                    onValueChange = { onOptionsChange(options.copy(sshUser = it.trim().ifEmpty { null })) },
                    label = sei("SSH User"),
                    hint = "root",
                    modifier = Modifier.weight(1.3f),
                )
                SeiField(
                    value = options.sshPort.toString(),
                    onValueChange = {
                        val p = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 22
                        onOptionsChange(options.copy(sshPort = p.coerceIn(1, 65535)))
                    },
                    label = sei("SSH Port"),
                    hint = "22",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(0.7f),
                )
            }

            val parsedEndpoint = parseRdpEndpoint(host)
            val sshCmd = WakeOnLan.buildSshTunnelCommand(
                targetHost = parsedEndpoint?.host ?: "127.0.0.1",
                targetPort = parsedEndpoint?.port ?: 3389,
                options = options,
            )
            if (!sshCmd.isNullOrBlank()) {
                Text(
                    sshCmd,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SeiPillButton(
                    text = sei("Copy SSH Command"),
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("SSH Tunnel", sshCmd))
                        wolStatusText = seiNow("SSH command copied to clipboard.")
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
