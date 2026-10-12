package io.seidesk.rm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.seidesk.rm.ui.components.SeiGlassSectionLabel
import io.seidesk.rm.ui.components.SeiIconPill
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.SeiTopBar

@Composable
internal fun SeiFeedScreen(
    onMenu: () -> Unit,
) {
    var refreshTrigger by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SeiTopBar(
            title = sei("Feed"),
            actions = {
                SeiIconPill(
                    icon = SeiIcons.Rotate,
                    onClick = { refreshTrigger++ },
                    flat = true,
                    contentDescription = sei("Refresh"),
                )
                SeiIconPill(
                    icon = SeiIcons.Menu,
                    onClick = onMenu,
                    flat = true,
                    contentDescription = sei("Menu"),
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SeiGlassSectionLabel(sei("Community"))
            FunHubCard(
                onHide = null,
                refreshTrigger = refreshTrigger,
            )
        }
    }
}

