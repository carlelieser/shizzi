package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WiredDownstreamTest {

    private class FakeLink(
        override val type: TetheringType,
        var isPlugged: Boolean? = false,
    ) : WiredLink {
        var startCount = 0

        override fun isConnected(): Boolean? = isPlugged

        override fun startTethering(): Pair<Boolean, String> {
            startCount++
            return true to "started"
        }
    }

    private val usb = FakeLink(TetheringType.USB)
    private val ethernet = FakeLink(TetheringType.ETHERNET)

    private fun downstream(wired: WiredTethering, isWifiHotspotOn: Boolean = true) =
        WiredDownstream(SharingSetup(wired, isWifiHotspotOn), listOf(usb, ethernet))

    private val nothingTethered: (TetheringType) -> DownstreamReading = { DownstreamReading.None }

    @Test
    fun `wired only refuses to start without its cable`() {
        assertEquals(WiredDownstream.NO_USB_CABLE, downstream(WiredTethering.USB, isWifiHotspotOn = false).startProblem())
        assertEquals(
            WiredDownstream.NO_ETHERNET,
            downstream(WiredTethering.ETHERNET, isWifiHotspotOn = false).startProblem(),
        )
        assertEquals(WiredDownstream.NO_WIRED, downstream(WiredTethering.AUTO, isWifiHotspotOn = false).startProblem())
    }

    @Test
    fun `wired only starts when the cable state cannot be read`() {
        usb.isPlugged = null

        assertNull(downstream(WiredTethering.USB, isWifiHotspotOn = false).startProblem())
    }

    @Test
    fun `auto without the hotspot starts over whichever cable is connected`() {
        ethernet.isPlugged = true
        val wired = downstream(WiredTethering.AUTO, isWifiHotspotOn = false)

        assertNull(wired.startProblem())
        assertEquals(listOf(TetheringType.ETHERNET), wired.typesToStart())
    }

    @Test
    fun `the hotspot starts without a cable`() {
        WiredTethering.entries.forEach { setting ->
            val wired = downstream(setting)

            assertNull(setting.name, wired.startProblem())
            assertEquals(setting.name, listOf(TetheringType.WIFI), wired.typesToStart())
        }
    }

    @Test
    fun `off cannot turn the hotspot off`() {
        assertEquals(listOf(TetheringType.WIFI), downstream(WiredTethering.OFF, isWifiHotspotOn = false).typesToStart())
    }

    @Test
    fun `starts the hotspot and every connected cable`() {
        usb.isPlugged = true
        ethernet.isPlugged = true

        assertEquals(
            listOf(TetheringType.WIFI, TetheringType.USB, TetheringType.ETHERNET),
            downstream(WiredTethering.AUTO).typesToStart(),
        )
        assertEquals(listOf(TetheringType.WIFI, TetheringType.ETHERNET), downstream(WiredTethering.ETHERNET).typesToStart())
    }

    @Test
    fun `wired only awaits a pulled cable before giving up`() {
        val wired = downstream(WiredTethering.ETHERNET, isWifiHotspotOn = false)

        assertEquals(DownstreamHealth.Awaiting, wired.check(0, nothingTethered))
        assertTrue(wired.isWaitingForWired)
        assertTrue(wired.check(WiredReconnect.GIVE_UP_AFTER_MS, nothingTethered) is DownstreamHealth.Ended)
    }

    @Test
    fun `the hotspot never ends the session over a missing cable`() {
        val wired = downstream(WiredTethering.AUTO)

        assertEquals(DownstreamHealth.Up, wired.check(0, nothingTethered))
        assertEquals(DownstreamHealth.Up, wired.check(WiredReconnect.GIVE_UP_AFTER_MS, nothingTethered))
        assertFalse(wired.isWaitingForWired)
    }

    @Test
    fun `auto keeps a wired-only session while either cable is tethered`() {
        val wired = downstream(WiredTethering.AUTO, isWifiHotspotOn = false)
        val usbTethered = { type: TetheringType ->
            if (type == TetheringType.USB) DownstreamReading.Tethered(listOf("ncm0")) else DownstreamReading.None
        }

        assertEquals(DownstreamHealth.Up, wired.check(WiredReconnect.GIVE_UP_AFTER_MS * 2, usbTethered))
        assertFalse(wired.isWaitingForWired)
    }

    @Test
    fun `restarts ethernet tethering once an adapter is connected`() {
        val wired = downstream(WiredTethering.ETHERNET)
        wired.check(0, nothingTethered)

        ethernet.isPlugged = true
        wired.check(5_000, nothingTethered)

        assertEquals(1, ethernet.startCount)
        assertEquals(0, usb.startCount)
    }

    @Test
    fun `auto switches to whichever cable comes back`() {
        val wired = downstream(WiredTethering.AUTO)
        wired.check(0, nothingTethered)

        usb.isPlugged = true
        wired.check(5_000, nothingTethered)

        assertEquals(1, usb.startCount)
        assertEquals(0, ethernet.startCount)
    }

    @Test
    fun `off leaves every cable alone`() {
        usb.isPlugged = true
        ethernet.isPlugged = true

        assertEquals(DownstreamHealth.Up, downstream(WiredTethering.OFF).check(0, nothingTethered))
        assertEquals(0, usb.startCount + ethernet.startCount)
    }

    @Test
    fun `an unreadable tethering state neither restarts nor gives up`() {
        usb.isPlugged = true
        val wired = downstream(WiredTethering.USB, isWifiHotspotOn = false)

        assertEquals(DownstreamHealth.Up, wired.check(WiredReconnect.GIVE_UP_AFTER_MS) { DownstreamReading.Unknown })
        assertEquals(0, usb.startCount)
    }

    @Test
    fun `does not read the cable while it is tethered`() {
        val link = object : WiredLink {
            override val type = TetheringType.USB
            override fun isConnected(): Boolean? = error("read the cable while tethered")
            override fun startTethering(): Pair<Boolean, String> = error("restarted while tethered")
        }
        val wired = WiredDownstream(SharingSetup(WiredTethering.USB, isWifiHotspotChosen = false), listOf(link))

        assertEquals(DownstreamHealth.Up, wired.check(0) { DownstreamReading.Tethered(listOf("ncm0")) })
    }

    @Test
    fun `only a session without the hotspot starts before a cable is tethered`() {
        assertTrue(downstream(WiredTethering.USB, isWifiHotspotOn = false).canStartUntethered)
        assertFalse(downstream(WiredTethering.USB).canStartUntethered)
        assertFalse(downstream(WiredTethering.OFF, isWifiHotspotOn = false).canStartUntethered)
    }

    @Test
    fun `wired only started before the cable is up waits for it`() {
        usb.isPlugged = true
        val wired = downstream(WiredTethering.USB, isWifiHotspotOn = false)

        assertEquals(DownstreamHealth.Awaiting, wired.check(0, nothingTethered))
        assertEquals(1, usb.startCount)
    }
}
