package dev.shizzi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors the shell process's view of a live session into the app's.
 *
 * The privileged side has no way to push. It can adopt or lose a VPN long after
 * the start call returned, and without this neither the badge nor the
 * notification would ever hear about it — they would keep rendering whatever
 * the last binder call happened to report. getStatus is documented as cheap
 * enough to poll and reports only what that process already holds.
 *
 * Runs only while a session is connected. status() binds the user service if
 * nothing is bound, so a poller left running past teardown would resurrect the
 * shell process the user just stopped.
 */
class SessionStatusPoller(
    private val scope: CoroutineScope,
    private val controller: TetherClient,
) {

    private var job: Job? = null

    /**
     * Restarts polling if [isConnected] says a session is up, else stops.
     *
     * @param onStatus folds one reading in and is called only for a successful
     *   read: a failed one is not a failed session — the shell process's own
     *   watchdogs decide that — and repainting an error here would contradict a
     *   session that is still running fine.
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
         * Fast enough that the byte counters read as live rather than as
         * steps.
         *
         * Affordable only because a status read no longer spawns a process:
         * the counters come from /proc in process, and the device count behind
         * it refreshes on its own slower schedule. At the previous design's
         * ~127ms per read this rate would have burned ~13% of a core for the
         * life of every session.
         */
        const val POLL_INTERVAL_MS = 1_000L
    }
}
