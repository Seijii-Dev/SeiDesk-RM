package io.seidesk.rm.ui

import androidx.compose.runtime.Composable

/**
 * Backward compatibility delegation for [SeiProfileScreen].
 */
@Composable
internal fun XyProfileScreen(
    onMenu: () -> Unit,
    onOpenSection: (XySection) -> Unit,
    appPrefs: AppPrefs,
    deviceCount: Int,
    onShowLog: () -> Unit,
    onOpenFeedback: () -> Unit,
) {
    SeiProfileScreen(
        onMenu = onMenu,
        onOpenSection = onOpenSection,
        appPrefs = appPrefs,
        deviceCount = deviceCount,
        onShowLog = onShowLog,
        onOpenFeedback = onOpenFeedback,
    )
}
