package dev.shizzi

/** Which cable a session shares over, alongside or instead of the Wi-Fi hotspot. */
enum class WiredTethering(val types: List<TetheringType>) {
    OFF(emptyList()),
    AUTO(listOf(TetheringType.USB, TetheringType.ETHERNET)),
    USB(listOf(TetheringType.USB)),
    ETHERNET(listOf(TetheringType.ETHERNET)),
}

fun parseWiredTethering(stored: String?): WiredTethering =
    runCatching { WiredTethering.valueOf(stored.orEmpty()) }.getOrDefault(WiredTethering.AUTO)

/** What a session shares over. With wired tethering off the hotspot is all there is, so it stays on. */
data class SharingSetup(val wired: WiredTethering, private val isWifiHotspotChosen: Boolean) {

    val isWifiHotspotOn: Boolean
        get() = isWifiHotspotChosen || wired == WiredTethering.OFF

    /** Every downstream this setup may bring up. */
    val types: List<TetheringType>
        get() = if (isWifiHotspotOn) listOf(TetheringType.WIFI) + wired.types else wired.types
}

/** The setup a retired USB tethering setting meant. */
fun UsbTethering.toSharingSetup(): SharingSetup = when (this) {
    UsbTethering.NEVER -> SharingSetup(WiredTethering.OFF, isWifiHotspotChosen = true)
    UsbTethering.AUTO -> SharingSetup(WiredTethering.USB, isWifiHotspotChosen = true)
    UsbTethering.ALWAYS -> SharingSetup(WiredTethering.USB, isWifiHotspotChosen = false)
}
