package dev.shizzi.spike

import android.content.Context
import android.util.Log
import org.json.JSONObject

/** What the session is currently doing, as reported to the UI. */
enum class SessionState { IDLE, STARTING, ACTIVE, ERROR }

/**
 * Owns a long-lived protected-tethering session inside the shell process.
 *
 * The probe runner answers questions and tears down immediately; this holds the
 * session up until asked to stop, which is what a client needs in order to
 * connect and pass traffic.
 *
 * The start ordering is not arbitrary and must not be rearranged: the
 * downstream restarts *before* the TUN is created. startTethering runs
 * UpstreamNetworkMonitor.startObserveUpstreamNetworks, which clears
 * mNetworkMap and registers a fresh callback, so a TUN created earlier has
 * already fired onAvailable and is dropped by that wipe. Meanwhile
 * setPreferTestNetworks only writes a flag — it never re-runs selection — so
 * the flag has to be true before the TUN arrives. Restarting first is the only
 * order satisfying both.
 */
class TetherSession(private val context: Context) {

    private val testNetworkApi = TestNetworkApi(context)
    private val inspector = UpstreamInspector()

    private var resources: SessionResources? = null
    private var state = SessionState.IDLE
    private var detail = "not started"
    private var interfaceName: String? = null
    private var watchdog: SessionWatchdog? = null
    private var shutdownHook: Thread? = null

    val isActive: Boolean get() = state == SessionState.ACTIVE

    /**
     * Brings the session up, tearing down on any failure.
     *
     * @return JSON status; never throws, so the UI always has something to show.
     */
    fun start(): String {
        if (isActive) return status()

        state = SessionState.STARTING
        return runCatching { bringUp() }
            .getOrElse { failure ->
                Log.e(TAG, "start failed", failure)
                stop()
                state = SessionState.ERROR
                detail = "${failure.javaClass.simpleName}: ${failure.message}"
                status()
            }
    }

    /**
     * Establishes the session in the order upstream selection requires.
     *
     * @throws IllegalStateException naming the step that failed.
     */
    private fun bringUp(): String {
        TetheringPreferenceApi(context).setPreferTestNetworks(true)
        restartDownstream()

        val group = SessionResources(testNetworkApi, context.connectivityManager())
        resources = group

        val name = group.acquire(tunAddress(), AVAILABILITY_TIMEOUT_MS)
        interfaceName = name
        group.startDatapath(TUN_MTU)

        verifyUpstream(name)

        state = SessionState.ACTIVE
        detail = "tethered clients routing through $name"

        installShutdownHook()
        startWatchdog(name)
        return status()
    }

    /**
     * Drops the downstream if this process exits through System.exit.
     *
     * Covers exactly one path: ShizukuServiceStarter calling System.exit when
     * the server it depends on goes away. That is a real path — it is how the
     * 13.5.4 crash took this process down — but it is the only one.
     *
     * ART does not unwind shutdown hooks on signal death, so SIGTERM and
     * SIGKILL both bypass this entirely; a device test confirmed SIGTERM
     * leaves the hotspot tethered with no hook output. Recovery for those
     * cases lives in the app process, which outlives this one (see
     * SpikeViewModel's session-lost handling).
     */
    private fun installShutdownHook() {
        val hook = Thread {
            Log.w(TAG, "process exiting with session active; dropping downstream")
            runCatching { DownstreamControl(context).stopWifiTethering() }
        }

        runCatching { Runtime.getRuntime().addShutdownHook(hook) }
            .onSuccess { shutdownHook = hook }
            .onFailure { Log.w(TAG, "installShutdownHook: ${it.message}") }
    }

    /** Stops the session if the tunnel stops being the sole upstream (R6.1). */
    private fun startWatchdog(name: String) {
        val guard = SessionWatchdog(name) { problem -> tearDownAfterDrift(problem) }
        watchdog = guard
        guard.start()
    }

    /**
     * Stops the session because the upstream drifted, reporting why.
     *
     * A teardown failure is kept alongside the drift rather than overwritten by
     * it: "the hotspot is still up" is the more dangerous of the two, and it is
     * the one the plain drift message would hide.
     */
    private fun tearDownAfterDrift(problem: String) {
        stop()

        val teardownProblem = detail.takeIf { state == SessionState.ERROR }
        state = SessionState.ERROR
        detail = when (teardownProblem) {
            null -> problem
            else -> "$problem; teardown incomplete: $teardownProblem"
        }
    }

    private fun restartDownstream() {
        val control = DownstreamControl(context)
        control.stopWifiTethering()
        val (didStart, startDetail) = control.startWifiTethering()
        check(didStart) { "restartDownstream: hotspot did not start ($startDetail)" }
    }

    /**
     * R4.3: startup is provisional until the owned TUN is the sole upstream.
     *
     * @throws IllegalStateException so [start] tears down rather than leaving
     *   clients on a physical upstream.
     */
    private fun verifyUpstream(name: String) {
        val deadline = System.currentTimeMillis() + UPSTREAM_SETTLE_MS
        var observed = inspector.observe().interfaceNames

        while (System.currentTimeMillis() < deadline) {
            if (observed.isNotEmpty() && observed.all { it == name }) return
            Thread.sleep(UPSTREAM_POLL_MS)
            observed = inspector.observe().interfaceNames
        }
        error("verifyUpstream: expected only $name, tethering reports $observed")
    }

    /**
     * Releases everything in fail-closed order: downstream first (R6.1).
     *
     * Reports ERROR when the downstream could not be confirmed down. Claiming
     * "stopped" while the hotspot is still broadcasting is the one lie with
     * real consequence here — clients stay connected through the physical
     * upstream, which is what the session exists to prevent.
     */
    fun stop(): String {
        watchdog?.stop()
        watchdog = null
        removeShutdownHook()

        val downstreamProblem = releaseDownstream()

        resources?.release()?.forEach { Log.w(TAG, "stop: $it") }
        resources = null

        runCatching { TetheringPreferenceApi(context).setPreferTestNetworks(false) }
            .onFailure { Log.w(TAG, "stop: preference ${it.message}") }

        interfaceName = null
        state = if (downstreamProblem == null) SessionState.IDLE else SessionState.ERROR
        detail = downstreamProblem ?: "stopped"
        return status()
    }

    /** @return null once no downstream is tethered, else what is still up. */
    private fun releaseDownstream(): String? {
        val control = DownstreamControl(context)

        val didAccept = runCatching { control.stopWifiTethering() }
            .getOrElse { failure ->
                Log.w(TAG, "stop: downstream ${failure.message}")
                false
            }

        val stillTethered = runCatching { DownstreamInspector().findTetheredDownstream() }
            .getOrElse { failure -> "could not verify downstream: ${failure.message}" }

        return when {
            stillTethered != null -> stillTethered
            didAccept -> null
            else -> "stopTethering was rejected, but no downstream remains tethered"
        }
    }

    private fun removeShutdownHook() {
        shutdownHook?.let { hook ->
            // Throws if the JVM is already shutting down, which is exactly when
            // the hook should stay installed.
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
        shutdownHook = null
    }

    fun status(): String = JSONObject().apply {
        put("state", state.name)
        put("detail", detail)
        put("interface", interfaceName ?: JSONObject.NULL)
    }.toString()

    private fun tunAddress() =
        buildLinkAddress(java.net.InetAddress.getByName(TUN_ADDRESS), TUN_PREFIX_LENGTH)

    private companion object {
        const val TAG = "TetherSession"
        const val TUN_ADDRESS = "192.0.2.2"
        const val TUN_PREFIX_LENGTH = 24
        const val TUN_MTU = 1500
        const val AVAILABILITY_TIMEOUT_MS = 10_000
        const val UPSTREAM_SETTLE_MS = 8_000L
        const val UPSTREAM_POLL_MS = 500L
    }
}
