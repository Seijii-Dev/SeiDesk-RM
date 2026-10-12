package io.seidesk.rm.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward compatibility delegation for [SeiBottomNav].
 */
@Composable
internal fun XyBottomNav(
    selected: XyTab,
    onSelect: (XyTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    SeiBottomNav(
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Backward compatibility delegation for [SeiBottomNavHairline].
 */
@Composable
internal fun XyBottomNavHairline(modifier: Modifier = Modifier) {
    SeiBottomNavHairline(modifier = modifier)
}
