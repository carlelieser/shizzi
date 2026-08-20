package dev.shizzi

import java.io.BufferedReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What the tethering stack currently reports as its upstream. */
data class UpstreamObservation(
    val rawOutput: String,
    val interfaceNames: List<String>,
    val didTimeout: Boolean,
) {
    /**
     * [interfaceNames] minus any interface the kernel no longer has.
     *
     * Tethering keeps naming destroyed interfaces: its detach needs the
     * interface to exist, so once the TUN is gone the reference is stuck for
     * the boot. Teardown ordering stops one being created; this is what saves a
     * device that already has one from failing every future session.
     *
     * Dropping only *absent* interfaces leaves the guarantee intact — the risk
     * is clients routing over a real physical upstream, and an interface that
     * does not exist carries nothing. Anything present and competing is still a
     * hard failure.
     *
     * @param owned kept unconditionally: a check that raced the session's own
     *   TUN into existence must not discard it.
     */
    fun liveInterfaceNames(owned: String): List<String> {
        val (live, absent) = interfaceNames.partition { it == owned || interfaceExists(it) }
        if (absent.isNotEmpty()) {
            SessionLog.warn("ignoring upstream that no longer exists: $absent")
        }
        return live
    }
}

/**
 * Sysfs rather than shelling out to `ip`: this runs on the watchdog's polling
 * path, so the answer is one stat instead of a process launch. Unreadable
 * counts as present, keeping the caller failing closed.
 */
private fun interfaceExists(name: String): Boolean =
    runCatching { java.io.File("/sys/class/net/$name").exists() }.getOrDefault(true)

/**
 * Reads tethering upstream state out of `dumpsys tethering`.
 *
 * R4.6: drained concurrently under a short deadline, so a stalled OEM tethering
 * service cannot wedge teardown. Both streams get their own thread — a process
 * that fills an undrained pipe buffer blocks forever whatever waitFor says.
 */
class UpstreamInspector(private val deadlineMs: Long = DEFAULT_DEADLINE_MS) {

    fun observe(): UpstreamObservation = run("tethering")

    /** For facts tethering's own dump reports only historically. */
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

    /**
     * Reads only lines stating which upstream tethering has *selected*. An
     * earlier version matched any "Upstream:", which caught the BPF rule tables:
     *
     *     IPv4 Upstream: proto [inDstMac] iif(iface) src -> nat -> dst ...
     *
     * Those hold rules for interfaces that no longer exist — a destroyed TUN
     * was still listed seven sessions later, failing every start with "expected
     * only testtun77, tethering reports [testtun70]" while `ip link` showed no
     * TUN at all. Rule tables describe what was, not what is.
     *
     * The format is not contractual, so the match stays loose within that
     * narrower set and [rawOutput] is kept so a parse miss is visible rather
     * than silently wrong.
     */
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

        /**
         * Prefixes rather than substrings, and no bare "Upstream:" — that
         * matched the nested BPF rule-table headers and pulled in interfaces
         * already destroyed.
         */
        private val UPSTREAM_HINTS = listOf(
            "current upstream interface(s):",
            "current upstream:",
            "selected upstream:",
            "upstream network:",
            "mCurrentUpstream",
        )

        /** Interface-shaped tokens: testtun0, wlan0, rmnet_data1, ... */
        private val INTERFACE_PATTERN =
            Regex("""\b(testtun\d+|wlan\d+|rmnet[a-z_]*\d*|eth\d+|ap\d+|swlan\d+)\b""")
    }
}
