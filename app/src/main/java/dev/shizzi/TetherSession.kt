package dev.shizzi

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject

enum class SessionState { IDLE, STARTING, ACTIVE, ERROR }

class TetherSession(private val context: Context) {

    private val testNetworkApi = TestNetworkApi(context)
    private val inspector = UpstreamInspector()

    private var resources: SessionResources? = null
    private var state = SessionState.IDLE
    private var detail = "not started"
    private var interfaceName: String? = null

    private var activeSince: Long = 0
    private var watchdog: SessionWatchdog? = null
    private val teardown = SessionTeardown(context)
    private val downstream = DownstreamInspector()
    private val vpn = VpnUpstream(context) { problem -> tearDownAfter(problem) }

    val isActive: Boolean get() = state == SessionState.ACTIVE

    fun start(): String {
        if (isActive) return status()

        state = SessionState.STARTING
        SessionLog.info("session start requested")

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

    private fun bringUp(): String {
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

    private fun startWatchdog(name: String) {
        val guard = SessionWatchdog(name) { problem ->
            SessionLog.warn("upstream drift: $problem")
            tearDownAfter(problem)
        }
        watchdog = guard
        guard.start()
    }

    private fun tearDownAfter(problem: String) {
        stop()

        val teardownProblem = detail.takeIf { state == SessionState.ERROR }
        state = SessionState.ERROR
        detail = when (teardownProblem) {
            null -> problem
            else -> "$problem; teardown incomplete: $teardownProblem"
        }
    }

    private fun preferTestNetworks() {
        val api = TetheringPreferenceApi(context)
        runCatching { api.setPreferTestNetworks(false) }
            .onFailure { SessionLog.warn("could not clear the stale test-network preference: ${it.message}") }
        api.setPreferTestNetworks(true)
    }

    private fun restartDownstream() {
        val control = DownstreamControl(context)
        control.stopWifiTethering()

        val (didStart, startDetail) = control.startWifiTethering()
        check(didStart) { "restartDownstream: hotspot did not start ($startDetail)" }

        awaitDownstreamTethered()
    }

    private fun awaitDownstreamTethered() {
        val deadline = System.currentTimeMillis() + DOWNSTREAM_SETTLE_MS
        val downstream = DownstreamInspector()

        while (System.currentTimeMillis() < deadline) {

            if (downstream.findTetheredDownstream() != null) {
                SessionLog.info("downstream tethered")
                return
            }
            Thread.sleep(DOWNSTREAM_POLL_MS)
        }

        SessionLog.warn("downstream not tethered after ${DOWNSTREAM_SETTLE_MS}ms; continuing")
    }

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

    fun stop(): String {
        watchdog?.stop()
        watchdog = null
        vpn.stop()
        teardown.removeShutdownHook()

        val summary = if (activeSince == 0L) null else sessionSummary()

        val downstreamProblem = teardown.releaseDownstream()

        teardown.releaseUpstreamSelection(interfaceName)

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

    private fun sessionSummary(): String {
        val traffic = interfaceName?.let(InterfaceCounters::read) ?: Traffic()
        val elapsed = when (activeSince) {
            0L -> 0L
            else -> System.currentTimeMillis() - activeSince
        }

        return "session summary: active ${formatDuration(elapsed)}, " +
            "${Traffic.format(traffic.up)} up, ${Traffic.format(traffic.down)} down"
    }

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

        put("isVpnBound", vpn.isBound)

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

        const val TUN_ADDRESS_V6 = "2001:db8::2"
        const val TUN_PREFIX_LENGTH_V6 = 64

        const val TUN_MTU = 1500
        const val AVAILABILITY_TIMEOUT_MS = 10_000
        const val UPSTREAM_SETTLE_MS = 8_000L
        const val UPSTREAM_POLL_MS = 500L

        const val DOWNSTREAM_SETTLE_MS = 10_000L
        const val DOWNSTREAM_POLL_MS = 500L
    }
}
