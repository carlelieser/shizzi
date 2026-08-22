package dev.shizzi

import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class StagingOutcome(val isStaged: Boolean, val rawOutput: String)

fun StagingOutcome.toJson(): String = org.json.JSONObject().apply {
    put("staged", isStaged)
    put("output", rawOutput)
}.toString()

fun parseStagingOutcome(report: String): StagingOutcome {
    val parsed = runCatching { org.json.JSONObject(report) }.getOrNull()

    return StagingOutcome(
        isStaged = parsed?.optBoolean("staged") == true,
        rawOutput = parsed?.optString("output").orEmpty()
            .ifEmpty { "not reported by the privileged process" },
    )
}

class ApexInstaller(private val deadlineMs: Long = DEFAULT_DEADLINE_MS) {

    fun stage(apex: ParcelFileDescriptor): StagingOutcome {
        val staged = File(STAGING_DIR, TetheringApex.FILE_NAME)

        return try {
            ParcelFileDescriptor.AutoCloseInputStream(apex).use { source ->
                staged.outputStream().use(source::copyTo)
            }
            install(staged.absolutePath)
        } finally {
            staged.delete()
        }
    }

    private fun install(path: String): StagingOutcome {
        val process = ProcessBuilder("pm", "install", "--apex", "--staged", path).start()
        val drainPool = Executors.newFixedThreadPool(2)

        val stdout = drainPool.submit<String> { process.inputStream.drain() }
        val stderr = drainPool.submit<String> { process.errorStream.drain() }

        val didExit = process.waitFor(deadlineMs, TimeUnit.MILLISECONDS)
        if (!didExit) process.destroyForcibly()

        val output = collectOutput(stdout, stderr)
        drainPool.shutdownNow()

        return StagingOutcome(
            isStaged = didExit && output.contains(SUCCESS_MARKER, ignoreCase = true),
            rawOutput = when {
                didExit -> output
                else -> "pm install did not exit within ${deadlineMs}ms\n$output"
            },
        )
    }

    private fun collectOutput(stdout: Future<String>, stderr: Future<String>): String {
        val out = runCatching { stdout.get(deadlineMs, TimeUnit.MILLISECONDS) }.getOrDefault("")
        val err = runCatching { stderr.get(deadlineMs, TimeUnit.MILLISECONDS) }.getOrDefault("")
        return listOf(out, err).filter { it.isNotBlank() }.joinToString("\n").trim()
    }

    private fun java.io.InputStream.drain(): String =
        bufferedReader().use(BufferedReader::readText)

    private companion object {
        const val DEFAULT_DEADLINE_MS = 120_000L

        const val STAGING_DIR = "/data/local/tmp"

        const val SUCCESS_MARKER = "Success"
    }
}
