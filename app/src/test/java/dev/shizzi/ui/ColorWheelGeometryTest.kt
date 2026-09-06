package dev.shizzi.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorWheelGeometryTest {

    private val center = Offset(100f, 100f)
    private val radius = 100f

    @Test
    fun `the center maps to zero saturation`() {
        assertEquals(0f, hueSaturationAt(center, center, radius).saturation, 0.001f)
    }

    @Test
    fun `the edge maps to full saturation`() {
        val edge = Offset(center.x + radius, center.y)

        assertEquals(1f, hueSaturationAt(edge, center, radius).saturation, 0.001f)
    }

    @Test
    fun `an offset beyond the edge clamps to full saturation`() {
        val outside = Offset(center.x + (radius * 3f), center.y)

        assertEquals(1f, hueSaturationAt(outside, center, radius).saturation, 0.001f)
    }

    @Test
    fun `the cardinal directions map to the expected hues`() {
        assertEquals(0f, hueAt(Offset(center.x + radius, center.y)), 0.5f)
        assertEquals(90f, hueAt(Offset(center.x, center.y + radius)), 0.5f)
        assertEquals(180f, hueAt(Offset(center.x - radius, center.y)), 0.5f)
        assertEquals(270f, hueAt(Offset(center.x, center.y - radius)), 0.5f)
    }

    @Test
    fun `hue always lands inside a single turn`() {
        val corners = listOf(
            Offset(0f, 0f),
            Offset(200f, 0f),
            Offset(0f, 200f),
            Offset(200f, 200f),
        )

        corners.forEach { corner ->
            val hue = hueAt(corner)

            assertTrue("hue $hue out of range", hue >= 0f && hue < 360f)
        }
    }

    @Test
    fun `a position round-trips back to its hue and saturation`() {
        val selection = HueSaturation(hue = 210f, saturation = 0.6f)

        val restored = hueSaturationAt(positionOf(selection, center, radius), center, radius)

        assertEquals(selection.hue, restored.hue, 0.5f)
        assertEquals(selection.saturation, restored.saturation, 0.01f)
    }

    private fun hueAt(offset: Offset) = hueSaturationAt(offset, center, radius).hue
}
