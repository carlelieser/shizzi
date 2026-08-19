package dev.shizzi

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
 * The state a stopped session renders as.
 *
 * Defined once because both the service and the ViewModel apply it optimistically
 * the moment the user asks to stop, and a copy that forgot to clear
 * [SessionUiState.interfaceName] left "via testtunNN" under an idle screen.
 */
fun SessionUiState.asStopped(): SessionUiState = copy(
    isBusy = false,
    status = UiStatus.READY,
    lastError = "",
    detail = "Stopped",
    interfaceName = "",
    isVpnBound = false,
    // Same reasoning: a stopped session has no clients and carries nothing,
    // so holding the last reading would caption an idle screen with live
    // numbers that stopped moving.
    clientCount = 0,
    traffic = Traffic(),
)

/**
 * Folds a privileged call's result into the rendered state.
 *
 * A thrown exception and a status reporting ERROR are different failures — a
 * dead binder versus the session refusing to come up — so both are surfaced
 * rather than collapsed into one message.
 *
 * Lives beside the state it produces rather than in a ViewModel: the session
 * outlives any one screen, so the service folds these results too.
 */
fun SessionUiState.applyOutcome(outcome: Result<String>): SessionUiState {
    val failure = outcome.exceptionOrNull()
    if (failure != null) {
        return copy(
            isBusy = false,
            status = UiStatus.ERROR,
            lastError = "${failure.javaClass.simpleName}: ${failure.message}",
            // A failed start tears its TUN down on the way out, so keeping the
            // name would caption an idle screen with an interface that is gone.
            interfaceName = "",
            // Same reasoning, and worse: a stale badge would claim the traffic
            // of a session that no longer exists is going through a VPN.
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
     * Notified when the shell process dies while a session is running.
     *
     * The app process outlives the shell process — it stayed alive at
     * oom_score_adj 0 through every observed death — so without this the UI
     * keeps showing CONNECTED for a session that no longer exists.
     */
    var onSessionLost: (() -> Unit)? = null

    private var deathRecipient: IBinder.DeathRecipient? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, TetherService::class.java.name),
    )
        // The session outlives the binder call that starts it: the test network
        // is tied to a Binder token held in this process, and a non-daemon
        // service is reaped once the call returns, taking the network with it.
        // That showed up as "TestNetworkAgent: NetworkAgent channel lost" about
        // four seconds after start() returned, with the upstream reverting.
        .daemon(true)
        // Without an explicit tag Shizuku keys each service record by a fresh
        // UUID, so every bind creates a *new* daemon and unbind cannot reach the
        // one already running. That leaked a shell process per session, each
        // holding its TUN's file descriptor and so keeping the interface alive
        // in the kernel. A stable tag makes bind and unbind name the same
        // record, which is what lets the daemon actually be terminated.
        .tag(SERVICE_TAG)
        .processNameSuffix("probe")
        .debuggable(true)
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
     * Binds the shell-side service, reusing an existing binding when live.
     *
     * A dead binder is discarded rather than reused: after an APK update the old
     * shell process is stale (R2.5, T-5).
     */
    private suspend fun service(): ITetherService {
        boundService?.let { existing ->
            if (existing.asBinder().pingBinder()) return existing
            boundService = null
        }

        val deferred = CompletableDeferred<ITetherService>()
        pendingBind = deferred
        Shizuku.bindUserService(userServiceArgs, connection)

        val bound = withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        observeDeath(bound)
        return bound
    }

    /**
     * Reports the session as lost when the shell process dies.
     *
     * onServiceDisconnected does not cover this: the shell process is a child
     * of the Shizuku server, and when that server dies the child exits without
     * the binding being torn down through the normal path. Linking to the
     * binder itself catches both.
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

    suspend fun start(logging: Boolean): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.start(logging)
    }

    /**
     * Pushes the logging setting into the shell process.
     *
     * Does nothing when nothing is bound, rather than binding to deliver it: an
     * unbound shell process holds no session to log, and start() carries the
     * setting across when one begins.
     */
    fun setLogging(enabled: Boolean) {
        runCatching { boundService?.setLogging(enabled) }
    }

    /**
     * Tears the session down.
     *
     * Binds if nothing is bound: with a stable service tag that reaches the
     * daemon already running rather than starting a fresh one, so a stop issued
     * from a process that never bound — the notification action, a restarted
     * service — still finds the session it needs to end.
     */
    suspend fun stop(): String = withContext(Dispatchers.IO) {
        service().stop()
    }

    /**
     * Drops a downstream left tethered by a shell process that died.
     *
     * The app process outlives the shell process — it survived at
     * oom_score_adj 0 through every observed death — so it is the only place
     * that can still act. A device test showed SIGTERM leaves the hotspot
     * tethered and tethering falling back to cellular with clients attached,
     * which the in-process shutdown hook cannot catch.
     *
     * This binds a *new* shell process purely to issue the teardown: the old
     * one is gone along with the session object it owned, and stop() on the
     * fresh instance still tears down the downstream that outlived it.
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
     * The daemon survives APK replacement by design, and an already-loaded
     * class is not reloaded — so a rebuilt implementation keeps running the old
     * code behind an unchanged AIDL surface. Keying the version on the build
     * rather than the interface makes that detectable: a debug build carries
     * its APK timestamp, so any reinstall invalidates a running daemon.
     */
    private fun verifyContract(bound: ITetherService) {
        val remote = bound.contractVersion
        check(remote == TetherService.CONTRACT_VERSION) {
            "UserService is stale: app is build ${TetherService.CONTRACT_VERSION}, " +
                "shell process reports $remote — press Stop then Start to reload it"
        }
    }

    /**
     * Drops the binding without destroying the shell process.
     *
     * Passing remove=true would tear down the daemon, and with it the test
     * network the session depends on. Leaving the UI must not drop tethered
     * clients; only an explicit stop() ends the session.
     */
    fun unbind() = releaseBinding(shouldTerminate = false)

    /**
     * Drops the binding *and* terminates the shell process behind it.
     *
     * Called once a session has been torn down, which is the only point where
     * killing the daemon is safe — and necessary. The daemon holds the TUN's
     * file descriptor, so a process that outlives its session keeps the
     * interface alive in the kernel no matter what teardown closes on this
     * side. A device test found fourteen orphaned daemons and eight surviving
     * testtun interfaces after a single evening of starts and stops, and
     * tethering selecting one of those leftovers is what failed the next
     * session's upstream check.
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
         * The daemon's stable identity across binds, and across the two
         * controllers that reach it — the service's and the ViewModel's — so
         * both address one shell process rather than one each.
         */
        private const val SERVICE_TAG = "shizzi-session"

        /**
         * Binding is fast; the probe run itself is not. Q5 waits up to 45s for
         * upstream selection to settle, so this bound covers only the bind.
         */
        const val BIND_TIMEOUT_MS = 10_000L

        /** R3.3 suggests a 10s bound on test-network availability. */
        const val AVAILABILITY_TIMEOUT_MS = 10_000
    }
}
