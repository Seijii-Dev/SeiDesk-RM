package io.seidesk.rm.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.seidesk.rm.ui.components.SeiField
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiToggleRow
import io.seidesk.rm.ui.sei

@Composable
fun DeviceGatewaySection(
    gatewayOn: Boolean,
    onGatewayOnChange: (Boolean) -> Unit,
    gwHost: String,
    onGwHostChange: (String) -> Unit,
    gwPort: String,
    onGwPortChange: (String) -> Unit,
    gwUser: String,
    onGwUserChange: (String) -> Unit,
    gwPass: String,
    onGwPassChange: (String) -> Unit,
    gwDomain: String,
    onGwDomainChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SeiToggleRow(
            title = sei("Use RDP Gateway"),
            subtitle = sei("RD Gateway for office networks / firewalled environments without direct port access"),
            checked = gatewayOn,
            onCheckedChange = onGatewayOnChange,
            leading = SeiIcons.Lock,
        )

        if (gatewayOn) {
            SeiField(
                value = gwHost,
                onValueChange = onGwHostChange,
                label = sei("Gateway host"),
                hint = "gateway.example.com",
            )
            SeiField(
                value = gwPort,
                onValueChange = onGwPortChange,
                label = sei("Gateway port"),
                hint = "443",
                keyboardType = KeyboardType.Number,
            )
            SeiField(
                value = gwUser,
                onValueChange = onGwUserChange,
                label = sei("Gateway username (optional)"),
            )
            SeiField(
                value = gwPass,
                onValueChange = onGwPassChange,
                label = sei("Gateway password (optional)"),
                isPassword = true,
                keyboardType = KeyboardType.Password,
            )
            SeiField(
                value = gwDomain,
                onValueChange = onGwDomainChange,
                label = sei("Gateway domain (optional)"),
            )
        }
    }
}
