package dev.shizzi.ui.theme

import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AccentPaletteTest {

    private val blue = 0xFF3B82F6.toInt()

    @Test
    fun `a custom seed keeps its hue in the generated primary`() {
        val scheme = schemeFor(AccentChoice.Custom(blue), isDark = false)
        val primary = scheme.roleArgb { primary() }

        val seedHue = Hct.fromInt(blue).hue
        val primaryHue = Hct.fromInt(primary).hue

        assertTrue(
            "expected a hue near $seedHue, was $primaryHue",
            hueGap(seedHue, primaryHue) < 40.0,
        )
    }

    @Test
    fun `a custom seed generates distinct light and dark primaries`() {
        val light = schemeFor(AccentChoice.Custom(blue), isDark = false)
        val dark = schemeFor(AccentChoice.Custom(blue), isDark = true)

        assertTrue(light.roleArgb { primary() } != dark.roleArgb { primary() })
    }

    @Test
    fun `light mode keeps brutalist edges pure black`() {
        assertEquals(0xFF000000.toInt(), brutalEdgeArgb(isDark = false))
    }

    @Test
    fun `dark mode keeps brutalist edges pure white`() {
        assertEquals(0xFFFFFFFF.toInt(), brutalEdgeArgb(isDark = true))
    }

    @Test
    fun `cards contrast with the background under a custom accent`() {
        listOf(false, true).forEach { isDark ->
            val scheme = schemeFor(AccentChoice.Custom(blue), isDark)

            assertTrue(
                "card surface matched the background in isDark=$isDark",
                scheme.roleArgb { surfaceContainerLow() } != scheme.roleArgb { background() },
            )
        }
    }

    private fun hueGap(first: Double, second: Double): Double {
        val gap = abs(first - second) % 360.0

        return if (gap > 180.0) 360.0 - gap else gap
    }
}
