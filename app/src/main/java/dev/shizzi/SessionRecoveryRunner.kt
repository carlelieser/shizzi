package dev.shizzi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

interface RecoveryHost {

    fun showReconnecting()

    fun showWaitingForShizuku()

    /** True once the restarted session is sharing. */
    suspend fun restartSession(): Boolean

    suspend fun endSession(end: RecoveryAction.End)
}

/** Carries out [SessionRecovery]'s decisions on the session service's scope. */
class SessionRecoveryRunner(
    private val scope: CoroutineScope,
    private val host: RecoveryHost,
) {

    private var recovery = SessionRecovery()
    private var pending: Job? = null

    private val shizuku = ShizukuWatch { handle(RecoveryEvent.ShizukuReturned(now())) }

    fun onHelperLost(orphaned: Set<TetheringType>) {
        handle(RecoveryEvent.HelperLost(now(), isShizukuReady(), orphaned))
    }

    @Synchronized
    fun cancel() {
        shizuku.stop()
        pending?.cancel()
        pending = null
        recovery = SessionRecovery()
    }

    @Synchronized
    private fun handle(event: RecoveryEvent) {
        val action = recovery.next(event)
        SessionLog.info("recovery: ${describe(event)} -> ${describe(action)}")

        when (action) {
            is RecoveryAction.Restart -> stopWatchingAnd { restartAfter(action.afterMs) }
            RecoveryAction.WaitForShizuku -> waitForShizuku()
            is RecoveryAction.End -> stopWatchingAnd { host.endSession(action) }
            RecoveryAction.None -> Unit
        }
    }

    private fun stopWatchingAnd(block: suspend () -> Unit) {
        shizuku.stop()
        launch(block)
    }

    private fun waitForShizuku() {
        host.showWaitingForShizuku()
        launch {
            delay(SessionRecovery.SHIZUKU_WAIT_MS)
            handle(RecoveryEvent.WaitExpired)
        }
        shizuku.start()
    }

    private suspend fun restartAfter(delayMs: Long) {
        host.showReconnecting()
        delay(delayMs)

        val isUp = host.restartSession()
        currentCoroutineContext().ensureActive()

        handle(
            when {
                isUp -> RecoveryEvent.RestartSucceeded
                else -> RecoveryEvent.RestartFailed(now(), isShizukuReady())
            },
        )
    }

    private fun launch(block: suspend () -> Unit) {
        pending?.cancel()
        pending = scope.launch { block() }
    }

    private fun isShizukuReady(): Boolean = ShizukuGate.currentState() is ShizukuState.Ready

    private fun now(): Long = System.currentTimeMillis()

    private fun describe(event: RecoveryEvent): String = when (event) {
        is RecoveryEvent.HelperLost -> "helper lost (shizuku ready: ${event.isShizukuReady})"
        is RecoveryEvent.RestartFailed -> "restart failed (shizuku ready: ${event.isShizukuReady})"
        RecoveryEvent.RestartSucceeded -> "restart succeeded"
        is RecoveryEvent.ShizukuReturned -> "shizuku returned"
        RecoveryEvent.WaitExpired -> "shizuku wait expired"
    }

    private fun describe(action: RecoveryAction): String = when (action) {
        is RecoveryAction.Restart -> "restart in ${action.afterMs}ms"
        RecoveryAction.WaitForShizuku -> "wait for shizuku"
        is RecoveryAction.End -> "end (${action.reason.name.lowercase()})"
        RecoveryAction.None -> "nothing to do"
    }
}
