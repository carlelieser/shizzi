package dev.shizzi

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

class TetherService : ITetherService.Stub {

    private val context: Context

    private val shellContext: Context by lazy { asShellContext(context) }
    private val runner: ProbeRunner by lazy { ProbeRunner(shellContext) }
    private val session: TetherSession by lazy { TetherSession(shellContext) }
    private val compatibility: CompatibilityCheck by lazy { CompatibilityCheck(shellContext) }
    private val apexInstaller: ApexInstaller by lazy { ApexInstaller() }

    @Suppress("unused")
    constructor() : this(acquireSystemContext())

    constructor(context: Context) {
        this.context = context
        liveInstance = this
    }

    override fun getContractVersion(): Int = CONTRACT_VERSION

    override fun grantBackgroundStart(): String? =
        BackgroundStartPermit.grant(BuildConfig.APPLICATION_ID)

    override fun hasBackgroundStart(): Boolean =
        BackgroundStartPermit.isHeld(BuildConfig.APPLICATION_ID)

    override fun start(logging: Boolean): String {
        SessionLog.setEnabled(logging)
        return runCatching { session.start() }
            .getOrElse { failure -> sessionError("start", failure) }
    }

    override fun stop(): String {
        runCatching { runner.teardown() }
            .onFailure { failure -> Log.w(TAG, "stop: probe teardown ${failure.message}") }

        return runCatching { session.stop() }
            .getOrElse { failure -> sessionError("stop", failure) }
    }

    override fun getStatus(): String =
        runCatching { session.status() }
            .getOrElse { failure -> sessionError("getStatus", failure) }

    override fun setLogging(enabled: Boolean) {
        SessionLog.setEnabled(enabled)
    }

    override fun checkCompatibility(): String =
        runCatching { compatibility.run().toJson() }
            .getOrElse { failure -> errorReport("checkCompatibility", failure) }

    override fun installTetheringApex(apex: ParcelFileDescriptor): String =
        runCatching { apexInstaller.stage(apex).toJson() }
            .getOrElse { failure -> stagingError(failure) }

    override fun rebootDevice(): String =
        runCatching {
            Runtime.getRuntime().exec(arrayOf("svc", "power", "reboot"))
            ""
        }.getOrElse { failure ->
            Log.e(TAG, "rebootDevice failed", failure)
            "${failure.javaClass.simpleName}: ${failure.message}"
        }

    override fun clearLog() {
        runCatching { SessionLog.clear() }
            .onFailure { failure -> Log.w(TAG, "clearLog: ${failure.message}") }
    }

    override fun runProbes(attemptTethering: Boolean, availabilityTimeoutMs: Int): String =
        publish(
            runCatching { runner.run(attemptTethering, availabilityTimeoutMs) }
                .getOrElse { failure -> errorReport("runProbes", failure) },
        )

    private fun publish(report: String): String {
        runCatching { java.io.File(REPORT_PATH).writeText(report) }
            .onFailure { failure -> Log.w(TAG, "publish: ${failure.message}") }

        Log.i(TAG, "report: $report")
        return report
    }

    private fun sessionError(operation: String, failure: Throwable): String {
        Log.e(TAG, "$operation failed", failure)
        return org.json.JSONObject().apply {
            put("state", SessionState.ERROR.name)
            put("detail", "$operation: ${failure.javaClass.simpleName}: ${failure.message}")
        }.toString()
    }

    private fun stagingError(failure: Throwable): String {
        Log.e(TAG, "installTetheringApex failed", failure)
        return StagingOutcome(
            isStaged = false,
            rawOutput = "installTetheringApex: ${failure.javaClass.simpleName}: ${failure.message}",
        ).toJson()
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

        val CONTRACT_VERSION = BuildConfig.SERVICE_BUILD_ID

        @Suppress("unused")
        @JvmStatic
        private var liveInstance: TetherService? = null

        private const val TAG = "TetherService"
        private const val STACK_TRACE_CHARS = 4000

        const val REPORT_PATH = "/data/local/tmp/shizzi-probe-report.json"

        private const val SHELL_PACKAGE = "com.android.shell"

        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun acquireSystemContext(): Context {
            val activityThread = Class.forName("android.app.ActivityThread")
            val systemMain = activityThread.getMethod("systemMain").invoke(null)
            return activityThread.getMethod("getSystemContext").invoke(systemMain) as Context
        }

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

        private fun forceOpPackageName(context: Context) {
            runCatching {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        rebaseAttributionSource(context)

                    else -> rebaseOpPackageName(context)
                }
            }.getOrElse { failure ->
                throw IllegalStateException(
                    "forceOpPackageName: could not attribute context to $SHELL_PACKAGE",
                    failure,
                )
            }
        }

        private fun rebaseAttributionSource(context: Context) {
            val field = context.javaClass.getDeclaredField("mAttributionSource")
            field.isAccessible = true

            val current = field.get(context) ?: error("mAttributionSource was null")
            val rebased = current.javaClass
                .getMethod("withPackageName", String::class.java)
                .invoke(current, SHELL_PACKAGE)

            field.set(context, rebased)
        }

        private fun rebaseOpPackageName(context: Context) {
            val field = context.javaClass.getDeclaredField("mOpPackageName")
            field.isAccessible = true
            field.set(context, SHELL_PACKAGE)
        }
    }
}
