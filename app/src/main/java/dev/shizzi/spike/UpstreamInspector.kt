package dev.shizzi.spike

import java.io.BufferedReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What the tethering stack currently reports as its upstream. */
data class UpstreamObservation(
    val rawOutput: String,
    val interfaceNames: List<String>,
    val didTimeout: Boolean,
)

/**
 * Reads tethering upstream state out of `dumpsys tethering`.
 *
 * R4.6 requires the probe to be drained concurrently under a short deadline:
 * a stalled OEM tethering service must not be able to wedge teardown. Both
 * stdout and stderr are drained on separate threads, because a process that
 * fills an undrained pipe buffer blocks forever regardless of any timeout on
 * waitFor.
 */
class UpstreamInspector(private val deadlineMs: Long = DEFAULT_DEADLINE_MS) {

    fun observe(): UpstreamObservation {
        val process = ProcessBuilder("dumpsys", "tethering").start()
        val drainPool = Executors.newFixedThreadPool(2)

        val stdout = drainPool.submit<String> { process.inputStream.drain() }
        val stderr = drainPool.submit<String> { process.errorStream.drain() }

        val didExit = process.waitFor(deadlineMs, TimeUnit.MILLISECONDS)
        if (!didExit) process.destroyForcibly()

        val output = collectOutput(stdout, stderr)
        drainPool.shutdownNow()

        return UpstreamObservation(
            rawOutput = output,
            interfaceNames = parseUpstreamInterfaces(output),
            didTimeout = !didExit,
        )
    }

    private fun collectOutput(
        stdout: java.util.concurrent.Future<String>,
        stderr: java.util.concurrent.Future<String>,
    ): String {
        val out = runCatching { stdout.get(deadlineMs, TimeUnit.MILLISECONDS) }.getOrDefault("")
        val err = runCatching { stderr.get(deadlineMs, TimeUnit.MILLISECONDS) }.getOrDefault("")
        return if (err.isBlank()) out else "$out\n[stderr]\n$err"
    }

    /**
     * Extracts interface names from the upstream-related lines of the dump.
     *
     * The format is not contractual, so this is intentionally loose: it collects
     * candidates and lets the caller decide. The spike prints [rawOutput]
     * alongside, so a parse miss is visible rather than silently wrong.
     */
    private fun parseUpstreamInterfaces(output: String): List<String> {
        val interesting = output.lineSequence()
            .filter { line -> UPSTREAM_HINTS.any { hint -> line.contains(hint, ignoreCase = true) } }

        return interesting
            .flatMap { line -> INTERFACE_PATTERN.findAll(line).map { it.value } }
            .distinct()
            .toList()
    }

    private fun java.io.InputStream.drain(): String =
        bufferedReader().use(BufferedReader::readText)

    companion object {
        const val DEFAULT_DEADLINE_MS = 3_000L

        /** Lines likely to name the selected upstream across AOSP versions. */
        private val UPSTREAM_HINTS = listOf(
            "current upstream",
            "upstream network",
            "selected upstream",
            "mCurrentUpstream",
            "Upstream:",
        )

        /** Interface-shaped tokens: testtun0, wlan0, rmnet_data1, ... */
        private val INTERFACE_PATTERN =
            Regex("""\b(testtun\d+|wlan\d+|rmnet[a-z_]*\d*|eth\d+|ap\d+|swlan\d+)\b""")
    }
}
