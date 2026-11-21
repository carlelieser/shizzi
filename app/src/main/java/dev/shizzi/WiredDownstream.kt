package dev.shizzi

private enum class LinkHealth { UP, UNREAD, WAITING, GIVEN_UP }

private val NO_CONNECTION_BY_SETTING = mapOf(
    WiredTethering.USB to WiredDownstream.NO_USB_CABLE,
    WiredTethering.ETHERNET to WiredDownstream.NO_ETHERNET,
    WiredTethering.AUTO to WiredDownstream.NO_WIRED,
)

/**
 * Decides what a session does with its cables for its [SharingSetup]: whether
 * it may start, what it brings up, and how the watchdog treats a missing cable.
 */
class WiredDownstream(
    private val setup: SharingSetup,
    links: List<WiredLink>,
    private val onEvent: (String) -> Unit = {},
) {

    private val candidates = links.filter { link -> link.type in setup.wired.types }
    private val reconnects = candidates.associateWith { link -> WiredReconnect(link.type.label, onEvent) }

    /** Only a session without the hotspot waits on a cable; otherwise it keeps sharing over Wi-Fi. */
    val isWaitingForWired: Boolean
        get() = !setup.isWifiHotspotOn && reconnects.values.all(WiredReconnect::isWaiting)

    /**
     * Without the hotspot only cables remain, which can come up well after they
     * are plugged in (a computer may first ask to allow USB), so the start
     * leaves the wait to [check] like an unplug.
     */
    val canStartUntethered: Boolean
        get() = !setup.isWifiHotspotOn

    /** Like the greyed-out stock toggle: no hotspot and no cable leaves nothing to share with. */
    fun startProblem(): String? {
        if (setup.isWifiHotspotOn) return null
        if (candidates.any { link -> link.isConnected() != false }) return null

        return NO_CONNECTION_BY_SETTING.getValue(setup.wired)
    }

    /** Cables known to be unplugged are left to [check]. */
    fun typesToStart(): List<TetheringType> {
        val hotspot = if (setup.isWifiHotspotOn) listOf(TetheringType.WIFI) else emptyList()
        return hotspot + candidates.filter { link -> link.isConnected() != false }.map(WiredLink::type)
    }

    /**
     * Restarts tethering over each cable that is connected again; called on
     * every watchdog check with that check's reading of each downstream.
     */
    fun check(now: Long, readingOf: (TetheringType) -> DownstreamReading): DownstreamHealth {
        val healths = candidates.map { link -> checkLink(link, now, readingOf(link.type)) }
        if (setup.isWifiHotspotOn) return DownstreamHealth.Up

        return when {
            healths.any { it == LinkHealth.UP || it == LinkHealth.UNREAD } -> DownstreamHealth.Up
            healths.all { it == LinkHealth.GIVEN_UP } -> {
                val minutes = WiredReconnect.GIVE_UP_AFTER_MS / 60_000
                DownstreamHealth.Ended("no wired connection for $minutes minutes")
            }
            else -> DownstreamHealth.Awaiting
        }
    }

    private fun checkLink(link: WiredLink, now: Long, reading: DownstreamReading): LinkHealth {
        // Unreadable: no grounds to restart the link or to give up on it.
        if (reading == DownstreamReading.Unknown) return LinkHealth.UNREAD

        val isTethered = reading is DownstreamReading.Tethered
        val isCableConnected = !isTethered && link.isConnected() == true
        val reconnect = reconnects.getValue(link)
        if (reconnect.shouldRestart(isTethered, isCableConnected, now)) {
            val (didStart, startDetail) = link.startTethering()
            if (!didStart) onEvent("${link.type.label} did not restart ($startDetail)")
        }

        return when {
            isTethered -> LinkHealth.UP
            reconnect.hasGivenUp(now) -> LinkHealth.GIVEN_UP
            else -> LinkHealth.WAITING
        }
    }

    companion object {
        const val NO_USB_CABLE = "Failed to start. USB connection required."
        const val NO_ETHERNET = "Failed to start. Ethernet connection required."
        const val NO_WIRED = "Failed to start. Wired connection required."
    }
}
