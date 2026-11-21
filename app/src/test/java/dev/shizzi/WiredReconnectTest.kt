package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WiredReconnectTest {

    @Test
    fun `does nothing while usb stays tethered`() {
        val reconnect = WiredReconnect("usb tethering")

        assertFalse(reconnect.shouldRestart(isTethered = true, isCableConnected = true, now = 0))
        assertFalse(reconnect.isWaiting)
    }

    @Test
    fun `waits without restarting while the cable is out`() {
        val reconnect = WiredReconnect("usb tethering")

        assertFalse(reconnect.shouldRestart(isTethered = false, isCableConnected = false, now = 0))
        assertTrue(reconnect.isWaiting)
    }

    @Test
    fun `restarts once the cable returns`() {
        val reconnect = WiredReconnect("usb tethering")
        reconnect.shouldRestart(isTethered = false, isCableConnected = false, now = 0)

        assertTrue(reconnect.shouldRestart(isTethered = false, isCableConnected = true, now = 5_000))
    }

    @Test
    fun `spaces out retries while tethering comes up`() {
        val reconnect = WiredReconnect("usb tethering")

        assertTrue(reconnect.shouldRestart(isTethered = false, isCableConnected = true, now = 0))
        assertFalse(reconnect.shouldRestart(isTethered = false, isCableConnected = true, now = 5_000))
        assertTrue(reconnect.shouldRestart(isTethered = false, isCableConnected = true, now = 15_000))
    }

    @Test
    fun `stops waiting once usb is tethered again`() {
        val events = mutableListOf<String>()
        val reconnect = WiredReconnect("usb tethering") { events += it }

        reconnect.shouldRestart(isTethered = false, isCableConnected = true, now = 0)
        reconnect.shouldRestart(isTethered = true, isCableConnected = true, now = 5_000)

        assertFalse(reconnect.isWaiting)
        assertEquals("usb tethering back up", events.last())
    }

    @Test
    fun `gives up after waiting too long for the cable`() {
        val reconnect = WiredReconnect("usb tethering")
        reconnect.shouldRestart(isTethered = false, isCableConnected = false, now = 1_000)

        assertFalse(reconnect.hasGivenUp(now = WiredReconnect.GIVE_UP_AFTER_MS))
        assertTrue(reconnect.hasGivenUp(now = 1_000 + WiredReconnect.GIVE_UP_AFTER_MS))
    }

    @Test
    fun `retries do not reset the deadline`() {
        val reconnect = WiredReconnect("usb tethering")
        reconnect.shouldRestart(isTethered = false, isCableConnected = true, now = 0)
        reconnect.shouldRestart(isTethered = false, isCableConnected = true, now = 60_000)

        assertTrue(reconnect.hasGivenUp(now = WiredReconnect.GIVE_UP_AFTER_MS))
    }

    @Test
    fun `reconnecting clears the deadline`() {
        val reconnect = WiredReconnect("usb tethering")
        reconnect.shouldRestart(isTethered = false, isCableConnected = false, now = 0)
        reconnect.shouldRestart(isTethered = true, isCableConnected = true, now = 5_000)

        assertFalse(reconnect.hasGivenUp(now = WiredReconnect.GIVE_UP_AFTER_MS * 2))
    }

    @Test
    fun `never gives up while usb is tethered`() {
        val reconnect = WiredReconnect("usb tethering")
        reconnect.shouldRestart(isTethered = true, isCableConnected = true, now = 0)

        assertFalse(reconnect.hasGivenUp(now = WiredReconnect.GIVE_UP_AFTER_MS))
    }
}
