package io.seidesk.rm.ui

import androidx.compose.ui.graphics.Color
import io.seidesk.rm.ui.theme.SeiColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SeiContrastTest {

    private fun linearize(channel: Float): Double {
        return if (channel <= 0.04045f) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun luminance(color: Color): Double {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun darkThemeContrastMeetsWcagAa() {
        // Text on DarkSurface
        val textOnSurface = contrastRatio(SeiColors.DarkText, SeiColors.DarkSurface)
        assertTrue("DarkText on DarkSurface should be >= 4.5:1, got $textOnSurface", textOnSurface >= 4.5)

        // Dim text on DarkSurface
        val textDimOnSurface = contrastRatio(SeiColors.DarkTextDim, SeiColors.DarkSurface)
        assertTrue("DarkTextDim on DarkSurface should be >= 4.5:1, got $textDimOnSurface", textDimOnSurface >= 4.5)

        // Faint text on DarkSurface (re-tuned to #8494AB)
        val textFaintOnSurface = contrastRatio(SeiColors.DarkTextFaint, SeiColors.DarkSurface)
        assertTrue("DarkTextFaint on DarkSurface should be >= 4.5:1, got $textFaintOnSurface", textFaintOnSurface >= 4.5)

        // Accent as text on DarkSurface (re-tuned to #818CF8)
        val accentTextOnSurface = contrastRatio(SeiColors.DarkAccentText, SeiColors.DarkSurface)
        assertTrue("DarkAccentText on DarkSurface should be >= 4.5:1, got $accentTextOnSurface", accentTextOnSurface >= 4.5)

        // White on primary button (#5558E6)
        val whiteOnButton = contrastRatio(Color.White, SeiColors.DarkPrimaryButton)
        assertTrue("White on DarkPrimaryButton should be >= 4.5:1, got $whiteOnButton", whiteOnButton >= 4.5)
    }

    @Test
    fun lightThemeContrastMeetsWcagAa() {
        // Text on LightSurface
        val textOnSurface = contrastRatio(SeiColors.LightText, SeiColors.LightSurface)
        assertTrue("LightText on LightSurface should be >= 4.5:1, got $textOnSurface", textOnSurface >= 4.5)

        // Dim text on LightSurface
        val textDimOnSurface = contrastRatio(SeiColors.LightTextDim, SeiColors.LightSurface)
        assertTrue("LightTextDim on LightSurface should be >= 4.5:1, got $textDimOnSurface", textDimOnSurface >= 4.5)

        // Faint text on LightSurface (#64748B)
        val textFaintOnSurface = contrastRatio(SeiColors.LightTextFaint, SeiColors.LightSurface)
        assertTrue("LightTextFaint on LightSurface should be >= 4.5:1, got $textFaintOnSurface", textFaintOnSurface >= 4.5)

        // White on primary button (#4F46E5)
        val whiteOnButton = contrastRatio(Color.White, SeiColors.LightPrimaryButton)
        assertTrue("White on LightPrimaryButton should be >= 4.5:1, got $whiteOnButton", whiteOnButton >= 4.5)
    }
}
