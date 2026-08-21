package dev.shizzi

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log

/**
 * Shizuku UserService implementation, running in the shell (uid 2000) process.
 *
 * Both constructors are invoked reflectively by Shizuku; neither may be removed.
 */
class TetherService : ITetherService.Stub {

    private val runner: ProbeRunner
    private val session: TetherSession
    private val compatibility: CompatibilityCheck

    @Suppress("unused")
    constructor() : this(acquireSystemContext())

    /**
     * Shizuku prefers this constructor and supplies a context packaged as
     * "android", so the rebasing has to happen here and not only in the no-arg
     * path, which is the one that never runs.
     */
    constructor(context: Context) {
        val shellContext = asShellContext(context)
        runner = ProbeRunner(shellContext)
        session = TetherSession(shellContext)
        compatibility = CompatibilityCheck(shellContext)
        liveInstance = this
    }

    override fun getContractVersion(): Int = CONTRACT_VERSION

    override fun start(logging: Boolean): String {
        SessionLog.setEnabled(logging)
        return runCatching { session.start() }
            .getOrElse { failure -> errorReport("start", failure) }
    }

    /**
     * Also tears down the probe runner: the two paths hold separate resources,
     * so stopping only the session leaves the other's test network alive.
     */
    override fun stop(): String {
        runCatching { runner.teardown() }
            .onFailure { failure -> Log.w(TAG, "stop: probe teardown ${failure.message}") }

        return runCatching { session.stop() }
            .getOrElse { failure -> errorReport("stop", failure) }
    }

    override fun getStatus(): String =
        runCatching { session.status() }
            .getOrElse { failure -> errorReport("getStatus", failure) }

    /** Pushed by the app, which writes few entries and holds the only DataStore. */
    override fun setLogging(enabled: Boolean) {
        SessionLog.setEnabled(enabled)
    }

    /**
     * Never throws across the binder: the app reads a capability the report
     * omits as absent, which is the honest answer when the check itself broke.
     */
    override fun checkCompatibility(): String =
        runCatching { compatibility.run().toJson() }
            .getOrElse { failure -> errorReport("checkCompatibility", failure) }

    /**
     * Empties the file this process writes; the app clears its own half.
     *
     * Never throws across the binder — a failed clear is not worth a
     * RemoteException, and the UI re-reads the file anyway.
     */
    override fun clearLog() {
        runCatching { SessionLog.clear() }
            .onFailure { failure -> Log.w(TAG, "clearLog: ${failure.message}") }
    }

    /**
     * Never throws across the binder: a RemoteException loses the diagnostic
     * detail, which is the entire point of a probe run (R7.5).
     */
    override fun runProbes(attemptTethering: Boolean, availabilityTimeoutMs: Int): String =
        publish(
            runCatching { runner.run(attemptTethering, availabilityTimeoutMs) }
                .getOrElse { failure -> errorReport("runProbes", failure) },
        )

    /**
     * Writes the probe [report] where it can be read off-device.
     *
     * Only probe runs publish — start and stop would overwrite the report with
     * a status SessionLog already keeps. Written from this process because uid
     * 2000 can write outside app-private storage, so it reads without run-as,
     * and ungated because logcat truncates the long lines a report is made of.
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
        /**
         * Identifies the build, so a stale shell process is detected (R2.5).
         *
         * Derived from the sources rather than hand-bumped: the daemon survives
         * APK replacement without reloading its classes, so the implementation
         * can change while the AIDL surface stays identical — the exact case a
         * hand-maintained number misses.
         */
        val CONTRACT_VERSION = BuildConfig.SERVICE_BUILD_ID

        /**
         * Keeps the session alive across unbinds — Shizuku holds the stub only
         * while a client is bound, but the user starts tethering and leaves.
         */
        @Suppress("unused")
        @JvmStatic
        private var liveInstance: TetherService? = null

        private const val TAG = "TetherService"
        private const val STACK_TRACE_CHARS = 4000

        /** Public so the settings screen can name it without restating the path. */
        const val REPORT_PATH = "/data/local/tmp/shizzi-probe-report.json"

        /** The shell package, whose UID (2000) this process actually runs as. */
        private const val SHELL_PACKAGE = "com.android.shell"

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
         * UID, and getSystemContext reports "android" from a UID-2000 process:
         * "Package android does not belong to 2000", which is how the first
         * device runs of setupTestNetwork failed.
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
         * createPackageContext fixes getPackageName but not getOpPackageName,
         * which is what services actually attribute by. On API 36 that reads
         * mAttributionSource; the older mOpPackageName field no longer backs
         * it, so writing that one succeeded and changed nothing.
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
