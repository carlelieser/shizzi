package dev.shizzi

sealed interface RecoveryEvent {

    data class HelperLost(
        val atMs: Long,
        val isShizukuReady: Boolean,
        val orphaned: Set<TetheringType>,
    ) : RecoveryEvent

    data class RestartFailed(val atMs: Long, val isShizukuReady: Boolean) : RecoveryEvent

    data object RestartSucceeded : RecoveryEvent

    data class ShizukuReturned(val atMs: Long) : RecoveryEvent

    data object WaitExpired : RecoveryEvent
}

enum class SessionEnd { RESTART_FAILED, SHIZUKU_STOPPED }

fun SessionEnd.message(isHotspotPossiblyOn: Boolean): String {
    val ended = when (this) {
        SessionEnd.RESTART_FAILED -> "Sharing could not be restarted automatically."
        SessionEnd.SHIZUKU_STOPPED -> "Sharing stopped because Shizuku is not running."
    }
    return if (isHotspotPossiblyOn) "$ended The hotspot may still be on." else ended
}

sealed interface RecoveryAction {

    data class Restart(val afterMs: Long) : RecoveryAction

    /** Shizuku is gone; a new helper can only be bound once it returns. */
    data object WaitForShizuku : RecoveryAction

    data class End(val reason: SessionEnd, val orphaned: Set<TetheringType>) : RecoveryAction

    data object None : RecoveryAction
}

/** Decides how a session that lost its helper gets back up, or why it ends. */
class SessionRecovery {

    private val restartedAt = mutableListOf<Long>()

    private var orphaned: Set<TetheringType> = emptySet()

    private var isWaitingForShizuku = false

    fun next(event: RecoveryEvent): RecoveryAction = when (event) {
        is RecoveryEvent.HelperLost -> {
            orphaned = orphaned + event.orphaned
            afterLoss(event.atMs, event.isShizukuReady)
        }

        is RecoveryEvent.RestartFailed -> afterLoss(event.atMs, event.isShizukuReady)

        RecoveryEvent.RestartSucceeded -> {
            orphaned = emptySet()
            RecoveryAction.None
        }

        is RecoveryEvent.ShizukuReturned -> afterWait { restart(event.atMs) }

        RecoveryEvent.WaitExpired -> afterWait { end(SessionEnd.SHIZUKU_STOPPED) }
    }

    private fun afterLoss(atMs: Long, isShizukuReady: Boolean): RecoveryAction {
        if (isShizukuReady) return restart(atMs)

        isWaitingForShizuku = true
        return RecoveryAction.WaitForShizuku
    }

    private fun afterWait(resolve: () -> RecoveryAction): RecoveryAction {
        if (!isWaitingForShizuku) return RecoveryAction.None

        isWaitingForShizuku = false
        return resolve()
    }

    private fun restart(atMs: Long): RecoveryAction {
        restartedAt.removeAll { restart -> restart < atMs - RESTART_WINDOW_MS }
        val delay = RESTART_DELAYS_MS.getOrNull(restartedAt.size)
            ?: return end(SessionEnd.RESTART_FAILED)

        restartedAt += atMs
        return RecoveryAction.Restart(afterMs = delay)
    }

    private fun end(reason: SessionEnd): RecoveryAction = RecoveryAction.End(reason, orphaned)

    companion object {

        val RESTART_DELAYS_MS = listOf(2_000L, 10_000L, 30_000L)

        const val RESTART_WINDOW_MS = 5 * 60_000L

        const val SHIZUKU_WAIT_MS = 10 * 60_000L
    }
}
