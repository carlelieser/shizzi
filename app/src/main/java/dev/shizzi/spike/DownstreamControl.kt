package dev.shizzi.spike

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Outcome of a downstream restart attempt. */
data class RestartOutcome(
    val didStop: Boolean,
    val didStart: Boolean,
    val detail: String,
)

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

    private val tetheringManager: Any
        get() = context.getSystemService("tethering")
            ?: error("tethering service unavailable")

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
     * Starts Wi-Fi tethering via the TetheringRequest builder.
     *
     * Blocks until the framework reports success or failure, bounded by
     * [START_TIMEOUT_MS] so a stalled service cannot wedge the probe run (R4.6).
     */
    fun startWifiTethering(): Pair<Boolean, String> {
        val request = runCatching { buildTetheringRequest() }
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

    private fun buildTetheringRequest(): Any {
        val builderClass = Class.forName("android.net.TetheringManager\$TetheringRequest\$Builder")
        val builder = builderClass
            .getConstructor(Int::class.javaPrimitiveType)
            .newInstance(wifiTetheringType)
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
                result[0] = "onTetheringFailed(error=${args?.firstOrNull()})"
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
