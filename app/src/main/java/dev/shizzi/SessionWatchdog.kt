package dev.shizzi

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class SessionWatchdog(
    private val expectedInterface: String,
    private val onDrift: (String) -> Unit,
) {

    private val inspector = UpstreamInspector()
    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    private var consecutiveFailures = 0

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        thread = Thread({ monitor() }, "session-watchdog").apply {
            isDaemon = true
            start()
        }
    }

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

    private fun checkUpstream(): String? {
        val observation = inspector.observe()

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

        const val FAILURES_BEFORE_TEARDOWN = 2
    }
}
