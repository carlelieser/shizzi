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

    /**
     * Shizuku prefers this constructor and supplies a context whose package name
     * is "android", so the shell rebasing has to happen here too — doing it only
     * in the no-arg path leaves the real code path unfixed.
     */
    constructor(context: Context) {
        runner = ProbeRunner(asShellContext(context))
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

        /** The shell package, whose UID (2000) this process actually runs as. */
        private const val SHELL_PACKAGE = "com.android.shell"

        /**
         * Obtains a Context attributed to the shell package.
         *
         * ActivityThread.getSystemContext() alone returns a context whose
         * package name is "android". Framework services that validate the
         * calling package against the calling UID then reject the call with
         * "Package android does not belong to 2000", which is what the first
         * device run of setupTestNetwork hit.
         *
         * Rebasing onto com.android.shell makes the package match the UID.
         */
        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun acquireSystemContext(): Context {
            val activityThread = Class.forName("android.app.ActivityThread")
            val systemMain = activityThread.getMethod("systemMain").invoke(null)
            return activityThread.getMethod("getSystemContext").invoke(systemMain) as Context
        }

        /**
         * Rebases [context] onto the shell package.
         *
         * Framework services validate the calling package against the calling
         * UID. A context reporting package "android" from a UID-2000 process is
         * rejected with "Package android does not belong to 2000", which is how
         * setupTestNetwork failed on the first two device runs.
         */
        private fun asShellContext(context: Context): Context {
            val rebased = runCatching { context.createPackageContext(SHELL_PACKAGE, 0) }
                .getOrElse { failure ->
                    throw IllegalStateException(
                        "asShellContext: could not rebase context (package=" +
                            "${context.packageName}) onto $SHELL_PACKAGE",
                        failure,
                    )
                }
            forceOpPackageName(rebased)
            return rebased
        }

        /**
         * Overwrites ContextImpl.mOpPackageName on [context].
         *
         * createPackageContext changes getPackageName but not
         * getOpPackageName, and system services attribute calls by the latter:
         * TetheringService rejected every start with "Package name android does
         * not match UID 2000" while the report showed opPackage=android.
         *
         * Writing the field directly is safe here because this process is a
         * short-lived shell helper we own outright.
         */
        private fun forceOpPackageName(context: Context) {
            runCatching {
                val field = context.javaClass.getDeclaredField("mOpPackageName")
                field.isAccessible = true
                field.set(context, SHELL_PACKAGE)
            }.onFailure { failure ->
                Log.w(TAG, "forceOpPackageName: ${failure.message}")
            }
        }
    }
}
