package dev.shizzi

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import java.net.InetAddress
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

sealed interface LocalAdbPairingState {
    data object Idle : LocalAdbPairingState
    data object Searching : LocalAdbPairingState
    data object ServiceFound : LocalAdbPairingState
    data object Pairing : LocalAdbPairingState
    data object Ready : LocalAdbPairingState
    data object Canceled : LocalAdbPairingState
    data class Error(val message: String) : LocalAdbPairingState
}

object LocalAdbManager {
    private const val PREFS = "local_adb"
    private const val KEY_PAIRED = "paired"
    private const val KEY_IDENTITY_VERSION = "identity_version"
    private const val KEY_PENDING_HOST = "pending_host"
    private const val KEY_PENDING_PORT = "pending_port"
    private const val DISCOVERY_TIMEOUT_MS = 90_000L
    private const val CONNECT_DISCOVERY_TIMEOUT_MS = 10_000L

    private val mutablePairingState = MutableStateFlow<LocalAdbPairingState>(LocalAdbPairingState.Idle)
    val pairingState: StateFlow<LocalAdbPairingState> = mutablePairingState.asStateFlow()

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun wasPaired(context: Context): Boolean {
        ensureIdentityVersion(context)
        return prefs(context).getBoolean(KEY_PAIRED, false)
    }

    fun startPairing(context: Context): Boolean {
        val app = context.applicationContext
        ensureIdentityVersion(app)
        if (!isSupported()) {
            setPairingState(LocalAdbPairingState.Error(app.getString(R.string.local_adb_unsupported)))
            return false
        }
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            setPairingState(LocalAdbPairingState.Error(app.getString(R.string.local_adb_notifications_required)))
            return false
        }
        setPairingState(LocalAdbPairingState.Searching)
        ContextCompat.startForegroundService(
            app,
            Intent(app, LocalAdbPairingService::class.java).setAction(LocalAdbPairingService.ACTION_START),
        )
        return true
    }

    fun cancelPairing(context: Context) {
        context.applicationContext.startService(
            Intent(context.applicationContext, LocalAdbPairingService::class.java)
                .setAction(LocalAdbPairingService.ACTION_CANCEL),
        )
    }

    fun openWirelessDebuggingSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    suspend fun discoverPairingEndpoint(context: Context): Endpoint =
        withTimeout(DISCOVERY_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                var mdns: AdbMdns? = null
                mdns = AdbMdns(
                    context.applicationContext,
                    AdbMdns.SERVICE_TYPE_TLS_PAIRING,
                ) { host: InetAddress?, port: Int ->
                    if (!continuation.isActive || host == null || port <= 0) return@AdbMdns
                    val endpoint = Endpoint(host.hostAddress ?: "127.0.0.1", port)
                    mdns?.stop()
                    persistEndpoint(context, endpoint)
                    setPairingState(LocalAdbPairingState.ServiceFound)
                    continuation.resume(endpoint)
                }
                continuation.invokeOnCancellation { mdns?.stop() }
                mdns.start()
            }
        }

    suspend fun pairDiscovered(context: Context, pairingCode: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val app = context.applicationContext
                ensureIdentityVersion(app)
                check(pairingCode.length == 6 && pairingCode.all(Char::isDigit)) {
                    app.getString(R.string.local_adb_pairing_code_invalid)
                }
                val endpoint = pendingEndpoint(app)
                    ?: error(app.getString(R.string.local_adb_pairing_service_missing))

                setPairingState(LocalAdbPairingState.Pairing)
                val connection = LocalAdbConnectionManager.get(app)
                runCatching { connection.disconnect() }
                check(connection.pair(endpoint.address, endpoint.port, pairingCode)) {
                    app.getString(R.string.local_adb_pairing_rejected)
                }
                prefs(app).edit()
                    .putBoolean(KEY_PAIRED, true)
                    .putInt(KEY_IDENTITY_VERSION, LocalAdbConnectionManager.IDENTITY_VERSION)
                    .apply()
                clearPendingEndpoint(app)
                connectOrThrow(app)
                setPairingState(LocalAdbPairingState.Ready)
            }.onFailure { failure ->
                setPairingState(LocalAdbPairingState.Error(friendlyFailure(context, failure)))
            }
        }

    suspend fun connect(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureIdentityVersion(context)
            connectOrThrow(context.applicationContext)
        }.onFailure { failure ->
            setPairingState(LocalAdbPairingState.Error(friendlyFailure(context, failure)))
        }
    }

    internal fun setPairingState(state: LocalAdbPairingState) {
        mutablePairingState.value = state
    }

    internal fun clearPendingEndpoint(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_HOST).remove(KEY_PENDING_PORT).apply()
    }

    fun resetPairing(context: Context, reason: String? = null) {
        val app = context.applicationContext
        runCatching { LocalAdbConnectionManager.get(app).disconnect() }
        LocalAdbConnectionManager.reset(app)
        prefs(app).edit()
            .putBoolean(KEY_PAIRED, false)
            .putInt(KEY_IDENTITY_VERSION, LocalAdbConnectionManager.IDENTITY_VERSION)
            .remove(KEY_PENDING_HOST)
            .remove(KEY_PENDING_PORT)
            .apply()
        setPairingState(reason?.let(LocalAdbPairingState::Error) ?: LocalAdbPairingState.Idle)
    }

    private fun connectOrThrow(context: Context) {
        check(isSupported()) { context.getString(R.string.local_adb_unsupported) }
        val connection = LocalAdbConnectionManager.get(context)
        if (connection.isConnected) return

        try {
            check(connection.connectTls(context, CONNECT_DISCOVERY_TIMEOUT_MS)) {
                context.getString(R.string.local_adb_connect_failed)
            }
        } catch (required: AdbPairingRequiredException) {
            prefs(context).edit().putBoolean(KEY_PAIRED, false).apply()
            throw IllegalStateException(context.getString(R.string.local_adb_pairing_required), required)
        } catch (failure: Throwable) {
            if (failure is SSLException ||
                failure.message.orEmpty().contains("SSL library", true) ||
                failure.message.orEmpty().contains("RSA routines", true)
            ) {
                resetPairing(context)
                throw IllegalStateException(context.getString(R.string.local_adb_tls_error), failure)
            }
            throw failure
        }
    }

    private fun ensureIdentityVersion(context: Context) {
        val store = prefs(context)
        if (store.getInt(KEY_IDENTITY_VERSION, 0) == LocalAdbConnectionManager.IDENTITY_VERSION) return
        LocalAdbConnectionManager.reset(context)
        store.edit()
            .putBoolean(KEY_PAIRED, false)
            .putInt(KEY_IDENTITY_VERSION, LocalAdbConnectionManager.IDENTITY_VERSION)
            .remove(KEY_PENDING_HOST)
            .remove(KEY_PENDING_PORT)
            .apply()
        setPairingState(LocalAdbPairingState.Idle)
    }

    private fun persistEndpoint(context: Context, endpoint: Endpoint) {
        prefs(context).edit()
            .putString(KEY_PENDING_HOST, endpoint.address)
            .putInt(KEY_PENDING_PORT, endpoint.port)
            .apply()
    }

    private fun pendingEndpoint(context: Context): Endpoint? {
        val store = prefs(context)
        val host = store.getString(KEY_PENDING_HOST, null) ?: return null
        val port = store.getInt(KEY_PENDING_PORT, -1)
        return port.takeIf { it > 0 }?.let { Endpoint(host, it) }
    }

    private fun friendlyFailure(context: Context, failure: Throwable): String {
        val raw = failure.message.orEmpty()
        return when {
            raw.contains("SSL library", true) || raw.contains("RSA routines", true) ->
                context.getString(R.string.local_adb_tls_error)
            raw.contains("Timed out", true) ->
                context.getString(R.string.local_adb_discovery_timeout)
            raw.isBlank() -> failure.javaClass.simpleName
            else -> raw
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Endpoint(val address: String, val port: Int)
}
