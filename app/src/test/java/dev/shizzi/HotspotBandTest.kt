package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotBandTest {

    @Test
    fun `adds 2_4 GHz to a 5 GHz only hotspot`() {
        assertEquals(BAND_2GHZ or BAND_5GHZ, hotspotBandFallback(BAND_5GHZ))
    }

    @Test
    fun `adds 2_4 GHz to a 6 GHz only hotspot`() {
        assertEquals(BAND_2GHZ or BAND_6GHZ, hotspotBandFallback(BAND_6GHZ))
    }

    @Test
    fun `keeps every saved band when adding 2_4 GHz`() {
        assertEquals(BAND_2GHZ or BAND_5GHZ or BAND_6GHZ, hotspotBandFallback(BAND_5GHZ or BAND_6GHZ))
    }

    @Test
    fun `has no fallback when 2_4 GHz is already allowed`() {
        listOf(BAND_2GHZ, BAND_2GHZ or BAND_5GHZ, BAND_2GHZ or BAND_5GHZ or BAND_6GHZ).forEach { band ->
            assertNull("band $band", hotspotBandFallback(band))
        }
    }

    private companion object {
        const val BAND_2GHZ = 1
        const val BAND_5GHZ = 2
        const val BAND_6GHZ = 4
    }
}
