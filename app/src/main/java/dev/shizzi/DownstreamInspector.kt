package dev.shizzi

/**
 * Reports whether any downstream is still tethered.
 *
 * Separate from DownstreamControl, which asks the framework to change state:
 * stopTethering returning says only that the request was accepted, and a
 * hotspot still up after teardown is the dangerous case — tethering falls back
 * to the physical upstream and clients keep browsing through it.
 */
class DownstreamInspector(private val inspector: UpstreamInspector = UpstreamInspector()) {

    private var cachedCount = 0
    private var lastReadAt = 0L

    /**
     * Kept apart from [cachedCount] so the log records changes rather than
     * readings — a line per poll would bury the file before the size cap.
     */
    private var loggedCount = 0

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

    /**
     * How many devices are on the hotspot.
     *
     * Rate-limited: the dump costs ~127ms of subprocess and status polls
     * continuously, which would spend the polling budget on a number that
     * changes when someone walks into the room. Bytes come from
     * [InterfaceCounters] instead, which is what lets them move at a useful rate.
     *
     * @return 0 when nothing is tethered or the dump could not be read — this
     *   feeds a display, and an unreadable dump is not worth failing over.
     */
    fun countDevices(): Int {
        val now = System.currentTimeMillis()
        if (now - lastReadAt < REFRESH_INTERVAL_MS) return cachedCount

        val observation = inspector.observeWifi()
        if (observation.didTimeout) return cachedCount

        lastReadAt = now
        cachedCount = parseDeviceCount(observation.rawOutput)
        logCountChange(cachedCount)
        return cachedCount
    }

    /** Transitions only; a steady count says nothing new. */
    private fun logCountChange(count: Int) {
        if (count == loggedCount) return

        val direction = if (count > loggedCount) "connected" else "disconnected"
        loggedCount = count
        SessionLog.info("client $direction: $count now on the hotspot")
    }

    /**
     * Read from `dumpsys wifi`, not `dumpsys tethering`, whose candidate
     * structures both describe the past: NAT rules keep entries for minutes
     * after a device leaves (observed at 176s), and "Client Information:" is a
     * DHCP lease list. The Wi-Fi layer is where association is known, and its
     * count returned to 0 the moment a test device disconnected.
     */
    private fun parseDeviceCount(output: String): Int =
        CONNECTED_CLIENTS_PATTERN.findAll(output)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            .maxOrNull()
            ?: 0

    private companion object {
        /**
         * The live state line, "wlan1 - TetheredState - lastError = 0", not the
         * timestamped event log below it — that names TETHERED interfaces long
         * after teardown, reporting a stopped hotspot as running.
         */
        private val TETHERED_STATE_PATTERN =
            Regex("""^\s*(\w+)\s+-\s+TetheredState\s+-""")

        /**
         * "getConnectedClientList().size(): 0" — current state, unlike the
         * `num_connected_clients=` history below it, where reading means
         * reading whichever transition printed last. Largest wins, because a
         * device serving several AP instances prints this once each.
         */
        private val CONNECTED_CLIENTS_PATTERN =
            Regex("""getConnectedClientList\(\)\.size\(\):\s*(\d+)""")

        /** Staleness bought in exchange for a cheap poll; nobody is waiting on it. */
        private const val REFRESH_INTERVAL_MS = 10_000L
    }
}
