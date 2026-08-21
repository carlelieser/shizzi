package dev.shizzi

import java.io.BufferedReader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** What `pm install --apex --staged` reported, verbatim. */
data class StagingOutcome(val isStaged: Boolean, val rawOutput: String)

/** The JSON [StagingOutcome] crosses the binder as. */
fun StagingOutcome.toJson(): String = org.json.JSONObject().apply {
    put("staged", isStaged)
    put("output", rawOutput)
}.toString()

/**
 * Reads back what [toJson] wrote.
 *
 * Unparseable is not staged: an older shell daemon answers with something else
 * entirely, and reading silence as success would leave the user waiting for a
 * reboot to apply an install that never happened.
 */
fun parseStagingOutcome(report: String): StagingOutcome {
    val parsed = runCatching { org.json.JSONObject(report) }.getOrNull()

    return StagingOutcome(
        isStaged = parsed?.optBoolean("staged") == true,
        rawOutput = parsed?.optString("output").orEmpty()
            .ifEmpty { "not reported by the privileged process" },
    )
}

/**
 * Stages the tethering APEX for install on the next boot.
 *
 * Runs in the shell (uid 2000) process: `pm install --apex` is refused to an
 * ordinary app, and the file has to sit somewhere pm can read it. /data/local/tmp
 * is readable by the app but writable only here, so the copy is this side's job
 * too — the app hands over a path in its own files dir.
 *
 * apexd verifies the AVB footer against Google's key on its own. A re-signed
 * APEX is rejected there, so nothing in this class needs to prove authenticity;
 * the digest check before the file ever arrives is what guards the transport.
 */
class ApexInstaller(private val deadlineMs: Long = DEFAULT_DEADLINE_MS) {

    /**
     * Copies [localPath] somewhere pm can reach, stages it, and clears the copy.
     *
     * The staged copy goes whether or not the install took: pm has already read
     * the file by the time it answers, and leaving three megabytes in
     * /data/local/tmp after a rejection helps nobody.
     */
    fun stage(localPath: String): StagingOutcome {
        val staged = File(STAGING_DIR, TetheringApex.FILE_NAME)

        return try {
            File(localPath).copyTo(staged, overwrite = true)
            install(staged.absolutePath)
        } finally {
            staged.delete()
        }
    }

    /**
     * R4.6, as [UpstreamInspector] does it: both streams drained on their own
     * threads under a deadline. A process that fills an undrained pipe blocks
     * forever whatever waitFor says, and pm on a slow device is exactly the
     * caller that writes enough to find out.
     */
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
        /**
         * Generous next to the dumpsys deadline: an APEX install verifies an AVB
         * footer over three megabytes, which is slower than reading a dump.
         */
        const val DEFAULT_DEADLINE_MS = 120_000L

        /** Where pm can read from, and the app cannot write. */
        const val STAGING_DIR = "/data/local/tmp"

        /** What pm prints for a session that will apply on the next boot. */
        const val SUCCESS_MARKER = "Success"
    }
}
