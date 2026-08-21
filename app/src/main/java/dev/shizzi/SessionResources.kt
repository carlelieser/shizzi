package dev.shizzi

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Binder
import android.os.ParcelFileDescriptor
import datapath.Datapath
import datapath.Session as DatapathSession

class SessionResources(
    private val testNetworkApi: TestNetworkApi,
    private val connectivityManager: ConnectivityManager,
) {

    private var tun: TunHandle? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var network: Network? = null
    private var datapathSession: DatapathSession? = null
    private var keepAliveCallback: ConnectivityManager.NetworkCallback? = null

    private val lifetimeToken = Binder()

    val interfaceName: String? get() = runCatching { tun?.interfaceName }.getOrNull()
    val acquiredNetwork: Network? get() = network

    fun acquire(
        addresses: List<android.net.LinkAddress>,
        dnsServers: List<java.net.InetAddress>,
        availabilityTimeoutMs: Int,
    ): String {
        val created = testNetworkApi.createTunInterface(addresses)
        tun = created
        fileDescriptor = created.fileDescriptor

        val name = created.interfaceName
        testNetworkApi.setupTestNetwork(name, dnsServers, lifetimeToken)

        network = awaitAvailability(name, availabilityTimeoutMs)
        requestKeepAlive()
        return name
    }

    private fun requestKeepAlive() {
        val request = NetworkRequest.Builder()
            .clearCapabilities()
            .addTransportType(resolveTransportTest())
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {}
        keepAliveCallback = callback

        runCatching { connectivityManager.requestNetwork(request, callback) }
            .onFailure { failure ->
                keepAliveCallback = null
                throw IllegalStateException(
                    "requestKeepAlive: could not request the test network, " +
                        "it would be lingered away within seconds",
                    failure,
                )
            }
    }

    fun startDatapath(mtu: Int) {
        val descriptor = fileDescriptor
            ?: error("startDatapath: no TUN fd; acquire() must succeed first")

        datapathSession = runCatching { Datapath.start(descriptor.fd.toLong(), mtu.toLong()) }
            .getOrElse { failure ->
                throw IllegalStateException(
                    "startDatapath: userspace stack failed to attach to fd " +
                        "${descriptor.fd} (mtu=$mtu)",
                    failure,
                )
            }
    }

    fun bindDatapathTo(handle: Long) {
        val session = datapathSession
            ?: error("bindDatapathTo($handle): no datapath session; startDatapath must succeed first")

        session.setNetwork(handle)
    }

    private fun awaitAvailability(interfaceName: String, timeoutMs: Int): Network {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            findNetworkOn(interfaceName)?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("test network '$interfaceName' did not become available within ${timeoutMs}ms")
    }

    private fun findNetworkOn(interfaceName: String): Network? =
        connectivityManager.allNetworks.firstOrNull { candidate ->
            connectivityManager.getLinkProperties(candidate)?.interfaceName == interfaceName
        }

    fun release(): List<String> {
        val problems = mutableListOf<String>()

        keepAliveCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                .onFailure { problems += "unregisterNetworkCallback: ${it.message}" }
        }
        keepAliveCallback = null

        datapathSession?.let { session ->
            runCatching { session.stop() }
                .onFailure { problems += "datapath.stop: ${it.message}" }
        }
        datapathSession = null

        network?.let { acquired ->
            runCatching { testNetworkApi.teardownTestNetwork(acquired) }
                .onFailure { problems += "teardownTestNetwork: ${it.message}" }
        }
        network = null

        fileDescriptor?.let { descriptor ->
            runCatching { descriptor.close() }
                .onFailure { problems += "close(tun fd): ${it.message}" }
        }
        fileDescriptor = null
        tun = null

        problems.forEach { SessionLog.warn("teardown problem: $it") }
        return problems
    }

    private companion object {
        const val POLL_INTERVAL_MS = 200L
    }
}
