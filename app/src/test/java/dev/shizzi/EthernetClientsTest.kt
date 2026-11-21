package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class EthernetClientsTest {

    @Test
    fun `reads resolved, failed, and router entries`() {
        val table = """
            10.72.57.44 lladdr e8:6a:64:0f:a0:c7 REACHABLE
            10.72.57.45  FAILED
            fe80::f864:ce06:a7a0:bcbe lladdr e8:6a:64:0f:a0:c7 router STALE
        """.trimIndent()

        assertEquals(
            listOf(
                Neighbor("10.72.57.44", "e8:6a:64:0f:a0:c7", "REACHABLE"),
                Neighbor("10.72.57.45", null, "FAILED"),
                Neighbor("fe80::f864:ce06:a7a0:bcbe", "e8:6a:64:0f:a0:c7", "STALE"),
            ),
            parseNeighbors(table),
        )
    }

    @Test
    fun `counts each device once across its addresses`() {
        val neighbors = listOf(
            Neighbor("10.72.57.44", "e8:6a:64:0f:a0:c7", "REACHABLE"),
            Neighbor("fe80::1", "e8:6a:64:0f:a0:c7", "STALE"),
            Neighbor("10.72.57.50", "aa:bb:cc:dd:ee:ff", "DELAY"),
        )

        assertEquals(2, countPresentDevices(neighbors))
    }

    @Test
    fun `a device the kernel gave up on is gone`() {
        val neighbors = listOf(
            Neighbor("10.72.57.44", null, "FAILED"),
            Neighbor("10.72.57.46", "aa:bb:cc:dd:ee:01", "INCOMPLETE"),
        )

        assertEquals(0, countPresentDevices(neighbors))
    }

    @Test
    fun `probes only stale entries`() {
        val stale = Neighbor("10.72.57.44", "e8:6a:64:0f:a0:c7", "STALE")
        val neighbors = listOf(stale, Neighbor("10.72.57.50", "aa:bb:cc:dd:ee:ff", "REACHABLE"))

        assertEquals(listOf(stale), neighborsToProbe(neighbors))
    }

    @Test
    fun `an empty table counts nobody`() {
        assertEquals(0, countPresentDevices(parseNeighbors("")))
    }
}
