package dev.shizzi

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Persists uncaught exceptions so the next launch can surface them.
 *
 * A crash tears the process down, so the report cannot be presented in the
 * moment; the dialog greets the user on the following run instead. This
 * matters inside onboarding, where the in-app log page is not reachable yet.
 * The previous handler still runs so the system crash flow stays untouched.
 */
object CrashReport {

    private const val FILE_NAME = "last-crash.txt"
    private const val LOG_TAIL_ENTRIES = 40

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context.applicationContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val file = file(context)
        if (!file.isFile) return null

        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val report = buildString {
            appendLine(
                "Device: ${Build.MANUFACTURER} ${Build.MODEL} · " +
                    "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
            )
            appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(throwable.stackTraceToString())
            appendLine()
            appendLine("--- last session log entries ---")
            SessionLog.merged()
                .take(LOG_TAIL_ENTRIES)
                .asReversed()
                .forEach { entry -> appendLine("${entry.timestamp} ${entry.level.name} ${entry.message}") }
        }

        file(context).writeText(report)
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}
