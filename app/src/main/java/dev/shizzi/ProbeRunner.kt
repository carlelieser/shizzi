package dev.shizzi

import android.content.Context
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.util.Log
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
    private val teardown = SessionTeardown(context)
    private var resources: SessionResources? = null

    /**
     * Whether this run started the hotspot, and so owes stopping it.
     *
     * A run with [attemptTethering] false only observes a downstream the user
     * brought up themselves, and stopping that would turn a diagnostic into a
     * destructive action.
     */
    private var didStartDownstream = false

    /**
     * Runs the probe sequence and always releases the session it created.
     *
     * Releasing here rather than only in [teardown] is a correctness fix, not
     * tidiness: teardown is a separate binder call driven by its own button, so
     * an un-torn-down run left its test network alive. Successive runs then
     * stacked orphaned testtun interfaces (17 through 21 across five runs), and
     * the stack's CMD_RETRY_UPSTREAM timer reselected among the leftovers —
     * which is what made Q5 read a different interface than the one it owned.
     */
    fun run(attemptTethering: Boolean, availabilityTimeoutMs: Int): String {
        val report = ProbeReportBuilder()
        report.recordHiddenApiResolutions(testNetworkApi.resolveAll())

        val canProceed = probeIdentityAndPlatform(report)
        try {
            when {
                canProceed -> probeNetworkPath(report, attemptTethering, availabilityTimeoutMs)
                else -> skipRemaining(report, "blocked by Q0/Q1 failure")
            }
        } finally {
            releaseSession(report)
        }

        return report.build(environment())
    }

    /**
     * Undoes everything the run changed, recording problems as T-2 evidence.
     *
     * A silent release would hide exactly the leak the spec's T-2 case is about,
     * so the outcome goes in the report rather than only the log.
     *
     * The order matches [TetherSession.stop] because the same hazards apply.
     * The downstream goes first, fail-closed per R6.1: a hotspot left
     * broadcasting after the TUN is gone falls back to the physical upstream
     * with clients still attached, which is the state this app exists to
     * prevent. The upstream preference is then handed back *while the TUN still
     * exists* — clearing it afterwards leaves tethering naming a destroyed
     * interface as its current upstream, permanently, as SessionTeardown
     * documents. Only then is the interface destroyed.
     *
     * A run used to do none of this: it released its TUN and test network and
     * left both the hotspot up and setPreferTestNetworks(true) set, so the
     * phone kept a hotspot the user had not asked for and the next session
     * started against a preference the diagnostic had turned on.
     */
    private fun releaseSession(report: ProbeReportBuilder) {
        val downstreamProblem = when {
            didStartDownstream -> teardown.releaseDownstream()
            else -> null
        }
        didStartDownstream = false

        teardown.releaseUpstreamSelection(resources?.interfaceName)

        val problems = resources?.release().orEmpty()
        resources = null

        report.recordReleaseProblems(
            when (downstreamProblem) {
                null -> problems
                else -> problems + "downstream: $downstreamProblem"
            },
        )
    }

    /** Q0/Q1: are we actually shell, with the service present? */
    private fun probeIdentityAndPlatform(report: ProbeReportBuilder): Boolean {
        val uid = Process.myUid()
        val isPrivilegedUid = uid == SHELL_UID || uid == ROOT_UID
        report.record(
            id = "Q0",
            question = "Does the privileged service run as shell (2000) or root (0)?",
            outcome = if (isPrivilegedUid) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = "uid=$uid (${if (uid == SHELL_UID) "shell" else if (uid == ROOT_UID) "root" else "unexpected"})",
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

        return isPrivilegedUid && testNetworkApi.isAvailable
    }

    private fun probeNetworkPath(
        report: ProbeReportBuilder,
        attemptTethering: Boolean,
        availabilityTimeoutMs: Int,
    ) {
        val group = SessionResources(testNetworkApi, context.connectivityManager())
        resources = group

        preferTestNetworksBeforeTunExists(report)
        if (attemptTethering) restartDownstreamBeforeTun(report)

        val acquired = runCatching {
            group.acquire(tunAddresses(), TEST_NETWORK_DNS_SERVERS, availabilityTimeoutMs)
        }

        acquired.fold(
            onSuccess = { name -> onTunAcquired(report, name, attemptTethering) },
            onFailure = { failure -> onTunFailed(report, failure) },
        )
    }

    /**
     * Sets the test-network preference before the TUN exists.
     *
     * Tethering.setPreferTestNetworks only writes the flag — the disassembly is
     * a Handler.post of setPreferTestNetworks then sendTetherResult, with no
     * reselection. Upstream selection re-runs only on an event: a network
     * arriving or leaving, or the retry timer.
     *
     * So the flag must already be true when the test network arrives, or that
     * arrival is evaluated with it false and nothing re-evaluates afterwards.
     * Setting it here makes the TUN's own onAvailable the event that selects
     * it. Q8 confirmed that event does reach the monitor.
     *
     * Q4 still records the call the spec asks about (R4.1); this earlier call
     * is about ordering, not about whether the API is accepted.
     */
    private fun preferTestNetworksBeforeTunExists(report: ProbeReportBuilder) {
        runCatching { TetheringPreferenceApi(context).setPreferTestNetworks(true) }
            .onFailure { failure ->
                report.recordFail(
                    "Q4pre",
                    "Can the preference be set before the TUN is created?",
                    "${failure.javaClass.simpleName}: ${failure.message}",
                )
            }
    }

    /**
     * Restarts the downstream before the TUN exists, so its arrival is seen.
     *
     * startTethering runs startObserveUpstreamNetworks, which calls stop()
     * first: that unregisters the listen callback and clears mNetworkMap, then
     * registers a fresh one. A TUN created beforehand has already fired its
     * onAvailable, so it is dropped by the wipe and never re-delivered — the
     * previous run showed exactly that, with the TUN absent from the upstream
     * quota table for the first time.
     *
     * Creating the TUN after the restart puts its arrival inside the new
     * callback's window, which is the only way both conditions hold at once:
     * the preference already true, and the network present in the map.
     */
    private fun restartDownstreamBeforeTun(report: ProbeReportBuilder) {
        val control = DownstreamControl(context)
        val didStop = control.stopWifiTethering()
        val (didStart, startDetail) = control.startWifiTethering()

        // Recorded even when the start is rejected: "stopped, then failed to
        // start" still leaves the radio in a state the run changed, and the
        // release path should confirm it is down rather than assume it.
        didStartDownstream = didStop || didStart

        if (!didStart) {
            report.recordFail(
                "Q5pre",
                "Can the downstream restart before the TUN is created?",
                "stopped=$didStop, opPackage=${control.opPackageName}, start=$startDetail",
            )
        }
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
        probeUpstreamEligibility(report)
        probeDatapath(report)
        probeUpstreamCallback(report, interfaceName)
        probeTetheringPreference(report, interfaceName, attemptTethering)
    }

    /**
     * Starts the userspace stack before tethering begins.
     *
     * Ordering matters: clients that associate before the datapath is reading
     * would send into a TUN nothing drains, so the stack comes up first.
     */
    private fun probeDatapath(report: ProbeReportBuilder) {
        val group = resources
        if (group == null) {
            report.recordSkip("Q7", QUESTION_DATAPATH, "no session resources")
            return
        }

        runCatching { group.startDatapath(TUN_MTU) }.fold(
            onSuccess = {
                report.recordPass("Q7", QUESTION_DATAPATH, "netstack attached to TUN fd, mtu=$TUN_MTU")
            },
            onFailure = { failure ->
                report.recordFail("Q7", QUESTION_DATAPATH, "${failure.javaClass.simpleName}: ${failure.message}")
            },
        )
    }

    /**
     * Q8: does a listen callback ever fire for the test network?
     *
     * UpstreamNetworkMonitor populates mNetworkMap only from its own
     * NetworkCallback: handleAvailable is what inserts, and handleNetCap
     * returns early when the network is not already in the map. If onAvailable
     * never arrives, findFirstTestNetwork has nothing to find no matter what
     * mPreferTestNetworks says — which is exactly the observed failure.
     *
     * This registers the same request the monitor builds on API 33+
     * (clearCapabilities, forbidding LOCAL_NETWORK) and reports whether the
     * framework delivers our TUN through it. That is the value every previous
     * theory assumed and none measured.
     */
    private fun probeUpstreamCallback(report: ProbeReportBuilder, interfaceName: String) {
        val network = resources?.acquiredNetwork
        if (network == null) {
            report.recordSkip("Q8", QUESTION_CALLBACK, "no test network")
            return
        }

        val observed = awaitCallbackDelivery(interfaceName)
        report.record(
            id = "Q8",
            question = QUESTION_CALLBACK,
            outcome = if (observed.first) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = observed.second,
        )
    }

    /**
     * Registers the monitor's listen request and waits for our TUN to arrive.
     *
     * Returns whether it was delivered, plus the verbatim outcome: a rejected
     * registration and a silent non-delivery are different findings and must
     * not be reported the same way.
     */
    private fun awaitCallbackDelivery(interfaceName: String): Pair<Boolean, String> {
        val manager = context.connectivityManager()
        val latch = java.util.concurrent.CountDownLatch(1)
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())

        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(available: Network) {
                val name = manager.getLinkProperties(available)?.interfaceName ?: "?"
                seen += name
                if (name == interfaceName) latch.countDown()
            }
        }

        val request = android.net.NetworkRequest.Builder()
            .clearCapabilities()
            .build()

        return runCatching {
            manager.registerNetworkCallback(request, callback)
            val delivered = latch.await(CALLBACK_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            runCatching { manager.unregisterNetworkCallback(callback) }

            delivered to when {
                delivered -> "onAvailable delivered $interfaceName; all seen=$seen"
                else -> "onAvailable never delivered $interfaceName within ${CALLBACK_WAIT_MS}ms; " +
                    "all seen=$seen"
            }
        }.getOrElse { failure ->
            false to "registerNetworkCallback rejected: " +
                "${failure.javaClass.simpleName}: ${failure.message}"
        }
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
        report.recordSkip("Q3b", QUESTION_ELIGIBILITY, "no test network")
        report.recordSkip("Q8", QUESTION_CALLBACK, "no test network")
        report.recordSkip("Q4", QUESTION_PREFER, "no test network")
        report.recordSkip("Q5", QUESTION_UPSTREAM, "no test network")
        report.recordSkip("Q6", QUESTION_IPV6, "no test network")
    }

    /**
     * Q3b: is the test network recognisable to the stack's test-network branch?
     *
     * UpstreamNetworkMonitor.getCurrentPreferredUpstream on this build reads:
     *
     *     if (mPreferTestNetworks) {
     *         state = findFirstTestNetwork(mNetworkMap.values());
     *         if (state != null) return state;
     *     }
     *
     * The branch returns before any INTERNET, cellular, or DUN check, and
     * findFirstTestNetwork filters on TRANSPORT_TEST alone. So the eligibility
     * criterion is the test transport, not NET_CAPABILITY_INTERNET — which
     * TestNetworkService never grants and exposes no parameter to add.
     *
     * INTERNET is still reported: absent it, the network is unusable as a
     * general default route, which constrains the datapath design even though
     * it does not block tethering upstream selection.
     */
    private fun probeUpstreamEligibility(report: ProbeReportBuilder) {
        val network = resources?.acquiredNetwork
        if (network == null) {
            report.recordSkip("Q3b", QUESTION_ELIGIBILITY, "no test network")
            return
        }

        val capabilities = context.connectivityManager().getNetworkCapabilities(network)
        if (capabilities == null) {
            report.recordFail("Q3b", QUESTION_ELIGIBILITY, "getNetworkCapabilities returned null")
            return
        }

        val hasTestTransport = capabilities.hasTransport(resolveTransportTest())
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        report.record(
            id = "Q3b",
            question = QUESTION_ELIGIBILITY,
            outcome = if (hasTestTransport) ProbeOutcome.PASS else ProbeOutcome.FAIL,
            detail = "TRANSPORT_TEST=$hasTestTransport (the selection criterion); " +
                "INTERNET=$hasInternet (informational: never granted by " +
                "TestNetworkService); raw=$capabilities",
        )
    }

    /**
     * Sets the preference, then observes selection either side of a restart.
     *
     * [attemptTethering] false no longer skips Q5. Q5 passed once on a run where
     * the downstream was already up and no restart happened, and has failed on
     * every run that restarted it — so "observe without restarting" is the
     * comparison that separates those two cases. Skipping the probe measured
     * nothing and hid the one condition under which it is known to work.
     */
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

        // The restart, when requested, already happened before the TUN was
        // created, so Q5 only observes. Restarting again here would wipe the
        // map entry this ordering exists to preserve.
        val restartDetail = when {
            attemptTethering -> "downstream restarted before TUN creation"
            else -> "no restart: observing the running downstream"
        }
        observeUpstream(report, interfaceName, restartDetail)
        probeIpv6Surface(report)
    }

    /**
     * Q5 is the actual viability answer: does the tethering stack select our TUN?
     *
     * The probe does not start tethering itself — the user enables the hotspot
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
                append("timedOut=${observation.didTimeout}\n--- selection lines ---\n")
                append(selectionLines(observation.rawOutput))
            },
        )
    }

    /**
     * Keeps only the dumpsys lines that bear on upstream selection.
     *
     * The raw dump opens with several KB of static tetherable-regex and DHCP
     * config, so a leading excerpt spent its whole budget before reaching
     * anything decision-relevant. These keys carry the actual signal: the chosen
     * upstream, the quota table the TUN was missing from, and the DUN and
     * automatic-selection flags that constrain the choice.
     */
    private fun selectionLines(rawOutput: String): String =
        rawOutput.lineSequence()
            .filter { line -> SELECTION_KEYS.any { key -> line.contains(key) } }
            .joinToString("\n")
            .take(DUMP_EXCERPT_CHARS)

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
            Log.i(TAG, "awaitUpstreamSettle: waiting for $interfaceName, saw ${latest.interfaceNames}")

            Thread.sleep(UPSTREAM_POLL_MS)
            latest = inspector.observe()
        }
        return latest
    }

    /**
     * Q6: can IPv6 be suppressed on the downstream?
     *
     * Spec R6.4 requires IPv6 blocked or unadvertised, and L-7 is unpassable if
     * shell cannot influence it. This only reports the current forwarding and
     * RA state so the answer is known before Phase 6 depends on it.
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
                "Reported for Phase 6 planning; suppression not attempted.",
        )
    }

    private fun readProcValue(path: String): String? =
        runCatching { java.io.File(path).readText().trim() }.getOrNull()

    /**
     * Releases anything a run left behind, for the session's stop path.
     *
     * Normally a no-op: [run] releases in its own finally, so this only has
     * work to do when a run died in a way that skipped it. It stays because
     * TetherService.stop calls it to make sure a diagnostic's resources cannot
     * outlive an unrelated session teardown.
     *
     * Fail-closed order, as in [releaseSession]: the caller stops the session's
     * own downstream before this runs (R6.1), and this hands the upstream back
     * before the TUN is destroyed.
     */
    fun teardown(): String {
        val downstreamProblem = when {
            didStartDownstream -> teardown.releaseDownstream()
            else -> null
        }
        didStartDownstream = false

        val restored = runCatching {
            teardown.releaseUpstreamSelection(resources?.interfaceName)
        }
        val problems = resources?.release() ?: emptyList()
        resources = null

        val result = JSONObject()
        result.put("preferTestNetworksRestored", restored.isSuccess)
        restored.exceptionOrNull()?.let { result.put("restoreError", it.message) }
        downstreamProblem?.let { result.put("downstreamProblem", it) }
        result.put("teardownProblems", org.json.JSONArray(problems))
        return result.toString(2)
    }

    private fun skipRemaining(report: ProbeReportBuilder, reason: String) {
        listOf(
            "Q2" to "Does createTunInterface() return a usable TUN?",
            "Q3" to "Does setupTestNetwork() produce an available network?",
            "Q3b" to QUESTION_ELIGIBILITY,
            "Q7" to QUESTION_DATAPATH,
            "Q8" to QUESTION_CALLBACK,
            "Q4" to QUESTION_PREFER,
            "Q5" to QUESTION_UPSTREAM,
            "Q6" to QUESTION_IPV6,
        ).forEach { (id, question) -> report.recordSkip(id, question, reason) }
    }

    private fun tunAddresses(): List<LinkAddress> = listOf(
        buildLinkAddress(InetAddress.getByName(TUN_ADDRESS), TUN_PREFIX_LENGTH),
        buildLinkAddress(InetAddress.getByName(TUN_ADDRESS_V6), TUN_PREFIX_LENGTH_V6),
    )

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

        /** TEST-NET-1 per spec R3.2. */
        const val TUN_ADDRESS = "192.0.2.2"
        const val TUN_PREFIX_LENGTH = 24

        /** The IPv6 counterpart, from the documentation range so it cannot collide. */
        const val TUN_ADDRESS_V6 = "2001:db8::2"
        const val TUN_PREFIX_LENGTH_V6 = 64


        /**
         * Link MTU for the TUN (R5.5 default). Egress is direct rather than
         * tunnelled, so there is no encapsulation overhead to subtract yet.
         */
        const val TUN_MTU = 1500

        const val DUMP_EXCERPT_CHARS = 4000

        /** dumpsys tethering substrings that bear on which upstream is chosen. */
        val SELECTION_KEYS = listOf(
            "Current upstream",
            "Upstream wanted",
            "Upstream quota",
            "isDunRequired",
            "chooseUpstreamAutomatically",
            "Exempted",
            "testtun",
        )

        /**
         * R4.4 requires waiting for the framework to settle but fixes no
         * duration. 15s is long enough to cover an observed reselection and
         * short enough to keep a failing run interactive; E-2's 20-cycle bar
         * should be used to tune it.
         */
        /**
         * Device logs show selection reaching the TUN several seconds after the
         * downstream restart, and a 15s window closed first: Q5 read wlan0 while
         * the dump's own log showed "Found upstream interface(s): [testtun18]"
         * from the run before. The test network carries no INTERNET capability,
         * so it settles more slowly than a validated Wi-Fi upstream.
         */
        /**
         * How long Q5 waits for upstream selection to settle.
         *
         * Was 45s, chasing a timing theory that proved wrong: when selection
         * picks the TUN it does so in ~100ms, and the loop returns as soon as
         * it sees that. The deadline is therefore only ever paid in full on
         * failure, which made every failing run cost 45s to learn what the
         * first second already showed.
         */
        const val UPSTREAM_SETTLE_MS = 8_000L
        const val UPSTREAM_POLL_MS = 1_000L
        const val TAG = "ProbeRunner"
        const val IPV6_FORWARDING_PATH = "/proc/sys/net/ipv6/conf/all/forwarding"
        const val IPV6_ACCEPT_RA_PATH = "/proc/sys/net/ipv6/conf/all/accept_ra"

        const val QUESTION_PREFER = "Does TetheringManager.setPreferTestNetworks exist and accept the call?"
        const val QUESTION_UPSTREAM = "Does the tethering stack report the owned testtunN as sole upstream?"
        const val QUESTION_ELIGIBILITY =
            "Does the test network carry the capabilities tethering requires of an upstream?"
        const val QUESTION_IPV6 = "Is the downstream IPv6 state observable for R6.4 planning?"
        const val QUESTION_DATAPATH = "Does the userspace stack attach to the TUN fd?"
        const val QUESTION_CALLBACK =
            "Does a NetworkCallback listen ever deliver the test network? " +
                "(this is what populates UpstreamNetworkMonitor.mNetworkMap)"

        /** Selection happens in ~100ms when it works; 5s is generous. */
        const val CALLBACK_WAIT_MS = 5_000L
    }
}
