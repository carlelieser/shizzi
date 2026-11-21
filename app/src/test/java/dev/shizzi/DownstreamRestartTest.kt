package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownstreamRestartTest {

    private val configuration = """
        Tethering:
          Configuration:
            tetherableUsbRegexs: [ncm\d]
            tetherableWifiRegexs: [wlan\d, softap\d, ap_br_wlan\d, ap_br_softap\d]
            tetherableWifiP2pRegexs: [p2p-p2p\d-.*, p2p\d, p2p-wlan\d-.*]
            tetherableBluetoothRegexs: [bt-pan]
            tetherableNcmRegexs: []
          Tether state:
    """.trimIndent()

    private fun dump(vararg tethered: String): String =
        configuration + tethered.joinToString("") { name -> "\n    $name - TetheredState - lastError = 0" }

    @Test
    fun `matches tethered interfaces against the device's own regexes`() {
        val active = activeDownstreamTypes(dump("ncm0", "wlan1", "bt-pan"))

        assertEquals(setOf(TetheringType.USB, TetheringType.WIFI, TetheringType.BLUETOOTH), active)
    }

    @Test
    fun `an interface no regex claims is ethernet, even one named like usb`() {
        assertEquals(setOf(TetheringType.ETHERNET), activeDownstreamTypes(dump("eth0", "usb0")))
    }

    @Test
    fun `wifi direct groups are not downstreams to stop`() {
        assertEquals(emptySet<TetheringType>(), activeDownstreamTypes(dump("p2p-wlan0-0")))
    }

    @Test
    fun `a dump without tethering configuration cannot be read`() {
        assertNull(activeDownstreamTypes("Tethering:\n  Tether state:\n    eth0 - TetheredState - lastError = 0"))
    }

    @Test
    fun `stops and restores downstreams the session does not start`() {
        val restart = planDownstreamRestart(
            types = listOf(TetheringType.WIFI),
            active = setOf(TetheringType.ETHERNET),
            usbReading = DownstreamReading.None,
        )

        assertEquals(listOf(TetheringType.WIFI, TetheringType.ETHERNET), restart.toStop)
        assertEquals(listOf(TetheringType.ETHERNET), restart.toRestore)
    }

    @Test
    fun `restores nothing the session starts itself`() {
        val restart = planDownstreamRestart(
            types = listOf(TetheringType.WIFI, TetheringType.USB),
            active = setOf(TetheringType.WIFI, TetheringType.USB),
            usbReading = DownstreamReading.Tethered(listOf("ncm0")),
        )

        assertEquals(listOf(TetheringType.WIFI, TetheringType.USB), restart.toStop)
        assertEquals(emptyList<TetheringType>(), restart.toRestore)
    }

    @Test
    fun `still leaves idle usb alone`() {
        val restart = planDownstreamRestart(
            types = listOf(TetheringType.WIFI, TetheringType.USB),
            active = emptySet(),
            usbReading = DownstreamReading.None,
        )

        assertEquals(listOf(TetheringType.WIFI), restart.toStop)
    }
}
