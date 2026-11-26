package dev.shizzi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * Where the Binder of the adb-started helper ([TetherMain]) arrives. The helper is not a Shizuku
 * user service, so it hands its Binder to the app with an explicit broadcast; this keeps the last
 * one received, wakes whoever is waiting for a fresh launch, and tells the helper it has a client
 * (so it stops re-sending). The helper re-sends every few seconds while it has none, which is how
 * an app process that was killed finds the still-running helper again.
 */
object AdbHelperRegistry {

    const val ACTION = "dev.shizzi.action.HELPER_BINDER"
    const val EXTRA_BUNDLE = "bundle"
    const val EXTRA_SECRET = "secret"
    const val KEY_BINDER = "binder"

    private const val PREFS = "adb_helper"
    private const val K_SECRET = "secret"

    private val lock = Any()
    private var latest: IBinder? = null
    private var waiter: CompletableFuture<IBinder>? = null

    /** Identifies this app process to the helper; when it dies the helper starts re-sending. */
    private val clientToken = Binder()

    /**
     * Persistent on purpose: the helper outlives the app process, and its re-sent Binder has to be
     * accepted by the next one. The secret travels only on the helper's command line, which other
     * apps cannot read.
     */
    fun secret(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(K_SECRET, null)?.let { return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val created = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(K_SECRET, created).apply()
        return created
    }

    /** The helper's service when one is alive and known. */
    fun live(): ITetherService? = synchronized(lock) {
        val binder = latest?.takeIf { it.pingBinder() } ?: return null
        ITetherService.Stub.asInterface(binder)
    }

    /** Forgets the known helper (it was told to exit, or turned out stale) and awaits the next. */
    fun expectNew(): CompletableFuture<IBinder> = synchronized(lock) {
        latest = null
        waiter?.cancel(false)
        CompletableFuture<IBinder>().also { waiter = it }
    }

    /** Waits for an already-running helper to announce itself, without forgetting anything. */
    fun awaitExisting(timeoutMs: Long): ITetherService? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            live()?.let { return it }
            Thread.sleep(100)
        }
        return live()
    }

    fun cancelWaiting() = synchronized(lock) {
        waiter?.cancel(false)
        waiter = null
    }

    internal fun accept(binder: IBinder) {
        synchronized(lock) {
            latest = binder
            waiter?.complete(binder)
            waiter = null
        }
        runCatching { ITetherService.Stub.asInterface(binder).attach(clientToken) }
    }

    internal fun matches(context: Context, given: String?): Boolean {
        if (given == null) return false
        return MessageDigest.isEqual(
            secret(context).toByteArray(StandardCharsets.UTF_8),
            given.toByteArray(StandardCharsets.UTF_8),
        )
    }
}

/**
 * Exported because the sender is the shell user; the sender is authenticated by the secret that
 * only the app and the helper it started know.
 */
class AdbHelperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdbHelperRegistry.ACTION) return
        if (!AdbHelperRegistry.matches(context, intent.getStringExtra(AdbHelperRegistry.EXTRA_SECRET))) return

        val binder = intent.getBundleExtra(AdbHelperRegistry.EXTRA_BUNDLE)
            ?.getBinder(AdbHelperRegistry.KEY_BINDER) ?: return

        // attach() is a binder call into another process: not on the main thread.
        val pending = goAsync()
        thread(name = "shizzi-adb-attach") {
            try {
                AdbHelperRegistry.accept(binder)
            } finally {
                pending.finish()
            }
        }
    }
}
