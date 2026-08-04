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
        publish(
            runCatching { runner.run(attemptTethering, availabilityTimeoutMs) }
                .getOrElse { failure -> errorReport("runProbes", failure) },
        )

    override fun teardown(): String =
        publish(
            runCatching { runner.teardown() }
                .getOrElse { failure -> errorReport("teardown", failure) },
        )

    /**
     * Writes [report] where it can be read off-device, and returns it unchanged.
     *
     * The report is otherwise only rendered in the app UI, which makes every
     * device run depend on someone transcribing it. Persisting it from the shell
     * process rather than the app process matters: uid 2000 can write outside
     * app-private storage, so the file is readable without run-as.
     */
    private fun publish(report: String): String {
        runCatching { java.io.File(REPORT_PATH).writeText(report) }
            .onFailure { failure -> Log.w(TAG, "publish: ${failure.message}") }
        Log.i(TAG, "report: $report")
        return report
    }

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

        /** World-readable scratch path; /data/local/tmp is writable by uid 2000. */
        private const val REPORT_PATH = "/data/local/tmp/shizzi-probe-report.json"

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
         * Repoints ContextImpl.mAttributionSource at the shell package.
         *
         * createPackageContext changes getPackageName but not
         * getOpPackageName, and system services attribute calls by the latter:
         * TetheringService rejects every start with "Package name android does
         * not match UID 2000".
         *
         * On API 36 getOpPackageName reads mAttributionSource.getPackageName();
         * the older mOpPackageName field no longer backs it, so writing that
         * field succeeded silently and changed nothing. AttributionSource is
         * immutable, hence withPackageName returning a replacement copy.
         *
         * @throws IllegalStateException so a future release moving this again
         *   fails loudly instead of producing another silent no-op.
         */
        private fun forceOpPackageName(context: Context) {
            runCatching {
                val field = context.javaClass.getDeclaredField("mAttributionSource")
                field.isAccessible = true

                val current = field.get(context)
                    ?: error("mAttributionSource was null")
                val rebased = current.javaClass
                    .getMethod("withPackageName", String::class.java)
                    .invoke(current, SHELL_PACKAGE)

                field.set(context, rebased)
            }.getOrElse { failure ->
                throw IllegalStateException(
                    "forceOpPackageName: could not attribute context to $SHELL_PACKAGE",
                    failure,
                )
            }
        }
    }
}
