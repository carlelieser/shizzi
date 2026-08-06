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
     * Tethering can keep naming an interface that has already been destroyed.
     * Its own detach needs the interface to exist — it logs "Could not detach
     * program: Fail to get interface params" and gives up — so once the TUN is
     * gone the reference is stuck for the life of the boot. Correct teardown
     * ordering is what stops one being created; this is what keeps a device
     * that already has one from failing every future session forever. Nobody
     * should have to reboot to recover from a session they were never told
     * about.
     *
     * Only *absent* interfaces are dropped, which leaves the guarantee intact:
     * the risk being guarded against is clients silently routing over a real
     * physical upstream, and an interface that does not exist cannot carry
     * their traffic. Anything present and competing is still a hard failure.
     *
     * @param owned kept unconditionally — it is the session's own TUN, and a
     *   check that raced its creation must not discard it.
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
 * Whether the kernel still has this interface.
 *
 * Reads sysfs rather than shelling out to `ip`: this runs on the watchdog's
 * polling path, and the answer is a single stat rather than a process launch.
 * An unreadable sysfs is treated as "present" so an unexpected failure keeps
 * the caller failing closed rather than silently ignoring a real upstream.
 */
private fun interfaceExists(name: String): Boolean =
    runCatching { java.io.File("/sys/class/net/$name").exists() }.getOrDefault(true)

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
     * Extracts the currently selected upstream interfaces from the dump.
     *
     * Reads only the lines that state which upstream tethering has *selected*.
     * An earlier version matched any line containing "Upstream:", which also
     * caught the BPF forwarding-rule tables:
     *
     *     IPv4 Upstream: proto [inDstMac] iif(iface) src -> nat -> dst ...
     *
     * Those tables hold rules for interfaces that no longer exist — a device
     * test found a destroyed TUN still listed there seven sessions later, which
     * failed every subsequent start with "expected only testtun77, tethering
     * reports [testtun70]" while `ip link` showed no TUN at all and the
     * authoritative line read null. Rule tables and quota maps describe what
     * was, not what is.
     *
     * The format is still not contractual, so the line match stays loose within
     * that narrower set and [rawOutput] is kept alongside so a parse miss is
     * visible rather than silently wrong.
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
         * Line prefixes that name the *selected* upstream, across AOSP versions.
         *
         * Prefixes rather than substrings, and deliberately without a bare
         * "Upstream:" — that one matched the BPF rule-table headers nested
         * under it and pulled in interfaces that had already been destroyed.
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
