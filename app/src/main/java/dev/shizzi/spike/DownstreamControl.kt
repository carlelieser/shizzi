package dev.shizzi.spike

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * TETHER_ERROR_* values, read out of framework-tethering.jar on the API 36
 * device under test.
 *
 * The framework reports these as bare ints, so a failure surfaces as
 * "error=14" with no clue what went wrong; naming them keeps R7.5's
 * verbatim-error requirement useful rather than cryptic.
 */
private val TETHER_ERROR_NAMES = mapOf(
    0 to "NO_ERROR",
    1 to "UNKNOWN_IFACE",
    2 to "SERVICE_UNAVAIL",
    3 to "UNSUPPORTED",
    4 to "UNAVAIL_IFACE",
    5 to "INTERNAL_ERROR",
    6 to "TETHER_IFACE_ERROR",
    7 to "UNTETHER_IFACE_ERROR",
    8 to "ENABLE_FORWARDING_ERROR",
    9 to "DISABLE_FORWARDING_ERROR",
    10 to "IFACE_CFG_ERROR",
    11 to "PROVISIONING_FAILED",
    12 to "DHCPSERVER_ERROR",
    13 to "ENTITLEMENT_UNKNOWN",
    14 to "NO_CHANGE_TETHERING_PERMISSION",
    15 to "NO_ACCESS_TETHERING_PERMISSION",
    16 to "UNKNOWN_TYPE",
    17 to "UNKNOWN_REQUEST",
    18 to "DUPLICATE_REQUEST",
    19 to "BLUETOOTH_SERVICE_PENDING",
    20 to "SOFT_AP_CALLBACK_PENDING",
)

private fun describeTetherError(code: Int?): String {
    val name = TETHER_ERROR_NAMES[code] ?: "UNKNOWN"
    return "error=$code TETHER_ERROR_$name"
}

/**
 * Stops and starts Wi-Fi tethering through TetheringManager.
 *
 * R4.1 requires setPreferTestNetworks(true) immediately *before* the downstream
 * starts. Device runs showed the framework will not move an already-selected
 * upstream: with the hotspot already tethered, the preference is accepted and
 * the TUN appears in the stack's upstream quota table, yet wlan0 stays selected
 * even after a 15s settle. Restarting the downstream under the preference is the
 * spec's own prescription (R4.4) and the only path that re-runs selection.
 *
 * Shell UID holds TETHER_PRIVILEGED, so these calls are permitted; there is no
 * `cmd tethering` shell implementation, hence reflection.
 */
class DownstreamControl(private val context: Context) {

    /**
     * TetheringManager captures the caller package at construction from
     * Context.getOpPackageName(), and TetheringService rejects the call with
     * "Package name android does not match UID 2000" when the two disagree.
     *
     * The context is rebased onto com.android.shell before use, and the op
     * package is forced to match, because createPackageContext alone does not
     * always change what getOpPackageName reports.
     */
    private val tetheringManager: Any
        get() = attributedContext.getSystemService("tethering")
            ?: error("tethering service unavailable")

    /** Package the framework will attribute these calls to. */
    val opPackageName: String get() = attributedContext.opPackageName

    private val attributedContext: Context by lazy {
        runCatching { context.createPackageContext(SHELL_PACKAGE, 0) }.getOrDefault(context)
    }

    private val managerClass: Class<*> get() = Class.forName("android.net.TetheringManager")

    /** TETHERING_WIFI, stable across releases. */
    private val wifiTetheringType = 0

    /**
     * Stops Wi-Fi tethering and waits for the framework to release it.
     *
     * The wait is a fixed settle rather than a callback: stopTethering's
     * completion callback shape has changed across releases, and the spike only
     * needs the downstream to be down before restarting it.
     */
    fun stopWifiTethering(): Boolean = runCatching {
        val method = managerClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
        method.invoke(tetheringManager, wifiTetheringType)
        Thread.sleep(STOP_SETTLE_MS)
    }.isSuccess

    /**
     * Starts Wi-Fi tethering, trying every route known to work from shell.
     *
     * Order matters. TetheringManager is first because the earlier
     * TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION came from requesting an
     * entitlement exemption, not from the start itself — the same failure
     * VPNHotspot handles by retrying without the exemption. WifiManager is the
     * fallback, though startSoftAp/startTetheredHotspot both enforce
     * MAINLINE_NETWORK_STACK, which shell can never hold.
     */
    fun startWifiTethering(): Pair<Boolean, String> {
        val viaTethering = startViaTetheringManager()
        if (viaTethering.first) return viaTethering

        val viaWifiManager = startViaWifiManager()
        return when {
            viaWifiManager.first -> viaWifiManager
            else -> false to "tethering[${viaTethering.second}]; wifiManager[${viaWifiManager.second}]"
        }
    }

    /**
     * Calls WifiManager.startTetheredHotspot(SoftApConfiguration) reflectively.
     *
     * Passing null uses the device's saved hotspot configuration, which is what
     * the spec wants: section 1.2 excludes SSID/password/band control.
     */
    private fun startViaWifiManager(): Pair<Boolean, String> = runCatching {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE)
            ?: return false to "wifi service unavailable"

        val configClass = Class.forName("android.net.wifi.SoftApConfiguration")
        val method = wifiManager.javaClass.getMethod("startTetheredHotspot", configClass)
        val accepted = method.invoke(wifiManager, null) as? Boolean ?: false

        when {
            accepted -> true to "startTetheredHotspot accepted"
            else -> false to "startTetheredHotspot returned false"
        }
    }.getOrElse { failure ->
        false to "startTetheredHotspot: ${failure.cause?.message ?: failure.message}"
    }

    /**
     * Tries the entitlement-exempt request first, then the plain one.
     *
     * VPNHotspot does the same: it asks for the exemption, and on
     * TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION retries without it. The
     * exemption is what a non-privileged caller cannot have.
     */
    private fun startViaTetheringManager(): Pair<Boolean, String> {
        val exempt = startTetheringRequest(isExemptFromEntitlement = true)
        if (exempt.first) return exempt

        val plain = startTetheringRequest(isExemptFromEntitlement = false)
        return when {
            plain.first -> plain
            else -> false to "exempt[${exempt.second}]; plain[${plain.second}]"
        }
    }

    private fun startTetheringRequest(isExemptFromEntitlement: Boolean): Pair<Boolean, String> {
        val request = runCatching { buildTetheringRequest(isExemptFromEntitlement) }
            .getOrElse { return false to "buildTetheringRequest: ${it.message}" }

        val latch = CountDownLatch(1)
        val result = arrayOf("no callback")

        val callback = createStartCallback(latch, result)
            ?: return false to "could not build StartTetheringCallback proxy"

        return runCatching {
            invokeStartTethering(request, callback)
            val didComplete = latch.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val didSucceed = didComplete && result[0] == SUCCESS
            didSucceed to result[0]
        }.getOrElse { false to "startTethering: ${it.message}" }
    }

    /**
     * Builds a TetheringRequest, optionally asking to skip the carrier
     * entitlement check.
     *
     * setExemptFromEntitlementCheck is the expensive option: in
     * TetheringService.hasTetherChangePermission that flag gates an early
     * branch requiring NETWORK_STACK or NETWORK_SETTINGS, which denies before
     * the TETHER_PRIVILEGED check shell would pass. Requesting no exemption
     * keeps the caller on the path shell can satisfy, at the cost of having to
     * clear entitlement normally.
     */
    private fun buildTetheringRequest(isExemptFromEntitlement: Boolean): Any {
        val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val builder = builderClass
            .getConstructor(Int::class.javaPrimitiveType)
            .newInstance(wifiTetheringType)

        if (isExemptFromEntitlement) {
            runCatching {
                builderClass
                    .getDeclaredMethod("setExemptFromEntitlementCheck", Boolean::class.java)
                    .invoke(builder, true)
            }
        }
        return builderClass.getMethod("build").invoke(builder)!!
    }

    private fun invokeStartTethering(request: Any, callback: Any) {
        val requestClass = Class.forName("android.net.TetheringManager\$TetheringRequest")
        val callbackClass = Class.forName("android.net.TetheringManager\$StartTetheringCallback")
        val method = managerClass.getMethod(
            "startTethering",
            requestClass,
            Executor::class.java,
            callbackClass,
        )
        method.invoke(tetheringManager, request, mainExecutor(), callback)
    }

    /**
     * Builds a dynamic proxy for the hidden StartTetheringCallback interface.
     *
     * onTetheringStarted/onTetheringFailed are reported through [result] so the
     * caller can surface the framework's own error code rather than a generic
     * failure (R7.5).
     */
    private fun createStartCallback(latch: CountDownLatch, result: Array<String>): Any? =
        runCatching {
            val callbackClass =
                Class.forName("android.net.TetheringManager\$StartTetheringCallback")

            java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { _, method, args ->
                recordCallback(method.name, args, result, latch)
                null
            }
        }.getOrNull()

    private fun recordCallback(
        name: String,
        args: Array<Any?>?,
        result: Array<String>,
        latch: CountDownLatch,
    ) {
        when (name) {
            "onTetheringStarted" -> {
                result[0] = SUCCESS
                latch.countDown()
            }

            "onTetheringFailed" -> {
                val code = args?.firstOrNull() as? Int
                result[0] = "onTetheringFailed(${describeTetherError(code)})"
                latch.countDown()
            }
        }
    }

    private fun mainExecutor(): Executor {
        val handler = Handler(Looper.getMainLooper())
        return Executor { command -> handler.post(command) }
    }

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val SUCCESS = "onTetheringStarted"
        const val STOP_SETTLE_MS = 3_000L
        const val START_TIMEOUT_MS = 15_000L
    }
}
