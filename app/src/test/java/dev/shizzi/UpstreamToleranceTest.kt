package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpstreamToleranceTest {

    private val expected = "testtun4"

    private fun tolerance() = UpstreamTolerance(expected) {}

    private fun classify(names: List<String>, didTimeout: Boolean = false) =
        classifyUpstream(names, expected, didTimeout)

    @Test
    fun `an empty upstream reads as absent rather than drifted`() {
        assertEquals(UpstreamReading.ABSENT, classify(emptyList()))
    }

    @Test
    fun `another live interface reads as drifted`() {
        assertEquals(UpstreamReading.DRIFTED, classify(listOf(expected, "rmnet_data2")))
    }

    @Test
    fun `the owned interface alone is healthy`() {
        assertEquals(UpstreamReading.HEALTHY, classify(listOf(expected)))
    }

    @Test
    fun `a timeout outranks an otherwise healthy reading`() {
        assertEquals(UpstreamReading.TIMED_OUT, classify(listOf(expected), didTimeout = true))
    }

    @Test
    fun `a brief absent upstream does not end the session`() {
        val guard = tolerance()

        repeat(3) {
            assertNull(guard.judge(UpstreamReading.ABSENT, emptyList()))
        }
    }

    @Test
    fun `an absent upstream that recovers clears its strikes`() {
        val guard = tolerance()

        repeat(5) { guard.judge(UpstreamReading.ABSENT, emptyList()) }
        assertNull(guard.judge(UpstreamReading.HEALTHY, listOf(expected)))

        repeat(5) {
            assertNull(guard.judge(UpstreamReading.ABSENT, emptyList()))
        }
    }

    @Test
    fun `a sustained absent upstream still ends the session`() {
        val guard = tolerance()
        val verdicts = (1..12).map { guard.judge(UpstreamReading.ABSENT, emptyList()) }

        assertNull(verdicts[10])
        assertNotNull(verdicts[11])
    }

    @Test
    fun `drift off the owned interface ends the session on the second strike`() {
        val guard = tolerance()
        val drifted = listOf("rmnet_data2")

        assertNull(guard.judge(UpstreamReading.DRIFTED, drifted))
        assertNotNull(guard.judge(UpstreamReading.DRIFTED, drifted))
    }

    @Test
    fun `drift is not excused by earlier absent readings`() {
        val guard = tolerance()
        val drifted = listOf("rmnet_data2")

        repeat(5) { guard.judge(UpstreamReading.ABSENT, emptyList()) }

        assertNull(guard.judge(UpstreamReading.DRIFTED, drifted))
        assertNotNull(guard.judge(UpstreamReading.DRIFTED, drifted))
    }
}
