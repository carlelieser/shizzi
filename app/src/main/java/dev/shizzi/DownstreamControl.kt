package dev.shizzi

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * TETHER_ERROR_* values from framework-tethering.jar on the API 36 device under
 * test. The framework reports bare ints, so R7.5's verbatim errors would
 * otherwise reach the user as "error=14".
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
 * R4.1 wants setPreferTestNetworks(true) immediately *before* the downstream
 * starts, because the framework will not move an already-selected upstream: on
 * device the preference is accepted and the TUN appears in the quota table, yet
 * wlan0 stays selected past a 15s settle. Restarting under the preference
 * (R4.4) is the only path that re-runs selection.
 *
 * Reflection because there is no `cmd tethering`; shell holds TETHER_PRIVILEGED.
 */
class DownstreamControl(private val context: Context) {

    /**
     * TetheringManager captures the caller package at construction from
     * getOpPackageName(), and TetheringService rejects a mismatch with "Package
     * name android does not match UID 2000" — hence the rebased context.
     */
    private val tetheringManager: Any
        get() = context.getSystemService("tethering")
            ?: error("tethering service unavailable")

    /** Package the framework will attribute these calls to. */
    val opPackageName: String get() = context.opPackageName

    private val managerClass: Class<*> get() = Class.forName("android.net.TetheringManager")

    /** TETHERING_WIFI, stable across releases. */
    private val wifiTetheringType = 0

    /**
     * A fixed settle rather than a callback, whose shape has changed across
     * releases when all this needs is the downstream down before a restart.
     *
     * @return whether the call was accepted, which is not the hotspot being
     *   down — teardown needs [DownstreamInspector] for that distinction.
     */
    fun stopWifiTethering(): Boolean = runCatching {
        val method = managerClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
        method.invoke(tetheringManager, wifiTetheringType)
        Thread.sleep(STOP_SETTLE_MS)
    }.isSuccess

    /**
     * TetheringManager first: the earlier NO_CHANGE_TETHERING_PERMISSION came
     * from requesting an entitlement exemption, not from the start itself.
     * WifiManager is the fallback, though startSoftAp/startTetheredHotspot both
     * enforce MAINLINE_NETWORK_STACK, which shell can never hold.
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
     * Null config uses the device's saved hotspot settings, per section 1.2,
     * which excludes SSID/password/band control.
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
     * Exempt request first, then plain — as VPNHotspot does, since the
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
     * setExemptFromEntitlementCheck is the expensive option: in
     * hasTetherChangePermission it gates a branch requiring NETWORK_STACK or
     * NETWORK_SETTINGS, denying before the TETHER_PRIVILEGED check shell would
     * pass. No exemption keeps the caller on the path shell can satisfy.
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
     * A proxy for the hidden StartTetheringCallback, reporting through [result]
     * so the caller can surface the framework's own error code (R7.5).
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
        const val SUCCESS = "onTetheringStarted"
        const val STOP_SETTLE_MS = 3_000L
        const val START_TIMEOUT_MS = 15_000L
    }
}
