package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnWatchdogTest {

    @Test
    fun `adopts the vpn that is present at start`() {
        val watchdog = VpnWatchdog(StubLocator(handle = 42L)) { }

        assertEquals(42L, watchdog.adoptCurrentVpn())
    }

    @Test
    fun `reports no vpn when the locator finds none`() {
        val watchdog = VpnWatchdog(StubLocator(handle = UNBOUND)) { }

        assertEquals(UNBOUND, watchdog.adoptCurrentVpn())
    }

    @Test
    fun `adopts a vpn that appears after start`() {
        val locator = StubLocator(handle = UNBOUND)
        val watchdog = VpnWatchdog(locator) { }
        watchdog.adoptCurrentVpn()

        locator.handle = 7L

        assertEquals(VpnBinding.Adopted(7L), watchdog.evaluate())
    }

    @Test
    fun `re-pins when the vpn handle changes`() {
        val locator = StubLocator(handle = 7L)
        val watchdog = VpnWatchdog(locator) { }
        watchdog.adoptCurrentVpn()

        locator.handle = 9L

        assertEquals(VpnBinding.Adopted(9L), watchdog.evaluate())
    }

    @Test
    fun `a steady vpn produces no binding change`() {
        val watchdog = VpnWatchdog(StubLocator(handle = 7L)) { }
        watchdog.adoptCurrentVpn()

        assertNull(watchdog.evaluate())
    }

    @Test
    fun `survives a single miss before tearing down`() {
        val locator = StubLocator(handle = 7L)
        val watchdog = VpnWatchdog(locator) { }
        watchdog.adoptCurrentVpn()

        locator.handle = UNBOUND

        assertNull(watchdog.evaluate())
    }

    @Test
    fun `tears down once a bound vpn stays gone`() {
        val locator = StubLocator(handle = 7L)
        val watchdog = VpnWatchdog(locator) { }
        watchdog.adoptCurrentVpn()

        locator.handle = UNBOUND
        watchdog.evaluate()

        assertTrue(watchdog.evaluate() is VpnBinding.Lost)
    }

    @Test
    fun `a session that never bound a vpn is never torn down`() {
        val watchdog = VpnWatchdog(StubLocator(handle = UNBOUND)) { }
        watchdog.adoptCurrentVpn()

        repeat(MISSES_BEFORE_TEARDOWN + 1) { assertNull(watchdog.evaluate()) }
    }

    private class StubLocator(var handle: Long) : VpnLocator {

        override fun currentVpnHandle(): Long = handle
    }

    private companion object {

        const val UNBOUND = 0L
        const val MISSES_BEFORE_TEARDOWN = 2
    }
}
