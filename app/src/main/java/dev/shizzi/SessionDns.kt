package dev.shizzi

import java.net.InetAddress

val DEFAULT_DNS_SERVERS: List<InetAddress>
    get() = listOf("2001:4860:4860::8888", "8.8.8.8").map(InetAddress::getByName)

enum class DnsSource { CUSTOM, VPN, DEFAULT }

/** The servers the test network carries; tethering forwards client DNS queries to them. */
data class SessionDns(val servers: List<InetAddress>, val source: DnsSource)

fun chooseDns(custom: List<InetAddress>, vpn: List<InetAddress>): SessionDns = when {
    custom.isNotEmpty() -> SessionDns(custom, DnsSource.CUSTOM)
    vpn.isNotEmpty() -> SessionDns(vpn, DnsSource.VPN)
    else -> SessionDns(DEFAULT_DNS_SERVERS, DnsSource.DEFAULT)
}

sealed interface DnsParse {
    data class Valid(val servers: List<InetAddress>) : DnsParse
    data class Invalid(val entry: String) : DnsParse
}

private val SEPARATOR = Regex("""[\s,]+""")

private const val OCTET = """(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)"""
private val IPV4 = Regex("""^$OCTET(\.$OCTET){3}$""")
private val IPV6_CHARACTERS = Regex("""^[0-9a-fA-F:.]+$""")

/** Accepts IP literals only, so parsing never triggers a DNS lookup. */
fun parseDnsServers(text: String): DnsParse {
    val entries = text.split(SEPARATOR).filter(String::isNotEmpty)
    val servers = entries.map { entry -> parseAddress(entry) ?: return DnsParse.Invalid(entry) }
    return DnsParse.Valid(servers)
}

fun customDnsServers(text: String): List<InetAddress> = when (val parsed = parseDnsServers(text)) {
    is DnsParse.Valid -> parsed.servers
    is DnsParse.Invalid -> throw IllegalArgumentException(
        "customDnsServers: '${parsed.entry}' is not an IP address",
    )
}

private fun parseAddress(entry: String): InetAddress? {
    val isIpv6Candidate = ':' in entry && IPV6_CHARACTERS.matches(entry)
    if (!IPV4.matches(entry) && !isIpv6Candidate) return null

    return runCatching { InetAddress.getByName(entry) }.getOrNull()
}
