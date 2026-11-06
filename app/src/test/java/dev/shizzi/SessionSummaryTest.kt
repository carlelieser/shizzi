package dev.shizzi

import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {

    @Test
    fun `short sessions read in seconds`() {
        assertTrue(describeSession(42_000, Traffic()).contains("active 42s"))
    }

    @Test
    fun `sessions over a minute read in minutes and seconds`() {
        assertTrue(describeSession(3 * 60_000L + 5_000, Traffic()).contains("active 3m 5s"))
    }

    @Test
    fun `sessions over an hour drop the seconds`() {
        assertTrue(describeSession(2 * 3_600_000L + 7 * 60_000L + 9_000, Traffic()).contains("active 2h 7m"))
    }
}
