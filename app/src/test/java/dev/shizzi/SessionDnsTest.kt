package dev.shizzi

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDnsTest {

    private val tailscale = listOf(address("100.100.100.100"), address("fd7a:115c:a1e0::53"))
    private val adguard = listOf(address("192.168.1.2"))

    @Test
    fun `uses custom servers over the vpn's`() {
        assertEquals(SessionDns(adguard, DnsSource.CUSTOM), chooseDns(adguard, tailscale))
    }

    @Test
    fun `uses the vpn's servers when no custom servers are set`() {
        assertEquals(SessionDns(tailscale, DnsSource.VPN), chooseDns(emptyList(), tailscale))
    }

    @Test
    fun `falls back to the default servers`() {
        assertEquals(SessionDns(DEFAULT_DNS_SERVERS, DnsSource.DEFAULT), chooseDns(emptyList(), emptyList()))
    }

    @Test
    fun `parses ipv4 and ipv6 servers separated by commas or whitespace`() {
        val parsed = parseDnsServers(" 192.168.1.2, fd7a:115c:a1e0::53\n1.1.1.1 ")

        assertEquals(
            DnsParse.Valid(listOf(address("192.168.1.2"), address("fd7a:115c:a1e0::53"), address("1.1.1.1"))),
            parsed,
        )
    }

    @Test
    fun `parses blank text as no servers`() {
        assertEquals(DnsParse.Valid(emptyList()), parseDnsServers("  "))
    }

    @Test
    fun `rejects hostnames and malformed addresses`() {
        listOf("dns.google", "192.168.1.256", "1.2.3", "fd7a::zz", "192.168.1.2,,example.com").forEach { text ->
            val parsed = parseDnsServers(text)
            assertTrue("$text -> $parsed", parsed is DnsParse.Invalid)
        }
    }

    @Test
    fun `names the first entry that is not an ip address`() {
        assertEquals(DnsParse.Invalid("dns.google"), parseDnsServers("1.1.1.1 dns.google 8.8.8.8"))
    }

    private fun address(literal: String): InetAddress = InetAddress.getByName(literal)
}
