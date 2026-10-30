package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartedDownstreamsTest {

    @Test
    fun `nothing is stopped before anything started`() {
        assertTrue(StartedDownstreams().types.isEmpty())
    }

    @Test
    fun `holds each downstream once it starts`() {
        val started = StartedDownstreams()

        started.add(TetheringType.WIFI)
        started.add(TetheringType.USB)

        assertEquals(setOf(TetheringType.WIFI, TetheringType.USB), started.types)
    }

    @Test
    fun `a stopped session forgets its downstreams`() {
        val started = StartedDownstreams()
        started.add(TetheringType.USB)

        started.clear()

        assertTrue(started.types.isEmpty())
    }
}
