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
import org.json.JSONObject
import rikka.shizuku.Shizuku

enum class UiStatus { READY, LOADING, CONNECTED, ERROR }

data class SessionUiState(
    val shizukuState: ShizukuState = ShizukuState.NotRunning,
    val status: UiStatus = UiStatus.READY,
    val isBusy: Boolean = false,
    val detail: String = "",
    val interfaceName: String = "",
    val lastError: String = "",
    val isVpnBound: Boolean = false,

    val hasBackgroundStart: Boolean = true,

    val clientCount: Int = 0,

    val traffic: Traffic = Traffic(),
) {

    val canStart: Boolean get() = shizukuState is ShizukuState.Ready && !isBusy
}

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

fun SessionUiState.applyOutcome(outcome: Result<String>): SessionUiState {
    val failure = outcome.exceptionOrNull()
    if (failure != null) {
        return copy(
            isBusy = false,
            status = UiStatus.ERROR,
            lastError = "${failure.javaClass.simpleName}: ${failure.message}",

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

    suspend fun start(logging: Boolean): String = withContext(Dispatchers.IO) {
        val bound = service()
        verifyContract(bound)
        bound.start(logging)
    }

    fun setLogging(enabled: Boolean) {
        runCatching { boundService?.setLogging(enabled) }
    }

    suspend fun stop(): String = withContext(Dispatchers.IO) {
        service().stop()
    }

    suspend fun grantBackgroundStart(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bound = service()

            verifyContract(bound)
            bound.grantBackgroundStart()
        }.getOrElse { failure -> failure.message ?: "could not reach Shizuku" }
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

        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, shouldTerminate) }
        boundService = null
    }

    private companion object {

        private const val SERVICE_TAG = "shizzi-session"

        const val BIND_TIMEOUT_MS = 35_000L

        const val AVAILABILITY_TIMEOUT_MS = 10_000
    }
}
