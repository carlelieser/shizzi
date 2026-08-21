package dev.shizzi

import java.io.BufferedReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class UpstreamObservation(
    val rawOutput: String,
    val interfaceNames: List<String>,
    val didTimeout: Boolean,
) {

    fun liveInterfaceNames(owned: String): List<String> {
        val (live, absent) = interfaceNames.partition { it == owned || interfaceExists(it) }
        if (absent.isNotEmpty()) {
            SessionLog.warn("ignoring upstream that no longer exists: $absent")
        }
        return live
    }
}

private fun interfaceExists(name: String): Boolean =
    runCatching { java.io.File("/sys/class/net/$name").exists() }.getOrDefault(true)

class UpstreamInspector(private val deadlineMs: Long = DEFAULT_DEADLINE_MS) {

    fun observe(): UpstreamObservation = run("tethering")

    fun observeWifi(): UpstreamObservation = run("wifi")

    private fun run(service: String): UpstreamObservation {
        val process = ProcessBuilder("dumpsys", service).start()
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

    private fun parseUpstreamInterfaces(output: String): List<String> {
        val interesting = output.lineSequence()
            .map(String::trim)
            .filter { line -> UPSTREAM_HINTS.any { hint -> line.startsWith(hint, ignoreCase = true) } }

        return interesting
            .flatMap { line -> INTERFACE_PATTERN.findAll(line).map { it.value } }
            .distinct()
            .toList()
    }

    private fun java.io.InputStream.drain(): String =
        bufferedReader().use(BufferedReader::readText)

    companion object {
        const val DEFAULT_DEADLINE_MS = 3_000L

        private val UPSTREAM_HINTS = listOf(
            "current upstream interface(s):",
            "current upstream:",
            "selected upstream:",
            "upstream network:",
            "mCurrentUpstream",
        )

        private val INTERFACE_PATTERN =
            Regex("""\b(testtun\d+|wlan\d+|rmnet[a-z_]*\d*|eth\d+|ap\d+|swlan\d+)\b""")
    }
}
