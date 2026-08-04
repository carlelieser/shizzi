package dev.shizzi.spike

import android.content.Context
import android.net.LinkAddress
import android.os.Build
import android.os.Process
import org.json.JSONObject
import java.net.InetAddress

/**
 * Executes the viability probe sequence inside the Shizuku shell process.
 *
 * Ordering matters: each probe is a precondition for the next, so a failure
 * marks the remainder SKIPPED rather than producing cascading false failures.
 */
class ProbeRunner(private val context: Context) {

    private val testNetworkApi = TestNetworkApi(context)
    private val inspector = UpstreamInspector()
    private var resources: SessionResources? = null

    fun run(attemptTethering: Boolean, availabilityTimeoutMs: Int): String {
        val report = ProbeReportBuilder()
        report.recordHiddenApiResolutions(testNetworkApi.resolveAll())

        val canProceed = probeIdentityAndPlatform(report)
        when {
            canProceed -> probeNetworkPath(report, attemptTethering, availabilityTimeoutMs)
            else -> skipRemaining(report, "blocked by Q0/Q1 failure")
        }

        return report.build(environment())
    }

    /** Q0/Q1: are we actually shell, on a build new enough, with the service present? */
    private fun probeIdentityAndPlatform(report: ProbeReportBuilder): Boolean {
        val uid = Process.myUid()
        val isPrivilegedUid = uid == SHELL_UID || uid == ROOT_UID
        report.record(
            id = "Q0",
            question = "Does the privileged service run as shell (2000) or root (0)?",
            outcome = if (isPrivilegedUid) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = "uid=$uid (${if (uid == SHELL_UID) "shell" else if (uid == ROOT_UID) "root" else "unexpected"})",
        )

        val isSupportedApi = Build.VERSION.SDK_INT >= FEATURE_MIN_API
        report.record(
            id = "Q0b",
            question = "Is the device at or above the API 33 feature floor?",
            outcome = if (isSupportedApi) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = "SDK_INT=${Build.VERSION.SDK_INT}, floor=$FEATURE_MIN_API",
        )

        report.record(
            id = "Q1",
            question = "Is TestNetworkManager reachable from this UID?",
            outcome = if (testNetworkApi.isAvailable) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = when {
                testNetworkApi.isAvailable -> "getSystemService(\"test_network\") returned an instance"
                else -> "test_network service or TestNetworkManager class unavailable"
            },
        )

        return isPrivilegedUid && isSupportedApi && testNetworkApi.isAvailable
    }

    private fun probeNetworkPath(
        report: ProbeReportBuilder,
        attemptTethering: Boolean,
        availabilityTimeoutMs: Int,
    ) {
        val group = SessionResources(testNetworkApi, context.connectivityManager())
        resources = group

        val acquired = runCatching {
            group.acquire(tunAddress(), availabilityTimeoutMs)
        }

        acquired.fold(
            onSuccess = { name -> onTunAcquired(report, name, attemptTethering) },
            onFailure = { failure -> onTunFailed(report, failure) },
        )
    }

    private fun onTunAcquired(
        report: ProbeReportBuilder,
        interfaceName: String,
        attemptTethering: Boolean,
    ) {
        report.recordPass("Q2", "Does createTunInterface() return a usable TUN?", "interface=$interfaceName")
        report.recordPass(
            "Q3",
            "Does setupTestNetwork() produce an available network?",
            "ConnectivityManager reported available; netId handle=${resources?.acquiredNetwork}",
        )
        probeTetheringPreference(report, interfaceName, attemptTethering)
    }

    private fun onTunFailed(report: ProbeReportBuilder, failure: Throwable) {
        val message = "${failure.javaClass.simpleName}: ${failure.message}"
        // Distinguish "TUN never created" from "created but never became available":
        // they point at different framework problems.
        val didCreateTun = resources?.interfaceName != null
        when {
            didCreateTun -> {
                report.recordPass("Q2", "Does createTunInterface() return a usable TUN?", "interface=${resources?.interfaceName}")
                report.recordFail("Q3", "Does setupTestNetwork() produce an available network?", message)
            }

            else -> {
                report.recordFail("Q2", "Does createTunInterface() return a usable TUN?", message)
                report.recordSkip("Q3", "Does setupTestNetwork() produce an available network?", "no TUN to register")
            }
        }
        report.recordSkip("Q4", QUESTION_PREFER, "no test network")
        report.recordSkip("Q5", QUESTION_UPSTREAM, "no test network")
        report.recordSkip("Q6", QUESTION_IPV6, "no test network")
    }

    private fun probeTetheringPreference(
        report: ProbeReportBuilder,
        interfaceName: String,
        attemptTethering: Boolean,
    ) {
        val preferenceApi = TetheringPreferenceApi(context)
        val didSetPreference = runCatching { preferenceApi.setPreferTestNetworks(true) }

        didSetPreference.fold(
            onSuccess = {
                report.recordPass("Q4", QUESTION_PREFER, "setPreferTestNetworks(true) accepted")
            },
            onFailure = { failure ->
                report.recordFail("Q4", QUESTION_PREFER, "${failure.javaClass.simpleName}: ${failure.message}")
            },
        )

        when {
            attemptTethering -> restartDownstreamThenObserve(report, interfaceName)
            else -> report.recordSkip("Q5", QUESTION_UPSTREAM, "tethering probe not requested")
        }
        probeIpv6Surface(report)
    }

    /**
     * Restarts the downstream so upstream selection re-runs under the preference.
     *
     * R4.1 puts setPreferTestNetworks(true) immediately before the downstream
     * starts. Observing without the restart measured the wrong thing: selection
     * had already happened against wlan0 and never re-ran.
     */
    private fun restartDownstreamThenObserve(
        report: ProbeReportBuilder,
        interfaceName: String,
    ) {
        val control = DownstreamControl(context)
        val didStop = control.stopWifiTethering()
        val (didStart, startDetail) = control.startWifiTethering()

        when {
            didStart -> observeUpstream(report, interfaceName, "restart: stopped=$didStop")
            else -> report.recordFail(
                "Q5",
                QUESTION_UPSTREAM,
                "downstream restart failed before upstream could be observed: " +
                    "stopped=$didStop, start=$startDetail",
            )
        }
    }

    /**
     * Q5 is the actual viability answer: does the tethering stack select our TUN?
     *
     * The spike does not start tethering itself — the user enables the hotspot
     * from Settings — so this polls whatever the stack reports until it either
     * settles on the owned TUN or the settle deadline passes.
     */
    private fun observeUpstream(
        report: ProbeReportBuilder,
        interfaceName: String,
        restartDetail: String,
    ) {
        val observation = awaitUpstreamSettle(interfaceName)
        val isOnlyOwnedTun = observation.interfaceNames.isNotEmpty() &&
            observation.interfaceNames.all { it == interfaceName }

        val outcome = when {
            observation.didTimeout -> ProbeOutcome.FAIL
            isOnlyOwnedTun -> ProbeOutcome.PASS
            else -> ProbeOutcome.FAIL
        }

        report.record(
            id = "Q5",
            question = QUESTION_UPSTREAM,
            outcome = outcome,
            detail = buildString {
                append("owned=$interfaceName; ")
                append("observed=${observation.interfaceNames}; ")
                append("$restartDetail; ")
                append("timedOut=${observation.didTimeout}\n--- dumpsys excerpt ---\n")
                append(observation.rawOutput.take(DUMP_EXCERPT_CHARS))
            },
        )
    }

    /**
     * Polls the upstream until it settles on the owned TUN, or the deadline passes.
     *
     * Upstream reselection is asynchronous: setPreferTestNetworks(true) does not
     * move an already-chosen upstream synchronously, and the first device run
     * read wlan0 microseconds after setting the preference. Returning the last
     * observation on timeout keeps the failure detail honest rather than
     * reporting an empty result.
     */
    private fun awaitUpstreamSettle(interfaceName: String): UpstreamObservation {
        val deadline = System.currentTimeMillis() + UPSTREAM_SETTLE_MS
        var latest = inspector.observe()

        while (System.currentTimeMillis() < deadline) {
            val hasSettled = latest.interfaceNames.isNotEmpty() &&
                latest.interfaceNames.all { it == interfaceName }
            if (hasSettled) return latest

            Thread.sleep(UPSTREAM_POLL_MS)
            latest = inspector.observe()
        }
        return latest
    }

    /**
     * Q6: can IPv6 be suppressed on the downstream?
     *
     * Spec R6.4 requires IPv6 blocked or unadvertised, and L-7 is unpassable if
     * shell cannot influence it. The spike only reports the current forwarding
     * and RA state so the answer is known before Phase 6 depends on it.
     */
    private fun probeIpv6Surface(report: ProbeReportBuilder) {
        val forwarding = readProcValue(IPV6_FORWARDING_PATH)
        val acceptRa = readProcValue(IPV6_ACCEPT_RA_PATH)
        val isReadable = forwarding != null

        report.record(
            id = "Q6",
            question = QUESTION_IPV6,
            outcome = if (isReadable) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = "all/forwarding=${forwarding ?: "unreadable"}; " +
                "all/accept_ra=${acceptRa ?: "unreadable"}. " +
                "Reported for Phase 6 planning; suppression not attempted by the spike.",
        )
    }

    private fun readProcValue(path: String): String? =
        runCatching { java.io.File(path).readText().trim() }.getOrNull()

    /** Fail-closed teardown order: caller stops the downstream before this runs (R6.1). */
    fun teardown(): String {
        val restored = runCatching { TetheringPreferenceApi(context).setPreferTestNetworks(false) }
        val problems = resources?.release() ?: emptyList()
        resources = null

        val result = JSONObject()
        result.put("preferTestNetworksRestored", restored.isSuccess)
        restored.exceptionOrNull()?.let { result.put("restoreError", it.message) }
        result.put("teardownProblems", org.json.JSONArray(problems))
        return result.toString(2)
    }

    private fun skipRemaining(report: ProbeReportBuilder, reason: String) {
        listOf(
            "Q2" to "Does createTunInterface() return a usable TUN?",
            "Q3" to "Does setupTestNetwork() produce an available network?",
            "Q4" to QUESTION_PREFER,
            "Q5" to QUESTION_UPSTREAM,
            "Q6" to QUESTION_IPV6,
        ).forEach { (id, question) -> report.recordSkip(id, question, reason) }
    }

    private fun tunAddress(): LinkAddress =
        buildLinkAddress(InetAddress.getByName(TUN_ADDRESS), TUN_PREFIX_LENGTH)

    private fun environment(): JSONObject = JSONObject().apply {
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("fingerprint", Build.FINGERPRINT)
        put("sdkInt", Build.VERSION.SDK_INT)
        put("release", Build.VERSION.RELEASE)
        put("uid", Process.myUid())
        // Must match the UID's package or framework services reject the calls.
        put("contextPackage", context.packageName)
    }

    private companion object {
        const val SHELL_UID = 2000
        const val ROOT_UID = 0
        const val FEATURE_MIN_API = 33

        /** TEST-NET-1 per spec R3.2. */
        const val TUN_ADDRESS = "192.0.2.2"
        const val TUN_PREFIX_LENGTH = 24

        const val DUMP_EXCERPT_CHARS = 4000

        /**
         * R4.4 requires waiting for the framework to settle but fixes no
         * duration. 15s is long enough to cover an observed reselection and
         * short enough to keep a failing run interactive; E-2's 20-cycle bar
         * should be used to tune it.
         */
        const val UPSTREAM_SETTLE_MS = 15_000L
        const val UPSTREAM_POLL_MS = 1_000L
        const val IPV6_FORWARDING_PATH = "/proc/sys/net/ipv6/conf/all/forwarding"
        const val IPV6_ACCEPT_RA_PATH = "/proc/sys/net/ipv6/conf/all/accept_ra"

        const val QUESTION_PREFER = "Does TetheringManager.setPreferTestNetworks exist and accept the call?"
        const val QUESTION_UPSTREAM = "Does the tethering stack report the owned testtunN as sole upstream?"
        const val QUESTION_IPV6 = "Is the downstream IPv6 state observable for R6.4 planning?"
    }
}
