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
    private var vpnMode = VpnMode.AUTO

    @Volatile
    private var usbTethering = UsbTethering.NEVER

    private var watchdog: SessionWatchdog? = null
    private var events: TetheringEvents? = null
    private val teardown = SessionTeardown(context)
    private val downstream = DownstreamInspector()
    private val started = StartedDownstreams()
    private val usbLink = AndroidUsbLink(context, started)

    @Volatile
    private var usbDownstream = UsbDownstream(UsbTethering.NEVER, usbLink)

    private val vpn = VpnUpstream(context) { problem -> tearDownAfter(problem) }

    val isActive: Boolean get() = state == SessionState.ACTIVE

    fun start(mode: VpnMode = VpnMode.AUTO, usb: UsbTethering = UsbTethering.NEVER): String {
        if (isActive) return status()

        vpnMode = mode
        usbTethering = usb
        usbDownstream = UsbDownstream(usb, usbLink, SessionLog::info)
        state = SessionState.STARTING
        SessionLog.info(
            "session start requested (vpn mode ${mode.name.lowercase()}, " +
                "usb tethering ${usb.name.lowercase()})",
        )

        SessionLog.info(
            "device: ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT}), " +
                "contract ${TetherService.CONTRACT_VERSION}",
        )

        val startProblem = usbDownstream.startProblem()
        if (startProblem != null) {
            SessionLog.warn("start refused: $startProblem")
            state = SessionState.ERROR
            detail = startProblem
            return status()
        }

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
        followVpn(group)

        preferTestNetworks()
        val isTethered = restartDownstream()

        if (isTethered || !usbDownstream.canStartUntethered) {
            verifyUpstream(name)
            SessionLog.info("upstream verified: $name is sole upstream")
        }

        state = SessionState.ACTIVE
        activeSince = System.currentTimeMillis()
        detail = "tethered clients routing through $name"

        teardown.installShutdownHook(started)
        startWatchdog(name)
        return status()
    }

    private fun followVpn(group: SessionResources) {
        if (vpnMode == VpnMode.NEVER) {
            SessionLog.info("vpn mode never: datapath left unbound")
            return
        }

        check(vpnMode != VpnMode.ALWAYS || vpn.isVpnPresent()) {
            "followVpn: vpn mode always requires an active VPN, none is connected"
        }
        vpn.follow(group)
    }

    private fun startWatchdog(name: String) {
        val checkUsb = { tethering: UpstreamObservation ->
            val usbReading = readDownstream(tethering, listOf(TetheringType.USB))
            usbDownstream.check(System.currentTimeMillis(), usbReading)
        }
        val guard = SessionWatchdog(name, checkDownstream = checkUsb, onEnd = ::endFromWatchdog)
        watchdog = guard
        guard.start()

        if (usbTethering == UsbTethering.NEVER) return
        events = TetheringEvents(context, guard::wake).also(TetheringEvents::register)
    }

    private fun endFromWatchdog(end: WatchdogEnd) {
        when (end) {
            is WatchdogEnd.Failed -> tearDownAfter(end.problem)
            is WatchdogEnd.Finished -> stop()
        }
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

    /** True once a started downstream is tethered. */
    private fun restartDownstream(): Boolean {
        val control = DownstreamControl(context)
        val types = usbDownstream.typesToStart()
        val usbReading = downstream.findTetheredDownstream(listOf(TetheringType.USB))
        control.stopTethering(typesToRestart(types, usbReading))

        val (succeeded, failed) = types
            .associateWith(control::startTethering)
            .entries
            .partition { (_, outcome) -> outcome.first }
        succeeded.forEach { (type, _) -> started.add(type) }

        val failures = failed.map { (type, outcome) -> "${type.label} did not start (${outcome.second})" }
        check(succeeded.isNotEmpty()) { "restartDownstream: ${failures.joinToString("; ")}" }

        failures.forEach(SessionLog::warn)
        return awaitDownstreamTethered(succeeded.map { it.key })
    }

    private fun awaitDownstreamTethered(types: List<TetheringType>): Boolean {
        val deadline = System.currentTimeMillis() + DOWNSTREAM_SETTLE_MS
        val downstream = DownstreamInspector()

        while (System.currentTimeMillis() < deadline) {

            if (downstream.findTetheredDownstream(types) is DownstreamReading.Tethered) {
                SessionLog.info("downstream tethered")
                return true
            }
            Thread.sleep(DOWNSTREAM_POLL_MS)
        }

        val hint = if (TetheringType.USB in types) " (is a USB cable connected to a computer?)" else ""
        SessionLog.warn("downstream not tethered after ${DOWNSTREAM_SETTLE_MS}ms$hint; continuing")
        return false
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
        events?.unregister()
        events = null
        vpn.stop()
        teardown.removeShutdownHook()

        val summary = if (activeSince == 0L) null else sessionSummary()

        val downstreamProblem = teardown.releaseDownstream(started.types)
        started.clear()

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

        return describeSession(elapsed, traffic)
    }


    fun status(): String = JSONObject().apply {
        put("state", state.name)
        put("detail", detail)
        put("isWaitingForUsb", isActive && usbDownstream.isWaitingForUsb)
        put("interface", interfaceName ?: JSONObject.NULL)

        put("isVpnBound", vpn.isBound)
        put("isVpnBypassed", isVpnBypassed())

        val traffic = interfaceName?.let(InterfaceCounters::read) ?: Traffic()
        put("bytesUp", traffic.up)
        put("bytesDown", traffic.down)
        put("clientCount", if (isActive) downstream.countDevices(usbTethering.types) else 0)
    }.toString()

    private fun isVpnBypassed(): Boolean {
        if (vpnMode != VpnMode.NEVER) return false
        if (!isActive) return false

        return vpn.isVpnPresent()
    }

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
