package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EthernetLinkTest {

    @Test
    fun `finds tracked and tethered ethernet interfaces`() {
        val dump = """
            Current Ethernet state:
              EthernetTracker
              Interface used for tethering: EthernetPort(eth0, ifindex=860, MAC=50:a0:30:0e:0d:65)
              EthernetNetworkFactory
              Tracking interfaces:
                eth0:NetworkInterfaceState{ port: EthernetPort(eth0, ifindex=860, MAC=50:a0:30:0e:0d:65), up: false }
                usb0:NetworkInterfaceState{ port: EthernetPort(usb0, ifindex=861, MAC=50:a0:30:0e:0d:66), up: true }
        """.trimIndent()

        assertEquals(listOf("eth0", "usb0"), parseEthernetInterfaces(dump))
    }

    @Test
    fun `no adapter means no interfaces`() {
        assertEquals(emptyList<String>(), parseEthernetInterfaces("Current Ethernet state:\n  Tracking interfaces:\n"))
    }

    @Test
    fun `reads the link from ip addr show`() {
        assertTrue(parseCarrier("860: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 state UP")!!)
        assertFalse(parseCarrier("860: eth0: <NO-CARRIER,BROADCAST,MULTICAST,UP> mtu 1500 state DOWN")!!)
        assertFalse(parseCarrier("[stderr]\nDevice \"eth0\" does not exist.")!!)
        assertNull(parseCarrier("request send failed: Permission denied"))
    }

    @Test
    fun `any adapter with a link counts as connected`() {
        assertEquals(true, combineCarriers(listOf(false, true)))
        assertEquals(false, combineCarriers(listOf(false, false)))
        assertEquals(false, combineCarriers(emptyList()))
        assertNull(combineCarriers(listOf(false, null)))
    }
}
