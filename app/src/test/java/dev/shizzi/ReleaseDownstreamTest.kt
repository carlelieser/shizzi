package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseDownstreamTest {

    private val tethered = "downstream still tethered: [ap_br_wlan2]"

    @Test
    fun `a downstream that releases on the first read needs one attempt`() {
        var stops = 0

        val problem = releaseDownstreamWith(
            stop = { stops++; true },
            findTethered = { null },
        )

        assertNull(problem)
        assertEquals(1, stops)
    }

    @Test
    fun `a downstream still up on the first read is stopped again`() {
        var stops = 0

        val problem = releaseDownstreamWith(
            stop = { stops++; true },
            findTethered = { if (stops < 2) tethered else null },
        )

        assertNull(problem)
        assertEquals(2, stops)
    }

    @Test
    fun `a downstream that never releases is reported`() {
        var stops = 0

        val problem = releaseDownstreamWith(
            stop = { stops++; true },
            findTethered = { tethered },
        )

        assertEquals(tethered, problem)
        assertEquals(DOWNSTREAM_STOP_ATTEMPTS, stops)
    }

    @Test
    fun `a rejected stop that leaves nothing tethered is still reported`() {
        val problem = releaseDownstreamWith(
            stop = { false },
            findTethered = { null },
        )

        assertNotNull(problem)
    }

    @Test
    fun `a later accepted stop clears an earlier rejection`() {
        var stops = 0

        val problem = releaseDownstreamWith(
            stop = { stops++; stops > 1 },
            findTethered = { if (stops < 2) tethered else null },
        )

        assertNull(problem)
    }

    @Test
    fun `each retry is announced once`() {
        val notices = mutableListOf<String>()

        releaseDownstreamWith(
            stop = { true },
            findTethered = { tethered },
            onRetry = { notices += it },
        )

        assertEquals(DOWNSTREAM_STOP_ATTEMPTS - 1, notices.size)
    }
}
