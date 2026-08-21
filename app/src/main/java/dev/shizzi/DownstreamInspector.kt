package dev.shizzi

class DownstreamInspector(private val inspector: UpstreamInspector = UpstreamInspector()) {

    private var cachedCount = 0
    private var lastReadAt = 0L

    private var loggedCount = 0

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

    private fun logCountChange(count: Int) {
        if (count == loggedCount) return

        val direction = if (count > loggedCount) "connected" else "disconnected"
        loggedCount = count
        SessionLog.info("client $direction: $count now on the hotspot")
    }

    private fun parseDeviceCount(output: String): Int =
        CONNECTED_CLIENTS_PATTERN.findAll(output)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            .maxOrNull()
            ?: 0

    private companion object {

        private val TETHERED_STATE_PATTERN =
            Regex("""^\s*(\w+)\s+-\s+TetheredState\s+-""")

        private val CONNECTED_CLIENTS_PATTERN =
            Regex("""getConnectedClientList\(\)\.size\(\):\s*(\d+)""")

        private const val REFRESH_INTERVAL_MS = 10_000L
    }
}
