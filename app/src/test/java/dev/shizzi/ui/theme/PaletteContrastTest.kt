package dev.shizzi.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PaletteContrastTest {

    private val minimumRatio = 2.0

    @Test
    fun `the muted foreground reads against the background in both modes`() {
        listOf(LightColors, DarkColors).forEach { palette ->
            val dot = blend(palette.onSurfaceMuted, InactiveAlpha, palette.background)
            val ratio = contrastRatio(dot, palette.background)

            assertTrue(
                "inactive dot contrast was $ratio in isDark=${palette.isDark}",
                ratio > minimumRatio,
            )
        }
    }

    @Test
    fun `the container roles alone cannot carry a dot on the background`() {
        listOf(LightColors, DarkColors).forEach { palette ->
            assertTrue(
                "surfaceContainer unexpectedly contrasts in isDark=${palette.isDark}",
                contrastRatio(palette.surfaceContainer, palette.background) < 1.2,
            )
        }
    }

    private fun blend(
        foreground: androidx.compose.ui.graphics.Color,
        alpha: Float,
        background: androidx.compose.ui.graphics.Color,
    ) = Triple(
        foreground.red * alpha + background.red * (1 - alpha),
        foreground.green * alpha + background.green * (1 - alpha),
        foreground.blue * alpha + background.blue * (1 - alpha),
    )

    private fun contrastRatio(
        color: androidx.compose.ui.graphics.Color,
        background: androidx.compose.ui.graphics.Color,
    ) = contrastRatio(Triple(color.red, color.green, color.blue), background)

    private fun contrastRatio(
        color: Triple<Float, Float, Float>,
        background: androidx.compose.ui.graphics.Color,
    ): Double {
        val first = relativeLuminance(color)
        val second = relativeLuminance(
            Triple(background.red, background.green, background.blue),
        )
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)

        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Triple<Float, Float, Float>): Double {
        val (red, green, blue) = color

        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun channel(value: Float): Double {
        val raw = value.toDouble()

        return if (raw <= 0.03928) raw / 12.92 else ((raw + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        const val InactiveAlpha = 0.6f
    }
}
