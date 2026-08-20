package dev.shizzi

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tears the session down when the tunnel stops being the sole upstream.
 *
 * verifyUpstream covers only the first seconds after start, but tethering can
 * reselect later — a cell handover, the retry timer, a Wi-Fi change — and
 * nothing else notices the session reporting ACTIVE while clients have moved
 * onto the physical upstream. That silent fallback is why the response is to
 * stop rather than repair.
 *
 * Runs in the shell process beside the session, so it dies when the session does.
 */
class SessionWatchdog(
    private val expectedInterface: String,
    private val onDrift: (String) -> Unit,
) {

    private val inspector = UpstreamInspector()
    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    /**
     * A single dumpsys miss is not evidence: parsing is loose and the upstream
     * is briefly empty during normal reselection, so acting on one sample would
     * trade a rare silent leak for frequent spurious disconnects.
     */
    private var consecutiveFailures = 0

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        thread = Thread({ monitor() }, "session-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Safe to call from [onDrift]: interrupting from inside its own callback
     * would flag the thread about to run teardown, and stopWifiTethering's
     * settle would throw InterruptedException exactly when the downstream most
     * needs to come down.
     */
    fun stop() {
        isRunning.set(false)

        val watcher = thread
        thread = null
        if (watcher != null && watcher != Thread.currentThread()) watcher.interrupt()
    }

    private fun monitor() {
        while (isRunning.get()) {
            val didSleep = runCatching { Thread.sleep(POLL_INTERVAL_MS) }.isSuccess
            if (!didSleep || !isRunning.get()) return

            val problem = checkUpstream()
            if (problem != null) {
                isRunning.set(false)
                Log.w(TAG, "tearing down: $problem")
                onDrift(problem)
                return
            }
        }
    }

    /**
     * @return null while the tunnel is still the sole upstream, otherwise a
     *   description of the drift once it has persisted long enough to trust.
     */
    private fun checkUpstream(): String? {
        val observation = inspector.observe()

        // A destroyed interface tethering still names is not traffic leaving
        // over a physical upstream; tearing down over a ghost would be the
        // drift response firing at nothing.
        val names = observation.liveInterfaceNames(expectedInterface)

        val isHealthy = !observation.didTimeout &&
            names.isNotEmpty() &&
            names.all { it == expectedInterface }

        if (isHealthy) {
            consecutiveFailures = 0
            return null
        }

        consecutiveFailures++
        if (consecutiveFailures < FAILURES_BEFORE_TEARDOWN) {
            Log.i(TAG, "upstream reads $names (strike $consecutiveFailures)")
            SessionLog.warn(
                "watchdog strike $consecutiveFailures/$FAILURES_BEFORE_TEARDOWN: " +
                    "upstream reads $names, expected $expectedInterface",
            )
            return null
        }

        return when {
            observation.didTimeout -> "upstream check timed out $consecutiveFailures times"
            else -> "upstream drifted from $expectedInterface to $names"
        }
    }

    private companion object {
        const val TAG = "SessionWatchdog"
        const val POLL_INTERVAL_MS = 5_000L

        /** Two strikes: tolerates a transient miss, still reacts within ~10s. */
        const val FAILURES_BEFORE_TEARDOWN = 2
    }
}
