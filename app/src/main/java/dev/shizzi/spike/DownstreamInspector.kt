package dev.shizzi.spike

/**
 * Reports whether any downstream is still tethered.
 *
 * Separate from DownstreamControl because the question is different in kind:
 * that class asks the framework to change state, this one checks what the
 * state actually is. stopTethering returning without throwing says only that
 * the request was accepted — a hotspot that stays up after teardown is the
 * dangerous case, since tethering falls back to the physical upstream and
 * clients keep browsing through it.
 */
class DownstreamInspector(private val inspector: UpstreamInspector = UpstreamInspector()) {

    /** @return null when nothing is tethered, else what is still up. */
    fun findTetheredDownstream(): String? {
        val observation = inspector.observe()
        if (observation.didTimeout) return "dumpsys tethering timed out; downstream state unknown"

        val tethered = observation.rawOutput.lineSequence()
            .mapNotNull { line -> TETHERED_STATE_PATTERN.find(line)?.groupValues?.get(1) }
            .distinct()
            .toList()

        return when {
            tethered.isEmpty() -> null
            else -> "downstream still tethered: $tethered"
        }
    }

    private companion object {
        /**
         * The live per-interface state line, e.g.
         * "wlan1 - TetheredState - lastError = 0".
         *
         * Anchored on this rather than the timestamped event log further down
         * the dump: those lines are history and still name TETHERED interfaces
         * long after teardown, which would report a stopped hotspot as running.
         */
        private val TETHERED_STATE_PATTERN =
            Regex("""^\s*(\w+)\s+-\s+TetheredState\s+-""")
    }
}
