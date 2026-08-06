package dev.shizzi

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.util.Log
import datapath.Datapath
import datapath.Session as DatapathSession

/**
 * The atomic resource group of R3.5: TUN fd, framework interface, network
 * handle, and callback registration are acquired and released together.
 *
 * The invariant is that nothing outside this class holds any of the four. The
 * spec's T-2 failure (orphaned testtun, leaked fd) is what happens when those
 * lifetimes drift apart, so they are deliberately not independently settable.
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
     * @throws IllegalStateException with the failing operation named, per the
     *   engineering guideline on error messages. Callers must call [release] on
     *   failure; partial state is never left implicitly owned.
     */
    fun acquire(address: android.net.LinkAddress, availabilityTimeoutMs: Int): String {
        val created = testNetworkApi.createTunInterface(address)
        tun = created
        fileDescriptor = created.fileDescriptor

        val name = created.interfaceName
        testNetworkApi.setupTestNetwork(name, lifetimeToken)

        network = awaitAvailability(name, availabilityTimeoutMs)
        requestKeepAlive()
        return name
    }

    /**
     * Holds a NetworkRequest so the framework does not linger the network away.
     *
     * Tethering consuming a network as its upstream is not a NetworkRequest.
     * With nothing requesting it, ConnectivityService lingers the test network
     * as soon as it connects and destroys it when the timer expires — observed
     * as "handleLingerComplete for [N TEST]" about four seconds after start,
     * with the upstream reverting to cellular.
     *
     * requestNetwork rather than registerNetworkCallback: only a request keeps
     * a network alive; a listen callback observes without holding it.
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
     * Starts the userspace stack that consumes the TUN.
     *
     * Nothing in the kernel forwards packets out of a test network's TUN, so
     * without this tethered clients get a DHCP lease and no connectivity. The
     * datapath joins the same atomic group as the fd it reads: [release] stops
     * it before the fd it is reading from is closed.
     *
     * @throws IllegalStateException naming the fd, so a failure here is
     *   distinguishable from a TUN or test-network failure.
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
     * Waits for the test network matching [interfaceName] to become available.
     *
     * R3.3 makes the timeout a hard failure: returning without a Network would
     * let tethering start against an upstream that does not exist yet, which is
     * exactly the R4.3 race.
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
     * Locates the framework Network bound to [interfaceName].
     *
     * Polling rather than registerNetworkCallback: from the shell UID that call
     * is rejected with "Package android does not belong to 2000" even when the
     * context is rebased onto com.android.shell, while the network itself
     * registers fine. getAllNetworks/getLinkProperties carry no such check.
     */
    private fun findNetworkOn(interfaceName: String): Network? =
        connectivityManager.allNetworks.firstOrNull { candidate ->
            connectivityManager.getLinkProperties(candidate)?.interfaceName == interfaceName
        }

    /**
     * Releases every held resource, continuing past individual failures.
     *
     * Teardown is the one place where continuing after an error is correct: a
     * failed teardownTestNetwork must not prevent closing the fd. Each failure
     * is still surfaced in the returned summary rather than swallowed.
     */
    fun release(): List<String> {
        val problems = mutableListOf<String>()

        // Releasing the request first lets the framework linger the network
        // normally rather than racing our explicit teardown.
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

        problems.forEach { Log.w(TAG, "teardown problem: $it") }
        return problems
    }

    private companion object {
        const val TAG = "SessionResources"
        const val POLL_INTERVAL_MS = 200L
    }
}
