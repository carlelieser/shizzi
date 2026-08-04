package dev.shizzi.spike

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    private var callback: ConnectivityManager.NetworkCallback? = null

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
        return name
    }

    /**
     * Waits for the test network matching [interfaceName] to become available.
     *
     * R3.3 makes the timeout a hard failure: returning without a Network would
     * let tethering start against an upstream that does not exist yet, which is
     * exactly the R4.3 race.
     */
    private fun awaitAvailability(interfaceName: String, timeoutMs: Int): Network {
        val latch = CountDownLatch(1)
        val found = arrayOfNulls<Network>(1)

        val request = NetworkRequest.Builder()
            .addTransportType(resolveTransportTest())
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val registered = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) {
                found[0] = available
                latch.countDown()
            }
        }

        connectivityManager.registerNetworkCallback(request, registered)
        callback = registered

        val didAppear = latch.await(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        check(didAppear) {
            "test network '$interfaceName' did not become available within ${timeoutMs}ms"
        }
        return found[0] ?: error("test network '$interfaceName' reported available with null Network")
    }

    /**
     * Releases every held resource, continuing past individual failures.
     *
     * Teardown is the one place where continuing after an error is correct: a
     * failure to unregister a callback must not prevent closing the fd. Each
     * failure is still surfaced in the returned summary rather than swallowed.
     */
    fun release(): List<String> {
        val problems = mutableListOf<String>()

        callback?.let { registered ->
            runCatching { connectivityManager.unregisterNetworkCallback(registered) }
                .onFailure { problems += "unregisterNetworkCallback: ${it.message}" }
        }
        callback = null

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
    }
}
