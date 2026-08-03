package dev.shizzi.spike

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.lang.reflect.Method

/**
 * Every hidden/system API this prototype depends on, named and described in one
 * place.
 *
 * Exit criterion #4 of the spec requires a written record of which hidden APIs
 * were touched and which reflection paths were required. That record is this
 * file plus docs/hidden-api-record.md: each [HiddenApiPath] entry is both the
 * documentation and the thing actually invoked, so the two cannot drift.
 */
data class HiddenApiPath(
    val id: String,
    val className: String,
    val memberName: String,
    val since: Int,
    val notes: String,
)

/**
 * The reflection paths, as data. Resolution failures are reported per-path so a
 * single missing method identifies itself instead of collapsing the whole run.
 */
object HiddenApiCatalog {
    val paths: List<HiddenApiPath> = listOf(
        HiddenApiPath(
            id = "TestNetworkManager.class",
            className = "android.net.TestNetworkManager",
            memberName = "<class>",
            since = 30,
            notes = "Obtained via Context.getSystemService(\"test_network\"); " +
                "@TestApi, not in the SDK. Absent on trimmed OEM builds.",
        ),
        HiddenApiPath(
            id = "TestNetworkManager.createTunInterface",
            className = "android.net.TestNetworkManager",
            memberName = "createTunInterface",
            since = 30,
            notes = "Signature changed across releases: API 30 takes " +
                "LinkAddress[], later releases take Collection<LinkAddress>. " +
                "Both overloads are probed.",
        ),
        HiddenApiPath(
            id = "TestNetworkManager.setupTestNetwork",
            className = "android.net.TestNetworkManager",
            memberName = "setupTestNetwork",
            since = 30,
            notes = "Overload used takes (String iface, IBinder binder). " +
                "Requires MANAGE_TEST_NETWORKS, held by shell UID 2000.",
        ),
        HiddenApiPath(
            id = "TestNetworkManager.teardownTestNetwork",
            className = "android.net.TestNetworkManager",
            memberName = "teardownTestNetwork",
            since = 30,
            notes = "Takes the Network returned by the availability callback.",
        ),
        HiddenApiPath(
            id = "TestNetworkInterface.getFileDescriptor",
            className = "android.net.TestNetworkInterface",
            memberName = "getFileDescriptor",
            since = 30,
            notes = "Returns ParcelFileDescriptor owning the TUN. This fd is " +
                "the datapath handoff point.",
        ),
        HiddenApiPath(
            id = "TestNetworkInterface.getInterfaceName",
            className = "android.net.TestNetworkInterface",
            memberName = "getInterfaceName",
            since = 30,
            notes = "Yields the testtunN name matched against dumpsys output.",
        ),
        HiddenApiPath(
            id = "TetheringManager.setPreferTestNetworks",
            className = "android.net.TetheringManager",
            memberName = "setPreferTestNetworks",
            since = 33,
            notes = "THE load-bearing call. @TestApi, added in T. Absent below " +
                "33, which is why the feature floor is API 33 (spec 1.3).",
        ),
    )
}

/** Outcome of resolving one hidden API path. */
sealed interface Resolution {
    data class Found(val path: HiddenApiPath) : Resolution
    data class Missing(val path: HiddenApiPath, val reason: String) : Resolution
}

/**
 * Thin typed wrapper over the hidden test-network surface.
 *
 * Construction resolves nothing; each accessor resolves lazily and reports its
 * own failure, so a probe run can distinguish "class absent" from "method
 * renamed" from "permission denied".
 */
class TestNetworkApi(private val context: Context) {

    private val managerClass: Class<*>? = runCatching {
        Class.forName("android.net.TestNetworkManager")
    }.getOrNull()

    @SuppressLint("WrongConstant")
    private val manager: Any? = runCatching {
        // "test_network" is TestNetworkManager.TEST_NETWORK_SERVICE, itself hidden.
        context.getSystemService("test_network")
    }.getOrNull()

    val isAvailable: Boolean get() = managerClass != null && manager != null

    /** Resolves every catalog entry so the report can list what exists on this build. */
    fun resolveAll(): List<Resolution> = HiddenApiCatalog.paths.map { path ->
        resolveOne(path)
    }

    private fun resolveOne(path: HiddenApiPath): Resolution {
        val owner = runCatching { Class.forName(path.className) }.getOrNull()
            ?: return Resolution.Missing(path, "class not found: ${path.className}")

        if (path.memberName == "<class>") return Resolution.Found(path)

        val hasMember = owner.declaredMethods.any { it.name == path.memberName }
        return when {
            hasMember -> Resolution.Found(path)
            else -> Resolution.Missing(path, "no method '${path.memberName}' on ${path.className}")
        }
    }

    /**
     * Creates a TUN carrying [address].
     *
     * Tries the Collection overload first (API 31+) then the array overload
     * (API 30), because the parameter type changed between releases.
     */
    fun createTunInterface(address: LinkAddress): TunHandle {
        val instance = manager ?: error("createTunInterface: test_network service unavailable")
        val owner = managerClass ?: error("createTunInterface: TestNetworkManager class absent")

        val created = invokeCollectionOverload(owner, instance, address)
            ?: invokeArrayOverload(owner, instance, address)
            ?: error("createTunInterface: no known overload accepted LinkAddress $address")

        return TunHandle(created)
    }

    private fun invokeCollectionOverload(
        owner: Class<*>,
        instance: Any,
        address: LinkAddress,
    ): Any? = runCatching {
        val method = owner.getMethod("createTunInterface", Collection::class.java)
        method.invoke(instance, listOf(address))
    }.getOrNull()

    private fun invokeArrayOverload(
        owner: Class<*>,
        instance: Any,
        address: LinkAddress,
    ): Any? = runCatching {
        val arrayType = Class.forName("[Landroid.net.LinkAddress;")
        val method = owner.getMethod("createTunInterface", arrayType)
        val argument = java.lang.reflect.Array.newInstance(LinkAddress::class.java, 1)
        java.lang.reflect.Array.set(argument, 0, address)
        method.invoke(instance, argument)
    }.getOrNull()

    /** Registers [interfaceName] as a test network bound to the lifetime of [binder]. */
    fun setupTestNetwork(interfaceName: String, binder: IBinder) {
        val instance = manager ?: error("setupTestNetwork: test_network service unavailable")
        val owner = managerClass ?: error("setupTestNetwork: TestNetworkManager class absent")
        val method = owner.getMethod("setupTestNetwork", String::class.java, IBinder::class.java)
        method.invoke(instance, interfaceName, binder)
    }

    /** Tears down the framework-side test network for [network]. */
    fun teardownTestNetwork(network: Network) {
        val instance = manager ?: return
        val owner = managerClass ?: return
        val method = owner.getMethod("teardownTestNetwork", Network::class.java)
        method.invoke(instance, network)
    }
}

/** Wrapper over a hidden TestNetworkInterface instance. */
class TunHandle(private val delegate: Any) {

    val interfaceName: String
        get() {
            val method = delegate.javaClass.getMethod("getInterfaceName")
            return method.invoke(delegate) as String
        }

    val fileDescriptor: ParcelFileDescriptor
        get() {
            val method = delegate.javaClass.getMethod("getFileDescriptor")
            return method.invoke(delegate) as ParcelFileDescriptor
        }
}

/**
 * Wrapper over TetheringManager.setPreferTestNetworks (API 33+).
 *
 * This is the single call the whole approach rests on; if it is absent the
 * prototype is not viable on that build, so its absence is reported rather than
 * swallowed.
 */
class TetheringPreferenceApi(private val context: Context) {

    private fun resolveMethod(): Method {
        check(Build.VERSION.SDK_INT >= 33) {
            "setPreferTestNetworks requires API 33, device is ${Build.VERSION.SDK_INT}"
        }
        val owner = Class.forName("android.net.TetheringManager")
        return owner.getMethod("setPreferTestNetworks", Boolean::class.javaPrimitiveType)
    }

    private fun tetheringManager(): Any =
        context.getSystemService("tethering")
            ?: error("setPreferTestNetworks: tethering service unavailable")

    fun setPreferTestNetworks(prefer: Boolean) {
        resolveMethod().invoke(tetheringManager(), prefer)
    }
}

/** ConnectivityManager is public API; kept here so the probe reads in one place. */
fun Context.connectivityManager(): ConnectivityManager =
    getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
