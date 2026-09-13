package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnSelectionTest {

    private val physical = VpnCandidate(handle = 10L, isVpn = false, isActive = true)

    @Test
    fun `a vpn on the active network is selected`() {
        val vpn = VpnCandidate(handle = 20L, isVpn = true, isActive = true)

        assertEquals(20L, selectVpnHandle(listOf(vpn)))
    }

    @Test
    fun `a secure folder vpn beside an active physical network is ignored`() {
        val secureFolder = VpnCandidate(handle = 20L, isVpn = true, isActive = false)

        assertEquals(UNBOUND, selectVpnHandle(listOf(secureFolder, physical)))
    }

    @Test
    fun `a secure folder vpn listed before the active vpn is not preferred`() {
        val secureFolder = VpnCandidate(handle = 20L, isVpn = true, isActive = false)
        val active = VpnCandidate(handle = 30L, isVpn = true, isActive = true)

        assertEquals(30L, selectVpnHandle(listOf(secureFolder, active)))
    }

    @Test
    fun `no vpn is selected when only a physical network is up`() {
        assertEquals(UNBOUND, selectVpnHandle(listOf(physical)))
    }

    @Test
    fun `no vpn is selected when nothing is active`() {
        val idle = VpnCandidate(handle = 20L, isVpn = true, isActive = false)

        assertEquals(UNBOUND, selectVpnHandle(listOf(idle)))
    }

    @Test
    fun `no vpn is selected when there are no networks`() {
        assertEquals(UNBOUND, selectVpnHandle(emptyList()))
    }
}
