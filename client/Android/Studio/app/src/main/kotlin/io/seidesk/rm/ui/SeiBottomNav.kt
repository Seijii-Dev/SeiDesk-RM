package io.seidesk.rm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.seidesk.rm.ui.components.SeiIcons
import io.seidesk.rm.ui.components.seiSurface

/**
 * Tujuan navigasi bawah.
 *
 * Sengaja hanya **tiga** sesuai permintaan pengguna. Sebelumnya bar ini memuat
 * enam seksi (`SeiSection`) sehingga penuh dan tiap item jadi sempit; seksi
 * lain sekarang hidup di drawer kiri dan di layar Profil.
 */
internal enum class SeiTab(val title: String, val titleEn: String) {
    HOME("Beranda", "Home"),
    FEED("Feed", "Feed"),
    PROFILE("Profil", "Profile"),
}

/**
 * Navigasi bawah app: tiga tujuan, gaya permukaan monokrom.
 *
 * Warna tetap hitam-putih (`onSurface`/`surface`) alih-alih aksen, mengikuti
 * permintaan pengguna. Item terpilih digambar sebagai pil berisi; sisanya
 * redup. Perubahan warna dan ukuran pil dianimasikan supaya perpindahan tab
 * terasa halus, bukan melompat.
 */
@Composable
internal fun SeiBottomNav(
    selected: SeiTab,
    onSelect: (SeiTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val ink = MaterialTheme.colorScheme.onSurface

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .seiSurface(shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), strength = 0.8f)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                ),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val totalWidth = maxWidth
        val tabCount = SeiTab.entries.size
        val tabWidth = totalWidth / tabCount
        val indicatorWidth = 56.dp
        val indicatorHeight = 28.dp

        // Single animated sliding indicator pill offset
        val targetOffset = (tabWidth * selected.ordinal) + ((tabWidth - indicatorWidth) / 2)
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "slidingIndicatorOffset",
        )

        // Sliding indicator pill background
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .offset(x = animatedOffset)
                .size(width = indicatorWidth, height = indicatorHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    RoundedCornerShape(14.dp),
                ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeiTab.entries.forEach { tab ->
                val active = tab == selected
                val title = sei(tab.titleEn)

                val iconTint by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.45f),
                    animationSpec = tween(durationMillis = 180),
                    label = "navIconTint",
                )
                val textColor by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.45f),
                    animationSpec = tween(durationMillis = 180),
                    label = "navTextColor",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onSelect(tab)
                        })
                        .semantics { contentDescription = title }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tabIcon(tab),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Ikon per tujuan. Tiga saja, jadi tidak ada risiko dua seksi berbagi ikon. */
private fun tabIcon(tab: SeiTab): ImageVector = when (tab) {
    SeiTab.HOME -> SeiIcons.Monitor
    SeiTab.FEED -> SeiIcons.Users
    SeiTab.PROFILE -> SeiIcons.Gear
}

/**
 * Garis pemisah bergradien halus di atas bar.
 */
@Composable
internal fun SeiBottomNavHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

