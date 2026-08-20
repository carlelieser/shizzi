package dev.shizzi

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Binder
import android.os.ParcelFileDescriptor
import datapath.Datapath
import datapath.Session as DatapathSession

/**
 * The atomic resource group of R3.5: TUN fd, framework interface, network
 * handle, and callback registration, acquired and released together.
 *
 * Nothing outside this class holds any of the four — T-2 (orphaned testtun,
 * leaked fd) is what happens when those lifetimes drift apart.
 */
class SessionResources(
    private val testNetworkApi: TestNetworkApi,
    private val connectivityManager: ConnectivityManager,
) {

    private var tun: TunHandle? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var network: Network? = null
    private var datapathSession: DatapathSession? = null
    private var keepAliveCallback: ConnectivityManager.NetworkCallback? = null

    /** Binder whose lifetime the framework ties the test network to. */
    private val lifetimeToken = Binder()

    val interfaceName: String? get() = runCatching { tun?.interfaceName }.getOrNull()
    val acquiredNetwork: Network? get() = network

    /**
     * Creates the TUN, registers it as a test network, and blocks until
     * ConnectivityManager reports it available.
     *
     * @throws IllegalStateException naming the failing operation. Callers must
     *   [release] on failure; partial state is never implicitly owned.
     */
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

    /**
     * Tethering consuming a network as its upstream is not a NetworkRequest, so
     * with nothing requesting it ConnectivityService lingers the test network
     * the moment it connects — "handleLingerComplete for [N TEST]" about four
     * seconds after start, with the upstream reverting to cellular.
     *
     * requestNetwork, not registerNetworkCallback: only a request holds a
     * network; a listen callback observes it.
     */
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

    /**
     * Nothing in the kernel forwards packets out of a test network's TUN, so
     * without this tethered clients get a DHCP lease and no connectivity. The
     * datapath joins the same atomic group as the fd it reads.
     *
     * @throws IllegalStateException naming the fd, so this is distinguishable
     *   from a TUN or test-network failure.
     */
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

    /**
     * Pins the datapath's upstream sockets to [handle], or unbinds at 0.
     *
     * @throws IllegalStateException when acquire and startDatapath have not
     *   both succeeded.
     */
    fun bindDatapathTo(handle: Long) {
        val session = datapathSession
            ?: error("bindDatapathTo($handle): no datapath session; startDatapath must succeed first")

        session.setNetwork(handle)
    }

    /**
     * R3.3 makes the timeout a hard failure: returning without a Network lets
     * tethering start against an upstream that does not exist yet — the R4.3
     * race.
     */
    private fun awaitAvailability(interfaceName: String, timeoutMs: Int): Network {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            findNetworkOn(interfaceName)?.let { return it }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        error("test network '$interfaceName' did not become available within ${timeoutMs}ms")
    }

    /**
     * Polling rather than registerNetworkCallback, which from the shell UID is
     * rejected with "Package android does not belong to 2000" even with the
     * context rebased. getAllNetworks/getLinkProperties carry no such check.
     */
    private fun findNetworkOn(interfaceName: String): Network? =
        connectivityManager.allNetworks.firstOrNull { candidate ->
            connectivityManager.getLinkProperties(candidate)?.interfaceName == interfaceName
        }

    /**
     * Continues past individual failures — a failed teardownTestNetwork must
     * not prevent closing the fd — and returns them rather than swallowing them.
     */
    fun release(): List<String> {
        val problems = mutableListOf<String>()

        // First, so the framework lingers the network normally rather than
        // racing our explicit teardown.
        keepAliveCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                .onFailure { problems += "unregisterNetworkCallback: ${it.message}" }
        }
        keepAliveCallback = null

        // Before the fd: the stack is actively reading it, and closing it first
        // leaves reads racing against a descriptor the kernel may have reused.
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

        // To the session log, not only logcat: a leak here is what the next
        // start trips over, and the log is where that gets diagnosed.
        problems.forEach { SessionLog.warn("teardown problem: $it") }
        return problems
    }

    private companion object {
        const val POLL_INTERVAL_MS = 200L
    }
}
