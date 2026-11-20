package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRecoveryTest {

    private val hotspot = setOf(TetheringType.WIFI)

    private fun lost(atMs: Long = 0, isShizukuReady: Boolean = true) =
        RecoveryEvent.HelperLost(atMs, isShizukuReady, hotspot)

    private fun failed(atMs: Long = 0, isShizukuReady: Boolean = true) =
        RecoveryEvent.RestartFailed(atMs, isShizukuReady)

    @Test
    fun `restarts a session whose helper died while shizuku runs`() {
        val action = SessionRecovery().next(lost())

        assertEquals(RecoveryAction.Restart(afterMs = 2_000), action)
    }

    @Test
    fun `backs off between failed restarts`() {
        val recovery = SessionRecovery()

        val delays = listOf(lost(), failed(), failed()).map { event -> recovery.next(event) }

        assertEquals(
            listOf(2_000L, 10_000L, 30_000L).map { RecoveryAction.Restart(afterMs = it) },
            delays,
        )
    }

    @Test
    fun `ends after the last restart fails and releases what the lost helper started`() {
        val recovery = SessionRecovery()
        listOf(lost(), failed(), failed()).forEach(recovery::next)

        val action = recovery.next(failed())

        assertEquals(RecoveryAction.End(SessionEnd.RESTART_FAILED, hotspot), action)
    }

    @Test
    fun `ends when shizuku is not running`() {
        val action = SessionRecovery().next(lost(isShizukuReady = false))

        assertEquals(RecoveryAction.End(SessionEnd.SHIZUKU_STOPPED, hotspot), action)
    }

    @Test
    fun `a session that recovered owns its downstreams again`() {
        val recovery = SessionRecovery()
        recovery.next(lost())
        recovery.next(RecoveryEvent.RestartSucceeded)

        val action = recovery.next(RecoveryEvent.HelperLost(0, isShizukuReady = false, emptySet()))

        assertEquals(RecoveryAction.End(SessionEnd.SHIZUKU_STOPPED, emptySet()), action)
    }

    @Test
    fun `restarts that stayed up long enough no longer count against the budget`() {
        val recovery = SessionRecovery()
        listOf(lost(), failed(), failed()).forEach(recovery::next)
        recovery.next(RecoveryEvent.RestartSucceeded)

        val later = SessionRecovery.RESTART_WINDOW_MS + 1
        val action = recovery.next(lost(atMs = later))

        assertEquals(RecoveryAction.Restart(afterMs = 2_000), action)
    }

    @Test
    fun `a helper that keeps dying is not restarted forever`() {
        val recovery = SessionRecovery()
        repeat(3) {
            recovery.next(lost())
            recovery.next(RecoveryEvent.RestartSucceeded)
        }

        val action = recovery.next(lost())

        assertEquals(RecoveryAction.End(SessionEnd.RESTART_FAILED, hotspot), action)
    }

    @Test
    fun `the end message says only what happened`() {
        assertEquals(
            "Sharing could not be restarted automatically.",
            SessionEnd.RESTART_FAILED.message(isHotspotPossiblyOn = false),
        )
        assertEquals(
            "Sharing stopped because Shizuku is not running. The hotspot may still be on.",
            SessionEnd.SHIZUKU_STOPPED.message(isHotspotPossiblyOn = true),
        )
    }
}
