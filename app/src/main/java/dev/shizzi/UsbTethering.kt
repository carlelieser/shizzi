package dev.shizzi

/** Whether a session shares over USB alongside the hotspot, instead of it, or not at all. */
enum class UsbTethering(val types: List<TetheringType>) {
    AUTO(listOf(TetheringType.WIFI, TetheringType.USB)),
    ALWAYS(listOf(TetheringType.USB)),
    NEVER(listOf(TetheringType.WIFI)),
}

fun parseUsbTethering(stored: String?): UsbTethering =
    runCatching { UsbTethering.valueOf(stored.orEmpty()) }.getOrDefault(UsbTethering.NEVER)
