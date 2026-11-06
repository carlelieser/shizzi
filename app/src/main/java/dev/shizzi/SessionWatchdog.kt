package dev.shizzi

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** What the session found when the watchdog asked about its downstream. */
sealed interface DownstreamHealth {
    object Up : DownstreamHealth
    object Awaiting : DownstreamHealth

    /** Gone for good, such as USB that never came back; the session ends like a stop. */
    class Ended(val reason: String) : DownstreamHealth
}

/** Why the watchdog ended a session. */
sealed interface WatchdogEnd {

    /** Tethering no longer routes only through the session's upstream. */
    class Failed(val problem: String) : WatchdogEnd

    class Finished(val reason: String) : WatchdogEnd
}

class SessionWatchdog(
    private val expectedInterface: String,
    private val checkDownstream: (UpstreamObservation) -> DownstreamHealth = { DownstreamHealth.Up },
    private val onEnd: (WatchdogEnd) -> Unit,
) {

    private val inspector = UpstreamInspector()
    private val isRunning = AtomicBoolean(false)
    private val timer = CheckTimer(POLL_INTERVAL_MS)
    private var thread: Thread? = null

    private val tolerance = UpstreamTolerance(expectedInterface) { strike ->
        Log.i(TAG, strike)
        SessionLog.warn(strike)
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        thread = Thread({ monitor() }, "session-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    /** Checks the downstream now instead of at the next interval. */
    fun wake() = timer.wake()

    fun stop() {
        isRunning.set(false)

        val watcher = thread
        thread = null
        if (watcher != null && watcher != Thread.currentThread()) watcher.interrupt()
    }

    private fun monitor() {
        while (isRunning.get()) {
            val kind = runCatching { timer.await() }.getOrElse { return }
            if (!isRunning.get()) return

            val end = when (kind) {
                CheckKind.FULL -> checkUpstream()
                CheckKind.DOWNSTREAM -> checkDownstreamOnly()
            }
            if (end != null) {
                isRunning.set(false)
                logEnd(end)
                onEnd(end)
                return
            }
        }
    }

    private fun checkUpstream(): WatchdogEnd? {
        val observation = inspector.observe()
        val names = observation.liveInterfaceNames(expectedInterface)
        val reading = classifyUpstream(names, expectedInterface, observation.didTimeout)

        // Asked every check, not only on ABSENT, so the session also sees the
        // downstream come back once the upstream is healthy again.
        val health = checkDownstream(observation)
        if (health is DownstreamHealth.Ended) return WatchdogEnd.Finished(health.reason)

        return tolerance.judge(reading, names, health)?.let(WatchdogEnd::Failed)
    }

    private fun logEnd(end: WatchdogEnd) = when (end) {
        is WatchdogEnd.Failed -> SessionLog.warn("ending session: ${end.problem}")
        is WatchdogEnd.Finished -> SessionLog.info("ending session: ${end.reason}")
    }

    /** Upstream strikes count full checks only, so a woken check judges the downstream alone. */
    private fun checkDownstreamOnly(): WatchdogEnd? {
        val health = checkDownstream(inspector.observe())
        return (health as? DownstreamHealth.Ended)?.let { WatchdogEnd.Finished(it.reason) }
    }

    private companion object {
        const val TAG = "SessionWatchdog"
        const val POLL_INTERVAL_MS = 5_000L
    }
}
