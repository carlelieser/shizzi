package dev.shizzi

private val TETHERABLE_REGEXES_PATTERN = Regex(
    """^\s*tetherable(Usb|Ncm|Wifi|WifiP2p|Bluetooth)Regexs:\s*\[(.*)]\s*$""",
    RegexOption.MULTILINE,
)

/** Wi-Fi Direct groups serve locally and have no TETHERING_* type to stop, so they map to null. */
private val TYPE_BY_CONFIG_NAME = mapOf(
    "Usb" to TetheringType.USB,
    "Ncm" to TetheringType.NCM,
    "Wifi" to TetheringType.WIFI,
    "WifiP2p" to null,
    "Bluetooth" to TetheringType.BLUETOOTH,
)

private class TetherableRegexes(val type: TetheringType?, val patterns: List<Regex>)

/**
 * The types of the downstreams tethering has up, matched against tethering's own
 * interface regexes. Ethernet tethering has none, so a tethered interface no
 * regex claims is Ethernet. Null when `dumpsys tethering` lists no regexes.
 */
fun activeDownstreamTypes(output: String): Set<TetheringType>? {
    val regexes = tetherableRegexes(output)
    if (regexes.isEmpty()) return null

    return tetheredInterfaces(output)
        .mapNotNull { name ->
            val claimed = regexes.firstOrNull { entry -> entry.patterns.any { it.matches(name) } }
            if (claimed == null) TetheringType.ETHERNET else claimed.type
        }
        .toSet()
}

private fun tetherableRegexes(output: String): List<TetherableRegexes> =
    TETHERABLE_REGEXES_PATTERN.findAll(output)
        .map { match ->
            val (configName, list) = match.destructured
            val patterns = list.split(",").map(String::trim).filter(String::isNotEmpty).map(::Regex)
            TetherableRegexes(TYPE_BY_CONFIG_NAME.getValue(configName), patterns)
        }
        .toList()
