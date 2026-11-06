package dev.shizzi

/** TetheringManager.TETHERING_* constants for the downstreams Shizzi can bring up. */
enum class TetheringType(val code: Int, val label: String) {
    WIFI(0, "hotspot"),
    USB(1, "usb tethering"),
}

private val USB_INTERFACE_PATTERN = Regex("""^(rndis|ncm|usb)\d*$""")

private val UNSHARED_INTERFACE_PATTERN = Regex("""^(bt-pan|eth\d+)$""")

/**
 * Classifies a tethered interface. Null for Bluetooth and Ethernet tethering,
 * which Shizzi neither starts nor stops; hotspot names vary by OEM, so anything
 * else counts as the hotspot.
 */
fun tetheringTypeOf(interfaceName: String): TetheringType? = when {
    USB_INTERFACE_PATTERN.matches(interfaceName) -> TetheringType.USB
    UNSHARED_INTERFACE_PATTERN.matches(interfaceName) -> null
    else -> TetheringType.WIFI
}

/**
 * The downstreams to stop before starting [types]. Stopping USB tethering with
 * nothing running can reset the USB function, but starting it while it runs
 * fails as a duplicate request, so USB is stopped unless it is known to be down.
 */
fun typesToRestart(types: List<TetheringType>, usbReading: DownstreamReading): List<TetheringType> =
    types.filter { type -> type != TetheringType.USB || usbReading != DownstreamReading.None }
