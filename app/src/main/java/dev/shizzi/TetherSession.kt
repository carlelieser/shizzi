package dev.shizzi

import android.content.Context
import android.os.Build
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

    /** When the session went ACTIVE, for the summary [stop] writes. */
    private var activeSince: Long = 0
    private var watchdog: SessionWatchdog? = null
    private val teardown = SessionTeardown(context)
    private val downstream = DownstreamInspector()
    private val vpn = VpnUpstream(context) { problem -> tearDownAfter(problem) }

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
        // The first thing worth knowing about a log someone sends in: which
        // device and build produced it, and whether the shell process matches
        // the app that is talking to it.
        SessionLog.info(
            "device: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT}), " +
                "contract ${TetherService.CONTRACT_VERSION}",
        )

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

        val name = group.acquire(tunAddresses(), TEST_NETWORK_DNS_SERVERS, AVAILABILITY_TIMEOUT_MS)
        interfaceName = name
        SessionLog.info("tun up: $name (mtu $TUN_MTU, $TUN_ADDRESS, $TUN_ADDRESS_V6)")

        group.startDatapath(TUN_MTU)
        SessionLog.info("datapath attached to $name")
        vpn.follow(group)

        preferTestNetworks()
        restartDownstream()

        verifyUpstream(name)
        SessionLog.info("upstream verified: $name is sole upstream")

        state = SessionState.ACTIVE
        activeSince = System.currentTimeMillis()
        detail = "tethered clients routing through $name"

        teardown.installShutdownHook()
        startWatchdog(name)
        return status()
    }

    /** Stops the session if the tunnel stops being the sole upstream (R6.1). */
    private fun startWatchdog(name: String) {
        val guard = SessionWatchdog(name) { problem ->
            SessionLog.warn("upstream drift: $problem")
            tearDownAfter(problem)
        }
        watchdog = guard
        guard.start()
    }

    /**
     * Stops the session because something it depends on went away.
     *
     * A teardown failure is kept alongside [problem] rather than overwritten by
     * it: "the hotspot is still up" is the more dangerous of the two, and it is
     * the one the plain message would hide.
     *
     * The caller logs its own cause — upstream drift and VPN loss are different
     * failures and each names itself before handing over.
     */
    private fun tearDownAfter(problem: String) {
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
            .onFailure { SessionLog.warn("could not clear the stale test-network preference: ${it.message}") }
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
        vpn.stop()
        teardown.removeShutdownHook()

        // Read before anything is released: the counters come from
        // /proc/net/dev, which stops knowing the interface once it is gone.
        // Null when the session never went active, whose summary would be all
        // zeroes on top of the failure the user is actually reading.
        val summary = if (activeSince == 0L) null else sessionSummary()

        val downstreamProblem = teardown.releaseDownstream()

        teardown.releaseUpstreamSelection(interfaceName)

        // release() already logs each problem; this keeps the count in the
        // teardown's own narrative rather than leaving it implicit.
        val releaseProblems = resources?.release().orEmpty()
        resources = null

        interfaceName = null
        activeSince = 0
        state = if (downstreamProblem == null) SessionState.IDLE else SessionState.ERROR
        detail = downstreamProblem ?: "stopped"

        when (downstreamProblem) {
            null -> SessionLog.info("session stopped; downstream confirmed down")
            else -> SessionLog.error("teardown: $downstreamProblem")
        }
        if (releaseProblems.isNotEmpty()) {
            SessionLog.warn("${releaseProblems.size} resource(s) not released cleanly")
        }
        summary?.let(SessionLog::info)
        return status()
    }

    /**
     * What the session did, in one line, for the log.
     *
     * Only for a session that reached ACTIVE. A failed start tears down through
     * the same path, and summarising it would report all zeroes over the error
     * that is the actual finding.
     */
    private fun sessionSummary(): String {
        val traffic = interfaceName?.let(InterfaceCounters::read) ?: Traffic()
        val elapsed = when (activeSince) {
            0L -> 0L
            else -> System.currentTimeMillis() - activeSince
        }

        return "session summary: active ${formatDuration(elapsed)}, " +
            "${Traffic.format(traffic.up)} up, ${Traffic.format(traffic.down)} down"
    }

    /** Whole units only: a log line reporting a session's length is not a stopwatch. */
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    fun status(): String = JSONObject().apply {
        put("state", state.name)
        put("detail", detail)
        put("interface", interfaceName ?: JSONObject.NULL)
        // A boolean rather than the handle: the handle is an opaque framework
        // token, and nothing on the far side of the binder should render it.
        put("isVpnBound", vpn.isBound)

        // Both only while active, and from deliberately different sources: the
        // byte counters are read from /proc in process on every call, while the
        // device count comes from dumpsys behind its own rate limit. That split
        // is what keeps a fast poll affordable.
        val traffic = interfaceName?.let(InterfaceCounters::read) ?: Traffic()
        put("bytesUp", traffic.up)
        put("bytesDown", traffic.down)
        put("clientCount", if (isActive) downstream.countDevices() else 0)
    }.toString()

    private fun tunAddresses() = listOf(
        buildLinkAddress(java.net.InetAddress.getByName(TUN_ADDRESS), TUN_PREFIX_LENGTH),
        buildLinkAddress(java.net.InetAddress.getByName(TUN_ADDRESS_V6), TUN_PREFIX_LENGTH_V6),
    )

    private companion object {
        const val TAG = "TetherSession"
        const val TUN_ADDRESS = "192.0.2.2"
        const val TUN_PREFIX_LENGTH = 24

        /** The IPv6 counterpart, from the documentation range so it cannot collide. */
        const val TUN_ADDRESS_V6 = "2001:db8::2"
        const val TUN_PREFIX_LENGTH_V6 = 64

        const val TUN_MTU = 1500
        const val AVAILABILITY_TIMEOUT_MS = 10_000
        const val UPSTREAM_SETTLE_MS = 8_000L
        const val UPSTREAM_POLL_MS = 500L

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
