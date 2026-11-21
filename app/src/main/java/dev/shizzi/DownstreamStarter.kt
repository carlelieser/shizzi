package dev.shizzi

import android.content.Context

/**
 * Brings up a session's downstreams after stopping every downstream already up,
 * then restores the ones the session does not own, so they route through it too.
 */
class DownstreamStarter(private val context: Context, private val started: StartedDownstreams) {

    private val inspector = UpstreamInspector()

    /** True once a started downstream is tethered. */
    fun start(types: List<TetheringType>): Boolean {
        val control = DownstreamControl(context)
        val restart = planFor(types)
        control.stopTethering(restart.toStop)

        val (succeeded, failed) = types
            .associateWith(control::startTethering)
            .entries
            .partition { (_, outcome) -> outcome.first }
        succeeded.forEach { (type, _) -> started.add(type) }
        restore(control, restart.toRestore)

        val failures = failed.map { (type, outcome) -> "${type.label} did not start (${outcome.second})" }
        check(succeeded.isNotEmpty()) {
            "${SessionError.DOWNSTREAM_NOT_STARTED} ${failures.joinToString("; ")}"
        }

        failures.forEach(SessionLog::warn)
        return awaitTethered(succeeded.map { it.key })
    }

    private fun planFor(types: List<TetheringType>): DownstreamRestart {
        val observation = inspector.observe()
        val active = if (observation.didTimeout) null else activeDownstreamTypes(observation.rawOutput)
        if (active == null) SessionLog.warn("could not tell which downstreams are up; other downstreams left running")

        val usbReading = readDownstream(observation, listOf(TetheringType.USB))
        val restart = planDownstreamRestart(types, active.orEmpty(), usbReading)
        if (restart.toRestore.isNotEmpty()) {
            SessionLog.info("stopping ${restart.toRestore.map { it.label }} so tethering takes the session upstream")
        }
        return restart
    }

    private fun restore(control: DownstreamControl, types: List<TetheringType>) {
        types.forEach { type ->
            val (didStart, detail) = control.startTethering(type)
            when {
                didStart -> SessionLog.info("${type.label} restored")
                else -> SessionLog.warn("${type.label} was stopped for the session and did not restart ($detail)")
            }
        }
    }

    private fun awaitTethered(types: List<TetheringType>): Boolean {
        val deadline = System.currentTimeMillis() + DOWNSTREAM_SETTLE_MS
        val downstream = DownstreamInspector()

        while (System.currentTimeMillis() < deadline) {

            if (downstream.findTetheredDownstream(types) is DownstreamReading.Tethered) {
                SessionLog.info("downstream tethered")
                return true
            }
            Thread.sleep(DOWNSTREAM_POLL_MS)
        }

        val hint = if (TetheringType.USB in types) " (is a USB cable connected to a computer?)" else ""
        SessionLog.warn("downstream not tethered after ${DOWNSTREAM_SETTLE_MS}ms$hint; continuing")
        return false
    }

    private companion object {
        const val DOWNSTREAM_SETTLE_MS = 10_000L
        const val DOWNSTREAM_POLL_MS = 500L
    }
}
