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
        SessionLog.info("session start requested")

        return runCatching { bringUp() }
            .getOrElse { failure ->
                Log.e(TAG, "start failed", failure)
                SessionLog.error(
                    "start failed: ${failure.javaClass.simpleName}: ${failure.message}",
                )
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
        // The TUN is built before the downstream, so a live test network exists
        // at the moment tethering goes looking for an upstream. Starting the
        // hotspot first makes it select from whatever test networks it already
        // knows about — on a device carrying a stale entry from a previous boot
        // that is the dead interface, and it holds the slot the real TUN needs.
        val group = SessionResources(testNetworkApi, context.connectivityManager())
        resources = group

        val name = group.acquire(tunAddress(), AVAILABILITY_TIMEOUT_MS)
        interfaceName = name
        SessionLog.info("tun up: $name")

        group.startDatapath(TUN_MTU)

        preferTestNetworks()
        restartDownstream()

        verifyUpstream(name)
        SessionLog.info("upstream verified: $name is sole upstream")

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
        SessionLog.warn("upstream drift: $problem")
        stop()

        val teardownProblem = detail.takeIf { state == SessionState.ERROR }
        state = SessionState.ERROR
        detail = when (teardownProblem) {
            null -> problem
            else -> "$problem; teardown incomplete: $teardownProblem"
        }
    }

    /**
     * Asks tethering to prefer test networks, forcing a real state transition.
     *
     * Set after the TUN exists, and driven false-then-true rather than straight
     * to true. The framework acts on the *change*: if the preference is already
     * true — which it is on the first start after a stop, since teardown clears
     * it but a start that never ran teardown does not — setting it to true again
     * is a no-op and no reselection happens. The new TUN then appears with
     * nothing prompting tethering to look at it, and it keeps whatever it had
     * cached.
     *
     * That was the intermittent failure: stop, start, and the upstream never
     * moves off the previous selection for the full verify window, while the
     * next start works because its teardown had just cleared the preference.
     */
    private fun preferTestNetworks() {
        val api = TetheringPreferenceApi(context)
        runCatching { api.setPreferTestNetworks(false) }
            .onFailure { Log.w(TAG, "preferTestNetworks: clear ${it.message}") }
        api.setPreferTestNetworks(true)
    }

    /**
     * Brings the hotspot up, cycling it first if one is already running.
     *
     * The user does not have to have enabled tethering beforehand: a session is
     * a request to share this connection, and requiring them to turn the
     * hotspot on first — then failing with an upstream error if they did not —
     * makes the app's internal sequencing their problem.
     */
    private fun restartDownstream() {
        val control = DownstreamControl(context)
        control.stopWifiTethering()

        val (didStart, startDetail) = control.startWifiTethering()
        check(didStart) { "restartDownstream: hotspot did not start ($startDetail)" }

        awaitDownstreamTethered()
    }

    /**
     * Waits for the hotspot to actually be tethered, not merely requested.
     *
     * startWifiTethering returns once the request is accepted, which is well
     * before the downstream is up. Proceeding on the acceptance alone builds
     * the TUN while tethering has no downstream to serve — it never starts
     * looking for an upstream, so `Upstream wanted` stays false and
     * verifyUpstream times out against an empty list. That was every "tethering
     * reports []" failure on the device.
     *
     * Bounded, and a timeout is not fatal here: verifyUpstream is the real
     * gate, and it reports the upstream truthfully whether or not this settled
     * in time.
     */
    private fun awaitDownstreamTethered() {
        val deadline = System.currentTimeMillis() + DOWNSTREAM_SETTLE_MS
        val downstream = DownstreamInspector()

        while (System.currentTimeMillis() < deadline) {
            // Non-null means something *is* tethered, which is what this waits
            // for -- the same reading means "still up" during teardown.
            if (downstream.findTetheredDownstream() != null) {
                SessionLog.info("downstream tethered")
                return
            }
            Thread.sleep(DOWNSTREAM_POLL_MS)
        }

        SessionLog.warn("downstream not tethered after ${DOWNSTREAM_SETTLE_MS}ms; continuing")
    }

    /**
     * R4.3: startup is provisional until the owned TUN is the sole upstream.
     *
     * @throws IllegalStateException so [start] tears down rather than leaving
     *   clients on a physical upstream.
     */
    private fun verifyUpstream(name: String) {
        val deadline = System.currentTimeMillis() + UPSTREAM_SETTLE_MS
        var observed = liveUpstreams(name)

        while (System.currentTimeMillis() < deadline) {
            if (observed.isNotEmpty() && observed.all { it == name }) return
            Thread.sleep(UPSTREAM_POLL_MS)
            observed = liveUpstreams(name)
        }
        error("verifyUpstream: expected only $name, tethering reports $observed")
    }

    private fun liveUpstreams(owned: String): List<String> =
        inspector.observe().liveInterfaceNames(owned)

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

        releaseUpstreamSelection()

        resources?.release()?.forEach { Log.w(TAG, "stop: $it") }
        resources = null

        interfaceName = null
        state = if (downstreamProblem == null) SessionState.IDLE else SessionState.ERROR
        detail = downstreamProblem ?: "stopped"

        when (downstreamProblem) {
            null -> SessionLog.info("session stopped; downstream confirmed down")
            else -> SessionLog.error("teardown: $downstreamProblem")
        }
        return status()
    }

    /**
     * Hands the upstream back to the framework *before* the TUN is destroyed.
     *
     * This ordering is load-bearing. Destroying the interface first leaves
     * tethering holding a reference it can no longer act on: its own teardown
     * needs the interface to exist, so it logs
     *
     *     [BpfCoordinator] Could not detach program: Fail to get interface
     *     params for interface testtunNN
     *
     * and keeps naming that dead interface as the current upstream. Every
     * subsequent start then fails verifyUpstream against a ghost, permanently,
     * with nothing the app can do about it afterward and no way for a user to
     * clear it short of a reboot. A device test wedged a phone exactly this way
     * for eleven consecutive sessions.
     *
     * So the preference is cleared while the TUN is still up, and this waits
     * for tethering to actually move off it. The wait is bounded and best
     * effort: if the framework has not let go by the deadline, destroying the
     * interface is still better than leaking it, and the next start reselects
     * from whatever remains.
     */
    private fun releaseUpstreamSelection() {
        val cleared = runCatching { TetheringPreferenceApi(context).setPreferTestNetworks(false) }
            .onFailure { Log.w(TAG, "stop: preference ${it.message}") }
        if (cleared.isFailure) return

        val name = interfaceName ?: return
        val deadline = System.currentTimeMillis() + UPSTREAM_RELEASE_MS

        while (System.currentTimeMillis() < deadline) {
            val observed = runCatching { inspector.observe().interfaceNames }.getOrDefault(emptyList())
            if (observed.none { it == name }) {
                SessionLog.info("upstream released: tethering moved off $name")
                return
            }
            Thread.sleep(UPSTREAM_POLL_MS)
        }

        SessionLog.warn("upstream still reads $name after ${UPSTREAM_RELEASE_MS}ms; releasing anyway")
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

        /**
         * How long teardown waits for tethering to release the owned TUN.
         *
         * Shorter than the settle deadline: this is the framework letting go of
         * an interface rather than selecting and validating a new one, and the
         * session is already on its way out. Exceeding it is logged and the
         * interface destroyed regardless — leaking the TUN would be worse.
         */
        const val UPSTREAM_RELEASE_MS = 4_000L

        /**
         * How long to wait for the hotspot to come up before building the TUN.
         *
         * Generous because this covers a cold start of the Wi-Fi AP, which on
         * a real device takes seconds rather than milliseconds.
         */
        const val DOWNSTREAM_SETTLE_MS = 10_000L
        const val DOWNSTREAM_POLL_MS = 500L
    }
}
