package dev.shizzi

/** Public resolvers offered in the DNS picker. Brand names are not translated. */
enum class DnsProvider(val label: String, val ipv4: List<String>, val ipv6: List<String>) {
    CLOUDFLARE(
        label = "Cloudflare",
        ipv4 = listOf("1.1.1.1", "1.0.0.1"),
        ipv6 = listOf("2606:4700:4700::1111", "2606:4700:4700::1001"),
    ),
    GOOGLE(
        label = "Google",
        ipv4 = listOf("8.8.8.8", "8.8.4.4"),
        ipv6 = listOf("2001:4860:4860::8888", "2001:4860:4860::8844"),
    ),
    QUAD9(
        label = "Quad9",
        ipv4 = listOf("9.9.9.9", "149.112.112.112"),
        ipv6 = listOf("2620:fe::fe", "2620:fe::9"),
    ),
    OPENDNS(
        label = "OpenDNS",
        ipv4 = listOf("208.67.222.222", "208.67.220.220"),
        ipv6 = listOf("2620:119:35::35", "2620:119:53::53"),
    ),
    ADGUARD(
        label = "AdGuard DNS",
        ipv4 = listOf("94.140.14.14", "94.140.15.15"),
        ipv6 = listOf("2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"),
    ),
    ;

    /** The setting value that selects this provider. */
    val servers: String get() = (ipv4 + ipv6).joinToString(", ")
}

/** The provider whose servers [saved] lists exactly, in any order or spelling. */
fun dnsProviderOf(saved: String): DnsProvider? {
    val parsed = parseDnsServers(saved) as? DnsParse.Valid ?: return null
    val addresses = parsed.servers.toSet()
    if (addresses.isEmpty()) return null

    return DnsProvider.entries.firstOrNull { provider ->
        (parseDnsServers(provider.servers) as DnsParse.Valid).servers.toSet() == addresses
    }
}
