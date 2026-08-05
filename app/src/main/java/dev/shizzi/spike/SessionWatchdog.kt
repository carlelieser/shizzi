package dev.shizzi.spike

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tears the session down when the tunnel stops being the sole upstream.
 *
 * verifyUpstream only covers the first few seconds after start. Tethering can
 * reselect later — a cell handover, the retry timer, a Wi-Fi change — and
 * nothing else notices: the session still reports ACTIVE while clients have
 * silently moved onto the physical upstream. That silent fallback is the
 * failure this exists to prevent, so the response is to stop rather than to
 * repair.
 *
 * Runs inside the shell process alongside the session it guards, so it dies
 * exactly when the session does.
 */
class SessionWatchdog(
    private val expectedInterface: String,
    private val onDrift: (String) -> Unit,
) {

    private val inspector = UpstreamInspector()
    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    /**
     * Consecutive bad reads seen so far.
     *
     * A single dumpsys miss is not evidence: parsing is deliberately loose and
     * the upstream is briefly empty during normal reselection. Acting on one
     * sample would turn a rare silent leak into frequent spurious disconnects,
     * so drift has to persist across [FAILURES_BEFORE_TEARDOWN] reads.
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
     * Stops watching.
     *
     * Safe to call from [onDrift]: interrupting the watchdog thread from inside
     * its own callback would set the interrupt flag on the thread about to run
     * teardown, and stopWifiTethering sleeps through a settle that would then
     * throw InterruptedException — skipping the settle precisely when the
     * downstream most needs to come down.
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
        val names = observation.interfaceNames

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
