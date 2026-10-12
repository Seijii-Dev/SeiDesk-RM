package io.seidesk.rm.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * SeiDesk RM single source of truth for design tokens.
 * Implements matte policy, high WCAG AA contrast (>= 4.5:1),
 * tonal surface hierarchy, and unified spacing/radii/motion.
 */
object SeiColors {
    // Dark Theme (Obsidian tonal surfaces)
    val DarkBg = Color(0xFF0A0C10)
    val DarkSurface = Color(0xFF131722)
    val DarkSurfaceAlt = Color(0xFF1B2030)
    val DarkSurfaceRaised = Color(0xFF23293E)
    val DarkLine = Color(0xFF262C3D)
    val DarkLineStrong = Color(0xFF384259)
    val DarkText = Color(0xFFF8FAFC)
    val DarkTextDim = Color(0xFF94A3B8)
    val DarkTextFaint = Color(0xFF8494AB) // 5.8:1 on DarkSurface
    val DarkAccent = Color(0xFF6366F1)
    val DarkAccentText = Color(0xFF818CF8) // 6.0:1 on DarkSurface
    val DarkPrimaryButton = Color(0xFF5558E6) // 5.35:1 against white text

    // Light Theme
    val LightBg = Color(0xFFF8FAFC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceAlt = Color(0xFFF1F5F9)
    val LightSurfaceRaised = Color(0xFFEEF2FF)
    val LightLine = Color(0xFFE2E8F0)
    val LightLineStrong = Color(0xFFCBD5E1)
    val LightText = Color(0xFF0F172A)
    val LightTextDim = Color(0xFF475569)
    val LightTextFaint = Color(0xFF64748B) // 4.54:1 on LightSurface
    val LightAccent = Color(0xFF4F46E5)
    val LightAccentText = Color(0xFF4338CA)
    val LightPrimaryButton = Color(0xFF4F46E5)

    // Status Colors
    val StatusOk = Color(0xFF10B981)      // Emerald Green
    val StatusWarn = Color(0xFFF59E0B)    // Amber
    val StatusDanger = Color(0xFFF43F5E)  // Rose Red

    // Network connection types
    val NetLan = Color(0xFF10B981)
    val NetTailscale = Color(0xFF0284C7)
    val NetDirect = Color(0xFF8B5CF6)
    val NetPcE2ee = Color(0xFFEC4899)

    // Session HUD plates (matte translucent neutral dark)
    val HudPlateBg = Color(0xD910131B)
    val HudPlateStroke = Color(0x40384259)
    val HudText = Color(0xFFF8FAFC)
    val HudTextDim = Color(0xFF94A3B8)
}

object SeiSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
}

object SeiRadii {
    val chip = RoundedCornerShape(8.dp)
    val button = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(28.dp)
}

object SeiMotion {
    const val DurationFast = 120
    const val DurationNormal = 200
    const val DurationSlow = 320
    val EasingStandard: Easing = FastOutSlowInEasing
}

/**
 * Micro-interaction: tactile press scale animation.
 */
fun Modifier.pressScale(scale: Float = 0.97f): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) scale else 1f,
        animationSpec = tween(
            durationMillis = SeiMotion.DurationFast,
            easing = SeiMotion.EasingStandard,
        ),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}
