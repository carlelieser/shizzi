package dev.shizzi

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import rikka.shizuku.Shizuku

/** The four states the status indicator shows. */
enum class UiStatus { READY, LOADING, CONNECTED, ERROR }

/** What the single screen renders. */
data class SessionUiState(
    val shizukuState: ShizukuState = ShizukuState.NotRunning,
    val status: UiStatus = UiStatus.READY,
    val isBusy: Boolean = false,
    val detail: String = "",
    val interfaceName: String = "",
    val lastError: String = "",
    val isVpnBound: Boolean = false,
    /** How many devices are on the hotspot; 0 unless a session is up. */
    val clientCount: Int = 0,
    /** Bytes carried this session, for the notification to report. */
    val traffic: Traffic = Traffic(),
) {
    /** Shizuku must be ready before the button can do anything. */
    val canStart: Boolean get() = shizukuState is ShizukuState.Ready && !isBusy
}

/**
 * Defined once because both the service and the ViewModel apply it
 * optimistically the moment the user asks to stop, and a copy that forgot to
 * clear [SessionUiState.interfaceName] left "via testtunNN" under an idle
 * screen. Clients and traffic go for the same reason.
 */
fun SessionUiState.asStopped(): SessionUiState = copy(
    isBusy = false,
    status = UiStatus.READY,
    lastError = "",
    detail = "Stopped",
    interfaceName = "",
    isVpnBound = false,
    clientCount = 0,
    traffic = Traffic(),
)

/**
 * Folds a privileged call's result into the rendered state.
 *
 * A thrown exception and a status reporting ERROR are different failures — a
 * dead binder versus the session refusing to come up — so both are surfaced.
 * Lives here rather than in a ViewModel because the session outlives any one
 * screen, so the service folds these results too.
 */
fun SessionUiState.applyOutcome(outcome: Result<String>): SessionUiState {
    val failure = outcome.exceptionOrNull()
    if (failure != null) {
        return copy(
            isBusy = false,
            status = UiStatus.ERROR,
            lastError = "${failure.javaClass.simpleName}: ${failure.message}",
            // A failed start tears its TUN down on the way out; a stale badge
            // would claim a session that no longer exists is on a VPN.
            interfaceName = "",
            isVpnBound = false,
            clientCount = 0,
            traffic = Traffic(),
        )
    }

    val parsed = runCatching { JSONObject(outcome.getOrDefault("{}")) }.getOrNull()
    val sessionState = parsed?.optString("state").orEmpty()
    val sessionDetail = parsed?.optString("detail").orEmpty()

    return copy(
        isBusy = false,
        status = statusFor(sessionState),
        detail = sessionDetail,
        interfaceName = parsed?.optString("interface").orEmpty().takeIf { it != "null" }.orEmpty(),
        lastError = if (sessionState == "ERROR") sessionDetail else "",
        // Absent on an older shell process, which reads correctly as unbound.
        isVpnBound = parsed?.optBoolean("isVpnBound") == true,
        clientCount = parsed?.optInt("clientCount") ?: 0,
        traffic = Traffic(
            up = parsed?.optLong("bytesUp") ?: 0,
            down = parsed?.optLong("bytesDown") ?: 0,
        ),
    )
}

private fun statusFor(sessionState: String): UiStatus = when (sessionState) {
    "ACTIVE" -> UiStatus.CONNECTED
    "ERROR" -> UiStatus.ERROR
    "STARTING" -> UiStatus.LOADING
    else -> UiStatus.READY
}

/**
 * Drives the privileged service from the app process.
 *
 * All calls serialize behind [isBusy] in the ViewModel (R7.6); this class holds
 * no lock of its own and assumes single-threaded entry from the UI scope.
 */
class TetherClient {

    private var boundService: ITetherService? = null
    private var pendingBind: CompletableDeferred<ITetherService>? = null

    /**
     * The app process outlives the shell process — it stayed alive at
     * oom_score_adj 0 through every observed death — so without this the UI
     * keeps showing CONNECTED for a session that no longer exists.
     */
    var onSessionLost: (() -> Unit)? = null

    private var deathRecipient: IBinder.DeathRecipient? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, TetherService::class.java.name),
    )
        // The test network is tied to a Binder token held in this process, and
        // a non-daemon service is reaped once the call returns, taking the
        // network with it: "TestNetworkAgent: NetworkAgent channel lost" about
        // four seconds after start() returned, with the upstream reverting.
        .daemon(true)
        // Without a tag Shizuku keys each service record by a fresh UUID, so
        // every bind creates a *new* daemon that unbind cannot reach — leaking
        // a shell process per session, each holding its TUN's fd and keeping
        // the interface alive in the kernel.
        .tag(SERVICE_TAG)
        .processNameSuffix("probe")
        // Shizuku turns this into "-Xcompiler-option --debuggable
        // -XjdwpProvider:adbconnection" on the app_process command line, which
        // costs the shell process its AOT code for the whole of its life. Worth
        // it to attach a debugger to it; not worth it on a user's phone, where
        // it only makes a start that is already the slowest part slower.
        .debuggable(BuildConfig.DEBUG)
        .version(TetherService.CONTRACT_VERSION)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = binder?.let { ITetherService.Stub.asInterface(it) }
            when (service) {
                null -> pendingBind?.completeExceptionally(
                    IllegalStateException("bindUserService: Shizuku returned a null binder"),
                )

                else -> {
                    boundService = service
                    pendingBind?.complete(service)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }

    /**
     * Reuses a live binding; discards a dead one, which after an APK update is
     * a stale shell process (R2.5, T-5).
     */
    private suspend fun service(): ITetherService {
        boundService?.let { existing ->
            if (existing.asBinder().pingBinder()) return existing
            boundService = null
        }

        val deferred = CompletableDeferred<ITetherService>()
        pendingBind = deferred
        Shizuku.bindUserService(userServiceArgs, connection)

        val bound = awaitBind(deferred)
        observeDeath(bound)
        return bound
    }

    /**
     * Restates a timeout as what actually went wrong.
     *
     * R8 renames TimeoutCancellationException, so the class name the UI shows
     * is a letter and a digit and the message is kotlinx's, which knows nothing
     * about what was being awaited. Every distinct way the shell process can
     * fail to appear -- never started, started and exited, still starting --
     * reached the user as the same uninformative string.
     *
     * IllegalStateException survives minification with its name intact, being
     * a platform class.
     */
    private suspend fun awaitBind(deferred: CompletableDeferred<ITetherService>): ITetherService =
        try {
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        } catch (timeout: TimeoutCancellationException) {
            pendingBind = null
            throw IllegalStateException(
                "bindUserService: Shizuku returned no binder for the privileged " +
                    "helper within ${BIND_TIMEOUT_MS}ms. The shell process either " +
                    "never started or exited before handing one back; " +
                    "`adb logcat -s ShizukuServiceStarter:*` carries the reason.",
                timeout,
            )
        }

    /**
     * onServiceDisconnected does not cover this: the shell process is a child
     * of the Shizuku server, and when that server dies the child exits without
     * the binding being torn down. Linking to the binder catches both.
     */
    private fun observeDeath(bound: ITetherService) {
        deathRecipient = null

        val binder = bound.asBinder()
        val recipient = IBinder.DeathRecipient {
            boundService = null
            onSessionLost?.invoke()
        }

        runCatching { binder.linkToDeath(recipient, 0) }
            .onSuccess { deathRecipient = recipient }
            .onFailure {
                // Already dead: the caller's next call fails and surfaces it.
                boundService = null
            }
    }

    suspend fun runProbes(attemptTethering: Boolean): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.runProbes(attemptTethering, AVAILABILITY_TIMEOUT_MS)
    }

    /**
     * Resolves the two APIs the app rests on, at shell UID.
     *
     * Verifies the contract first: an older daemon has no such method and
     * raises AbstractMethodError, which says nothing about compatibility.
     */
    suspend fun checkCompatibility(): List<CapabilityResult> = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        parseCapabilities(bound.checkCompatibility())
    }

    suspend fun start(logging: Boolean): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.start(logging)
    }

    /**
     * No-ops when unbound rather than binding to deliver: an unbound process
     * holds no session to log, and start() carries the setting across.
     */
    fun setLogging(enabled: Boolean) {
        runCatching { boundService?.setLogging(enabled) }
    }

    /**
     * Binds if nothing is bound — the stable tag reaches the daemon already
     * running, so a stop from a process that never bound (the notification
     * action, a restarted service) still finds the session it needs to end.
     */
    suspend fun stop(): String = withContext(Dispatchers.IO) {
        service().stop()
    }

    /**
     * Empties the shell process's half of the log.
     *
     * Binds to deliver this, unlike [setLogging]: a setting survives being
     * skipped, but a file left unwritten stays written, and the shell's file
     * holds most of the history — the screen would reload and show the clear
     * had not happened.
     *
     * @return null on success, else why the entries could not be dropped, for
     *   a UI that should not claim more than it did.
     */
    suspend fun clearLog(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bound = service()

            // clearLog is newer than daemons that may still be running, where
            // it raises an AbstractMethodError saying nothing about why.
            verifyContract(bound)
            bound.clearLog()
        }.fold(
            onSuccess = { null },
            onFailure = { failure -> failure.message ?: "could not reach Shizuku" },
        )
    }

    /**
     * Drops a downstream left tethered by a shell process that died — SIGTERM
     * leaves the hotspot up and tethering falling back to cellular with clients
     * attached, which the in-process shutdown hook cannot catch.
     *
     * Binds a *new* shell process purely to issue the teardown: the old one is
     * gone with the session object it owned, but stop() on a fresh instance
     * still drops the downstream that outlived it.
     *
     * @return null on success, else why the downstream could not be dropped.
     */
    suspend fun releaseOrphanedDownstream(): String? = withContext(Dispatchers.IO) {
        runCatching { service().stop() }
            .fold(
                onSuccess = { null },
                onFailure = { failure ->
                    "could not reach Shizuku to drop the hotspot: ${failure.message}"
                },
            )
    }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        service().status
    }

    /**
     * T-5: a shell process left over from a previous APK must not be used.
     *
     * The daemon survives APK replacement and does not reload its classes, so a
     * rebuilt implementation keeps running old code behind an unchanged AIDL
     * surface. Keying the version on the build makes that detectable — a debug
     * build carries its APK timestamp, so any reinstall invalidates a daemon.
     */
    private fun verifyContract(bound: ITetherService) {
        val remote = bound.contractVersion
        check(remote == TetherService.CONTRACT_VERSION) {
            "UserService is stale: app is build ${TetherService.CONTRACT_VERSION}, " +
                "shell process reports $remote — press Stop then Start to reload it"
        }
    }

    /**
     * Keeps the daemon, and with it the test network the session depends on:
     * leaving the UI must not drop tethered clients. Only stop() ends a session.
     */
    fun unbind() = releaseBinding(shouldTerminate = false)

    /**
     * Safe only once a session is torn down, and necessary then: the daemon
     * holds the TUN's fd, so a process outliving its session keeps the
     * interface alive in the kernel whatever teardown closes on this side. An
     * evening of starts and stops left fourteen orphaned daemons and eight
     * surviving testtun interfaces, and tethering picking one of those is what
     * failed the next session's upstream check.
     */
    fun unbindAndStopDaemon() = releaseBinding(shouldTerminate = true)

    private fun releaseBinding(shouldTerminate: Boolean) {
        deathRecipient?.let { recipient ->
            runCatching { boundService?.asBinder()?.unlinkToDeath(recipient, 0) }
        }
        deathRecipient = null

        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, shouldTerminate) }
        boundService = null
    }

    private companion object {
        /**
         * Stable across binds and across both controllers that reach it — the
         * service's and the ViewModel's — so they address one shell process.
         */
        private const val SERVICE_TAG = "shizzi-session"

        /**
         * Deliberately longer than the 30s Shizuku gives a user service to
         * start (UserServiceRecord.setStartingTimeout), so the server's own
         * deadline is the one that decides. At 10s this expired while the shell
         * process was still legitimately starting, and reported a failure for a
         * bind that had not failed.
         *
         * Covers only the bind. The probe run is not bounded by this: Q5 waits
         * up to 45s for upstream selection to settle.
         */
        const val BIND_TIMEOUT_MS = 35_000L

        /** R3.3 suggests a 10s bound on test-network availability. */
        const val AVAILABILITY_TIMEOUT_MS = 10_000
    }
}
