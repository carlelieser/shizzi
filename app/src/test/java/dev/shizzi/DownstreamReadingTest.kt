package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownstreamReadingTest {

    private val dump = """
        Tethering:
          Tether state:
            ncm0 - TetheredState - lastError = 0
            wlan1 - TetheredState - lastError = 0
            eth0 - TetheredState - lastError = 0
            rmnet0 - AvailableState - lastError = 0
    """.trimIndent()

    private fun observed(output: String, didTimeout: Boolean = false) =
        UpstreamObservation(rawOutput = output, interfaceNames = emptyList(), didTimeout = didTimeout)

    @Test
    fun `a timed out read is unknown, not tethered`() {
        val reading = readDownstream(observed(dump, didTimeout = true), TetheringType.entries)

        assertEquals(DownstreamReading.Unknown, reading)
    }

    @Test
    fun `reports only the tethered interfaces of the asked types`() {
        val reading = readDownstream(observed(dump), listOf(TetheringType.USB))

        assertTrue(reading is DownstreamReading.Tethered)
        assertEquals(listOf("ncm0"), (reading as DownstreamReading.Tethered).names)
    }

    @Test
    fun `nothing tethered reads as none`() {
        val reading = readDownstream(observed("Tethering:\n  rmnet0 - AvailableState -"), TetheringType.entries)

        assertEquals(DownstreamReading.None, reading)
    }

    @Test
    fun `an ethernet tether is not read as the hotspot`() {
        val reading = readDownstream(observed(dump), listOf(TetheringType.WIFI))

        assertEquals(listOf("wlan1"), (reading as DownstreamReading.Tethered).names)
    }
}
