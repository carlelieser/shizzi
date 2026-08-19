package dev.shizzi

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

    private var cachedCount = 0
    private var lastReadAt = 0L

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
     * Rate-limited rather than read every call. The dump costs ~127ms of
     * subprocess on device and status is polled continuously, so refreshing it
     * on every tick would spend most of a session's polling budget on a number
     * that changes when someone walks into the room. Between refreshes the last
     * reading stands.
     *
     * Bytes deliberately do not come from here — [InterfaceCounters] reads them
     * from the kernel in process, which is what lets the visible numbers move
     * at a useful rate.
     *
     * @return 0 when nothing is tethered or the dump could not be read. A
     *   failure reads the same as an idle hotspot: this feeds a display, and an
     *   unreadable dump is not worth failing a session over.
     */
    fun countDevices(): Int {
        val now = System.currentTimeMillis()
        if (now - lastReadAt < REFRESH_INTERVAL_MS) return cachedCount

        val observation = inspector.observeWifi()
        if (observation.didTimeout) return cachedCount

        lastReadAt = now
        cachedCount = parseDeviceCount(observation.rawOutput)
        return cachedCount
    }

    /**
     * Devices currently associated with the hotspot.
     *
     * Read from `dumpsys wifi`, not `dumpsys tethering`. Both of tethering's
     * candidate structures describe the past: the NAT forwarding rules keep
     * entries for minutes after a device leaves (observed at 176s), and the
     * "Client Information:" block is a DHCP lease list, which also still names
     * a device that has gone. UpstreamInspector's own notes warn about exactly
     * this — rule tables "describe what was, not what is".
     *
     * The Wi-Fi layer is where association is actually known, and it reports a
     * live count that returned to 0 the moment a test device disconnected.
     *
     * @return 0 when nothing is connected or the dump could not be read.
     */
    private fun parseDeviceCount(output: String): Int =
        CONNECTED_CLIENTS_PATTERN.findAll(output)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            .maxOrNull()
            ?: 0

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

        /**
         * The SoftAP's live association count, e.g.
         * "getConnectedClientList().size(): 0".
         *
         * Current state rather than one of the `num_connected_clients=` lines
         * further down, which are a timestamped event history: reading those
         * means reading whichever transition happened to be printed last.
         *
         * A device serving more than one AP instance prints this once each, so
         * the largest is taken rather than the first.
         */
        private val CONNECTED_CLIENTS_PATTERN =
            Regex("""getConnectedClientList\(\)\.size\(\):\s*(\d+)""")

        /**
         * How stale a device count may be.
         *
         * A device joining or leaving is not urgent to reflect — it is visible
         * to the person holding the device either way — and this is the cost
         * that keeps the poll cheap.
         */
        private const val REFRESH_INTERVAL_MS = 10_000L
    }
}
