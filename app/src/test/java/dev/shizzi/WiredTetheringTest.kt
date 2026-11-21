package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WiredTetheringTest {

    @Test
    fun `a retired usb setting keeps its behavior`() {
        assertEquals(SharingSetup(WiredTethering.OFF, isWifiHotspotChosen = true), UsbTethering.NEVER.toSharingSetup())
        assertEquals(SharingSetup(WiredTethering.USB, isWifiHotspotChosen = true), UsbTethering.AUTO.toSharingSetup())
        assertEquals(SharingSetup(WiredTethering.USB, isWifiHotspotChosen = false), UsbTethering.ALWAYS.toSharingSetup())
    }

    @Test
    fun `the hotspot stays on while wired tethering is off`() {
        assertTrue(SharingSetup(WiredTethering.OFF, isWifiHotspotChosen = false).isWifiHotspotOn)
        assertFalse(SharingSetup(WiredTethering.ETHERNET, isWifiHotspotChosen = false).isWifiHotspotOn)
    }

    @Test
    fun `lists every downstream a setup may bring up`() {
        assertEquals(
            listOf(TetheringType.WIFI, TetheringType.USB, TetheringType.ETHERNET),
            SharingSetup(WiredTethering.AUTO, isWifiHotspotChosen = true).types,
        )
        assertEquals(listOf(TetheringType.ETHERNET), SharingSetup(WiredTethering.ETHERNET, isWifiHotspotChosen = false).types)
    }

    @Test
    fun `an unknown stored value reads as auto`() {
        assertEquals(WiredTethering.AUTO, parseWiredTethering(null))
        assertEquals(WiredTethering.AUTO, parseWiredTethering("SOMETHING"))
        assertEquals(WiredTethering.ETHERNET, parseWiredTethering("ETHERNET"))
    }
}
