package dev.shizzi

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

class TetherClient {

    private var boundService: ITetherService? = null
    private var pendingBind: CompletableDeferred<ITetherService>? = null

    var onSessionLost: (() -> Unit)? = null

    private var deathRecipient: IBinder.DeathRecipient? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, TetherService::class.java.name),
    )

        .daemon(true)

        .tag(SERVICE_TAG)
        .processNameSuffix("probe")

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

    private var isAdbBound = false

    private suspend fun service(): ITetherService {
        boundService?.let { existing ->
            if (existing.asBinder().pingBinder()) return existing
            boundService = null
        }

        if (!ShizukuGate.isShizukuUsable() && AdbBridge.isPaired(App.instance)) return bindViaAdb()
        isAdbBound = false

        val deferred = CompletableDeferred<ITetherService>()
        pendingBind = deferred
        Shizuku.bindUserService(userServiceArgs, connection)

        val bound = awaitBind(deferred)
        observeDeath(bound)
        return bound
    }

    /** Shizuku is unavailable: use (or start) the helper that the app launches through its own adb. */
    private suspend fun bindViaAdb(): ITetherService {
        val app = App.instance
        var bound = AdbHelper.launch(app)
        if (bound.contractVersion != TetherService.CONTRACT_VERSION) {
            // A helper from an older build is still alive: retire it and start the current one.
            runCatching { bound.destroy() }
            AdbHelperRegistry.expectNew().also { AdbHelperRegistry.cancelWaiting() }
            Thread.sleep(500)
            bound = AdbHelper.launch(app)
        }
        isAdbBound = true
        boundService = bound
        observeDeath(bound)
        return bound
    }

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

                boundService = null
            }
    }

    suspend fun runProbes(attemptTethering: Boolean): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.runProbes(attemptTethering, AVAILABILITY_TIMEOUT_MS)
    }

    suspend fun checkCompatibility(): List<CapabilityResult> = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        parseCapabilities(bound.checkCompatibility())
    }

    suspend fun installTetheringApex(path: String): StagingOutcome =
        withContext(Dispatchers.IO) {
            val bound = service()
            verifyContract(bound)

            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                .use { apex -> parseStagingOutcome(bound.installTetheringApex(apex)) }
        }

    suspend fun rebootDevice(): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.rebootDevice()
    }

    suspend fun start(settings: Settings): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        val sharing = settings.sharing
        bound.start(
            settings.isLogging,
            settings.vpnMode.name,
            sharing.wired.name,
            sharing.isWifiHotspotOn,
            settings.customDns,
        )
    }

    fun setLogging(enabled: Boolean) {
        runCatching { boundService?.setLogging(enabled) }
    }

    suspend fun stop(): String = withContext(Dispatchers.IO) {
        service().stop()
    }

    suspend fun clearLog(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bound = service()

            verifyContract(bound)
            bound.clearLog()
        }.fold(
            onSuccess = { null },
            onFailure = { failure -> failure.message ?: "could not reach Shizuku" },
        )
    }

    /** Null once released; otherwise why the downstreams may still be up. */
    suspend fun releaseOrphanedDownstream(types: Set<TetheringType>): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bound = service()
                verifyContract(bound)
                bound.releaseOrphanedDownstream(types.map { type -> type.name }.toTypedArray())
            }.getOrElse { failure ->
                "could not reach Shizuku to drop the hotspot: ${failure.message}"
            }
        }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        service().status
    }

    private fun verifyContract(bound: ITetherService) {
        val remote = bound.contractVersion
        check(remote == TetherService.CONTRACT_VERSION) {
            "UserService is stale: app is build ${TetherService.CONTRACT_VERSION}, " +
                "shell process reports $remote — press Stop then Start to reload it"
        }
    }

    fun unbind() = releaseBinding(shouldTerminate = false)

    fun unbindAndStopDaemon() = releaseBinding(shouldTerminate = true)

    private fun releaseBinding(shouldTerminate: Boolean) {
        deathRecipient?.let { recipient ->
            runCatching { boundService?.asBinder()?.unlinkToDeath(recipient, 0) }
        }
        deathRecipient = null

        if (isAdbBound) {
            if (shouldTerminate) {
                runCatching { boundService?.destroy() }
                AdbHelperRegistry.expectNew().also { AdbHelperRegistry.cancelWaiting() }
            }
            isAdbBound = false
        } else {
            runCatching { Shizuku.unbindUserService(userServiceArgs, connection, shouldTerminate) }
        }
        boundService = null
    }

    private companion object {

        private const val SERVICE_TAG = "shizzi-session"

        const val BIND_TIMEOUT_MS = 35_000L

        const val AVAILABILITY_TIMEOUT_MS = 10_000
    }
}
