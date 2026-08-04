package dev.shizzi.spike

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/** The four states the status indicator shows. */
enum class UiStatus { READY, LOADING, CONNECTED, ERROR }

/** What the single screen renders. */
data class SpikeUiState(
    val shizukuState: ShizukuState = ShizukuState.NotRunning,
    val status: UiStatus = UiStatus.READY,
    val isBusy: Boolean = false,
    val detail: String = "",
    val interfaceName: String = "",
    val lastError: String = "",
    val isDebugLogging: Boolean = false,
) {
    /** Shizuku must be ready before the button can do anything. */
    val canStart: Boolean get() = shizukuState is ShizukuState.Ready && !isBusy
}

/**
 * Drives the privileged service from the app process.
 *
 * All calls serialize behind [isBusy] in the ViewModel (R7.6); this class holds
 * no lock of its own and assumes single-threaded entry from the UI scope.
 */
class ProbeController {

    private var boundService: IProbeService? = null
    private var pendingBind: CompletableDeferred<IProbeService>? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ProbeService::class.java.name),
    )
        // The session outlives the binder call that starts it: the test network
        // is tied to a Binder token held in this process, and a non-daemon
        // service is reaped once the call returns, taking the network with it.
        // That showed up as "TestNetworkAgent: NetworkAgent channel lost" about
        // four seconds after start() returned, with the upstream reverting.
        .daemon(true)
        .processNameSuffix("probe")
        .debuggable(true)
        .version(ProbeService.CONTRACT_VERSION)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = binder?.let { IProbeService.Stub.asInterface(it) }
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
    private suspend fun service(): IProbeService {
        boundService?.let { existing ->
            if (existing.asBinder().pingBinder()) return existing
            boundService = null
        }

        val deferred = CompletableDeferred<IProbeService>()
        pendingBind = deferred
        Shizuku.bindUserService(userServiceArgs, connection)

        return withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
    }

    suspend fun runProbes(attemptTethering: Boolean): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.runProbes(attemptTethering, AVAILABILITY_TIMEOUT_MS)
    }

    suspend fun start(debugLogging: Boolean): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.start(debugLogging)
    }

    suspend fun stop(): String = withContext(Dispatchers.IO) {
        service().stop()
    }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        service().status
    }

    /** T-5: a shell process left over from a previous APK must not be used. */
    private fun verifyContract(bound: IProbeService) {
        val remote = bound.contractVersion
        check(remote == ProbeService.CONTRACT_VERSION) {
            "UserService contract mismatch: app expects ${ProbeService.CONTRACT_VERSION}, " +
                "shell process reports $remote — force-stop Shizuku and retry"
        }
    }

    /**
     * Drops the binding without destroying the shell process.
     *
     * Passing remove=true would tear down the daemon, and with it the test
     * network the session depends on. Leaving the UI must not drop tethered
     * clients; only an explicit stop() ends the session.
     */
    fun unbind() {
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, false) }
        boundService = null
    }

    private companion object {
        /**
         * Binding is fast; the probe run itself is not. Q5 waits up to 45s for
         * upstream selection to settle, so this bound covers only the bind.
         */
        const val BIND_TIMEOUT_MS = 10_000L

        /** R3.3 suggests a 10s bound on test-network availability. */
        const val AVAILABILITY_TIMEOUT_MS = 10_000
    }
}
