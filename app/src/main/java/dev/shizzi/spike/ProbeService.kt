package dev.shizzi.spike

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

/**
 * Shizuku UserService implementation. Every method here executes in the shell
 * (uid 2000) process that Shizuku spawns, not in the app process.
 *
 * Shizuku instantiates this reflectively via the no-arg or Context constructor,
 * so neither may be removed.
 */
class ProbeService : IProbeService.Stub {

    private val runner: ProbeRunner

    @Suppress("unused")
    constructor() : this(acquireSystemContext())

    constructor(context: Context) {
        runner = ProbeRunner(context)
    }

    override fun getContractVersion(): Int = CONTRACT_VERSION

    /**
     * Never throws across the binder: a RemoteException in the app process loses
     * the diagnostic detail, which is the entire product of this spike (R7.5).
     */
    override fun runProbes(attemptTethering: Boolean, availabilityTimeoutMs: Int): String =
        runCatching { runner.run(attemptTethering, availabilityTimeoutMs) }
            .getOrElse { failure -> errorReport("runProbes", failure) }

    override fun teardown(): String =
        runCatching { runner.teardown() }
            .getOrElse { failure -> errorReport("teardown", failure) }

    private fun errorReport(operation: String, failure: Throwable): String {
        Log.e(TAG, "$operation failed", failure)
        return org.json.JSONObject().apply {
            put("verdict", "ERROR")
            put("operation", operation)
            put("error", "${failure.javaClass.name}: ${failure.message}")
            put("stackTrace", failure.stackTraceToString().take(STACK_TRACE_CHARS))
        }.toString(2)
    }

    companion object {
        /** Bumped whenever the AIDL surface changes, so a stale shell process is detected (R2.5). */
        const val CONTRACT_VERSION = 1

        private const val TAG = "ProbeService"
        private const val STACK_TRACE_CHARS = 4000

        /**
         * Obtains a system Context inside the shell process, which has no
         * Application object of its own.
         */
        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun acquireSystemContext(): Context {
            val activityThread = Class.forName("android.app.ActivityThread")
            val systemMain = activityThread.getMethod("systemMain").invoke(null)
            val getSystemContext = activityThread.getMethod("getSystemContext")
            return getSystemContext.invoke(systemMain) as Context
        }
    }
}
