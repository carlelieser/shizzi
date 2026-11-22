package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsProviderTest {

    @Test
    fun `every provider's servers parse as valid addresses`() {
        DnsProvider.entries.forEach { provider ->
            val parsed = parseDnsServers(provider.servers)
            assertEquals(provider.name, DnsParse.Valid::class, parsed::class)
            assertEquals(provider.name, 4, (parsed as DnsParse.Valid).servers.size)
        }
    }

    @Test
    fun `recognizes a provider's saved servers`() {
        DnsProvider.entries.forEach { provider ->
            assertEquals(provider, dnsProviderOf(provider.servers))
        }
    }

    @Test
    fun `recognizes a provider regardless of order and address spelling`() {
        val reordered = "2606:4700:4700:0:0:0:0:1001 1.0.0.1, 2606:4700:4700::1111,1.1.1.1"

        assertEquals(DnsProvider.CLOUDFLARE, dnsProviderOf(reordered))
    }

    @Test
    fun `treats a subset of a provider's servers as custom`() {
        assertNull(dnsProviderOf("1.1.1.1"))
    }

    @Test
    fun `treats extra servers as custom`() {
        assertNull(dnsProviderOf(DnsProvider.GOOGLE.servers + ", 192.168.1.2"))
    }

    @Test
    fun `has no provider for auto or invalid text`() {
        assertNull(dnsProviderOf(""))
        assertNull(dnsProviderOf("dns.google"))
    }
}
