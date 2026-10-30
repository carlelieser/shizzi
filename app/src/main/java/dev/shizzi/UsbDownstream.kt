package dev.shizzi

/**
 * Decides what a session does with USB for its [UsbTethering] setting: whether
 * it may start, what it brings up, and how the watchdog treats a missing cable.
 */
class UsbDownstream(
    private val setting: UsbTethering,
    private val link: UsbLink,
    private val onEvent: (String) -> Unit = {},
) {

    private val reconnect = UsbReconnect(onEvent)

    /** Only Always waits on USB; Auto keeps sharing over the hotspot. */
    val isWaitingForUsb: Boolean
        get() = setting == UsbTethering.ALWAYS && reconnect.isWaiting

    /**
     * Always has only USB, which can come up well after the cable does (the
     * computer may first ask to allow it), so its start leaves the wait to
     * [check] like an unplug.
     */
    val canStartUntethered: Boolean
        get() = setting == UsbTethering.ALWAYS

    /** Like the greyed-out stock toggle: Always with no cable has nothing to share with. */
    fun startProblem(): String? = when {
        setting != UsbTethering.ALWAYS -> null
        link.isHostConnected() == false -> NO_USB_CABLE
        else -> null
    }

    /** Auto leaves USB to [check] when no cable is connected yet. */
    fun typesToStart(): List<TetheringType> = when {
        setting != UsbTethering.AUTO -> setting.types
        link.isHostConnected() == false -> listOf(TetheringType.WIFI)
        else -> setting.types
    }

    /**
     * Restarts USB tethering when a cable is connected; called on every watchdog
     * check with that check's reading of the USB downstream.
     */
    fun check(now: Long, usbReading: DownstreamReading): DownstreamHealth {
        if (setting == UsbTethering.NEVER) return DownstreamHealth.Up

        // Unreadable: no grounds to restart USB or to give up on it.
        if (usbReading == DownstreamReading.Unknown) return DownstreamHealth.Up

        val isTethered = usbReading is DownstreamReading.Tethered
        val isCableConnected = !isTethered && link.isHostConnected() == true
        if (reconnect.shouldRestart(isTethered, isCableConnected, now)) {
            val (didStart, startDetail) = link.startTethering()
            if (!didStart) onEvent("usb tethering did not restart ($startDetail)")
        }
        if (isTethered || setting == UsbTethering.AUTO) return DownstreamHealth.Up

        if (reconnect.hasGivenUp(now)) {
            val minutes = UsbReconnect.GIVE_UP_AFTER_MS / 60_000
            return DownstreamHealth.Ended("usb not connected for $minutes minutes")
        }
        return DownstreamHealth.Awaiting
    }

    companion object {
        const val NO_USB_CABLE = "Failed to start. USB connection required."
    }
}
