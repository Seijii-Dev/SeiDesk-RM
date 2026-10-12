package io.seidesk.rm.ui

import androidx.compose.runtime.Composable

/**
 * Backward compatibility delegation for [SeiFeedScreen].
 */
@Composable
internal fun XyFeedScreen(
    onMenu: () -> Unit,
) {
    SeiFeedScreen(onMenu = onMenu)
}
