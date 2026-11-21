package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatusTest {

    private fun stateFrom(status: String) =
        SessionUiState().applyOutcome(Result.success(status))

    @Test
    fun `a bypassed vpn is carried across the service boundary`() {
        val state = stateFrom("""{"state":"ACTIVE","isVpnBypassed":true}""")

        assertTrue(state.isVpnBypassed)
        assertFalse(state.isVpnBound)
    }

    @Test
    fun `a bound vpn is not reported as bypassed`() {
        val state = stateFrom("""{"state":"ACTIVE","isVpnBound":true}""")

        assertTrue(state.isVpnBound)
        assertFalse(state.isVpnBypassed)
    }

    @Test
    fun `an absent bypass flag reads as false`() {
        assertFalse(stateFrom("""{"state":"ACTIVE"}""").isVpnBypassed)
    }

    @Test
    fun `stopping clears the bypass flag`() {
        val bypassed = stateFrom("""{"state":"ACTIVE","isVpnBypassed":true}""")

        assertFalse(bypassed.asStopped().isVpnBypassed)
    }

    @Test
    fun `a failed start clears the bypass flag`() {
        val bypassed = stateFrom("""{"state":"ACTIVE","isVpnBypassed":true}""")

        val failed = bypassed.applyOutcome(Result.failure(IllegalStateException("boom")))

        assertFalse(failed.isVpnBypassed)
    }

    @Test
    fun `waiting for usb is carried across the service boundary`() {
        assertTrue(stateFrom("""{"state":"ACTIVE","isWaitingForWired":true}""").isWaitingForWired)
    }

    @Test
    fun `the downstreams a session started are carried across the service boundary`() {
        val state = stateFrom("""{"state":"ACTIVE","downstreams":["WIFI","USB"]}""")

        assertEquals(setOf(TetheringType.WIFI, TetheringType.USB), state.downstreams)
    }

    @Test
    fun `a status without downstreams reads as none started`() {
        val started = stateFrom("""{"state":"ACTIVE","downstreams":["WIFI"]}""")

        assertTrue(started.applyOutcome(Result.success("""{"state":"IDLE"}""")).downstreams.isEmpty())
    }

    @Test
    fun `waiting for shizuku counts as recovering, not as an error`() {
        val waiting = stateFrom("""{"state":"ACTIVE"}""").asWaitingForShizuku()

        assertTrue(waiting.isRecovering)
        assertTrue(waiting.lastError.isEmpty())
    }

    @Test
    fun `stopping ends recovery`() {
        val waiting = SessionUiState().asWaitingForShizuku()

        assertFalse(waiting.asStopped().isRecovering)
    }

    @Test
    fun `stopping clears waiting for usb`() {
        val waiting = stateFrom("""{"state":"ACTIVE","isWaitingForWired":true}""")

        assertFalse(waiting.asStopped().isWaitingForWired)
    }
}
