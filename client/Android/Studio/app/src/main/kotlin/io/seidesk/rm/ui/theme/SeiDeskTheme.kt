package io.seidesk.rm.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.seidesk.rm.R
import io.seidesk.rm.ui.AppPrefs

// =============================================================
// SeiDesk design tokens.
// Modern, sleek cyber-aesthetic with electric indigo accents,
// high-contrast obsidian dark surfaces, and vivid semantic indicators.
// =============================================================

// Dark theme tokens mapped from SeiColors
val SeiBg = SeiColors.DarkBg
val SeiSurface = SeiColors.DarkSurface
val SeiSurfaceAlt = SeiColors.DarkSurfaceAlt
val SeiPrimaryContainer = SeiColors.DarkSurfaceRaised
val SeiLine = SeiColors.DarkLine
val SeiLineStrong = SeiColors.DarkLineStrong
val SeiText = SeiColors.DarkText
val SeiTextDim = SeiColors.DarkTextDim
val SeiTextFaint = SeiColors.DarkTextFaint

// Accent & Status
val SeiAccent = SeiColors.DarkAccent
val SeiAccentText = SeiColors.DarkAccentText
val XyAccentInk = Color(0xFFFFFFFF)

val SeiOk = SeiColors.StatusOk
val SeiWarn = SeiColors.StatusWarn
val SeiDanger = SeiColors.StatusDanger

val DarkScheme = darkColorScheme(
    primary = SeiColors.DarkPrimaryButton,
    onPrimary = Color.White,
    primaryContainer = SeiColors.DarkSurfaceRaised,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF03223A),
    secondaryContainer = Color(0xFF16253B),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = SeiOk,
    background = SeiBg,
    onBackground = SeiText,
    surface = SeiSurface,
    onSurface = SeiText,
    surfaceVariant = SeiSurfaceAlt,
    onSurfaceVariant = SeiTextDim,
    outline = SeiLine,
    outlineVariant = SeiLineStrong,
    error = SeiDanger,
    onError = Color.White,
    errorContainer = Color(0xFF3E121B),
    onErrorContainer = Color(0xFFFFD1D8),
    scrim = Color(0x66000000),
)

val LightScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFF059669),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    scrim = Color(0x38000000),
)

// JetBrains Mono static fonts (SIL OFL 1.1).
val SeiFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
val SeiDisplay = SeiFont
val SeiBody = SeiFont

val SeiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SeiFont, fontSize = 32.sp, lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
        fontFeatureSettings = "calt 0",
    ),
    displayMedium = TextStyle(
        fontFamily = SeiFont, fontSize = 28.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
        fontFeatureSettings = "calt 0",
    ),
    displaySmall = TextStyle(
        fontFamily = SeiFont, fontSize = 26.sp, lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp,
        fontFeatureSettings = "calt 0",
    ),
    headlineLarge = TextStyle(
        fontFamily = SeiFont, fontSize = 24.sp, lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
        fontFeatureSettings = "calt 0",
    ),
    headlineMedium = TextStyle(
        fontFamily = SeiFont, fontSize = 22.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
        fontFeatureSettings = "calt 0",
    ),
    headlineSmall = TextStyle(
        fontFamily = SeiFont, fontSize = 19.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp,
        fontFeatureSettings = "calt 0",
    ),
    titleLarge = TextStyle(
        fontFamily = SeiFont, fontSize = 18.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
        fontFeatureSettings = "calt 0",
    ),
    titleMedium = TextStyle(
        fontFamily = SeiFont, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
        fontFeatureSettings = "calt 0",
    ),
    titleSmall = TextStyle(
        fontFamily = SeiFont, fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp,
        fontFeatureSettings = "calt 0",
    ),
    bodyLarge = TextStyle(
        fontFamily = SeiFont, fontSize = 15.sp, lineHeight = 23.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
        fontFeatureSettings = "calt 0",
    ),
    bodyMedium = TextStyle(
        fontFamily = SeiFont, fontSize = 14.sp, lineHeight = 21.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
        fontFeatureSettings = "calt 0",
    ),
    bodySmall = TextStyle(
        fontFamily = SeiFont, fontSize = 12.5.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
        fontFeatureSettings = "calt 0",
    ),
    labelLarge = TextStyle(
        fontFamily = SeiFont, fontSize = 13.5.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp,
        fontFeatureSettings = "calt 0",
    ),
    labelMedium = TextStyle(
        fontFamily = SeiFont, fontSize = 12.sp, lineHeight = 16.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp,
        fontFeatureSettings = "calt 0",
    ),
    labelSmall = TextStyle(
        fontFamily = SeiFont, fontSize = 11.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp,
        fontFeatureSettings = "calt 0",
    ),
)

/** Radius lebih ramah sentuh; kontrol pill tetap terbaca sebagai kontrol. */
val SeiPill = RoundedCornerShape(18.dp)

val SeiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * Mode tema sebagai state Compose yang di-share lintas activity.
 */
object SeiThemeState {
    const val FOLLOW_SYSTEM = 0
    const val DARK = 1
    const val LIGHT = 2

    var mode by mutableIntStateOf(Int.MIN_VALUE)
        private set

    fun init(prefs: AppPrefs) {
        if (mode == Int.MIN_VALUE) mode = prefs.themeMode
    }

    fun set(prefs: AppPrefs, value: Int) {
        mode = value
        prefs.themeMode = value
    }
}

@Composable
fun seiDark(): Boolean = when (SeiThemeState.mode) {
    SeiThemeState.DARK -> true
    SeiThemeState.LIGHT -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun SeiDeskTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.decorView.setBackgroundColor(scheme.background.toArgb())
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !dark
            insets.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = SeiShapes,
        typography = SeiTypography,
        content = {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                content()
            }
        },
    )
}

@Composable
fun XyDeskTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) = SeiDeskTheme(dark = dark, content = content)
