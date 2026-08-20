package dev.shizzi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors the shell process's view of a live session into the app's, because
 * the privileged side has no way to push: it can adopt or lose a VPN long after
 * start returned, and the badge and notification would render whatever the last
 * binder call reported.
 *
 * Runs only while connected — status() binds if nothing is bound, so a poller
 * left running past teardown resurrects the process the user just stopped.
 */
class SessionStatusPoller(
    private val scope: CoroutineScope,
    private val controller: TetherClient,
) {

    private var job: Job? = null

    /**
     * @param onStatus called only for a successful read: a failed one is not a
     *   failed session — the shell's own watchdogs decide that — and an error
     *   here would contradict a session still running fine.
     */
    fun follow(isConnected: () -> Boolean, onStatus: (Result<String>) -> Unit) {
        stop()
        if (!isConnected()) return

        job = scope.launch {
            while (isConnected()) {
                delay(POLL_INTERVAL_MS)
                if (!isConnected()) return@launch

                val outcome = runCatching { controller.status() }
                if (outcome.isSuccess) onStatus(outcome)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        /**
         * Fast enough that the byte counters read as live rather than stepped.
         * Affordable only because a status read no longer spawns a process —
         * at the old ~127ms per read this would have burned ~13% of a core for
         * the life of every session.
         */
        const val POLL_INTERVAL_MS = 1_000L
    }
}
