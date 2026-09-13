package dev.shizzi

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
}
