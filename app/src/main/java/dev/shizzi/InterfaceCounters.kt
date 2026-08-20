package dev.shizzi

import java.io.File

/**
 * Reads an interface's byte counters straight from the kernel, keeping the byte
 * half of a status read off `dumpsys`: a subprocess costs ~127ms on device
 * (~75ms of it fork/exec) against a few hundred microseconds here, and status
 * polls for the life of a session.
 *
 * These are the counters the tethering BPF stats report — sampled together
 * across a 52 MB transfer, the TUN's rx and ForwardedStats' rxb tracked to
 * within a few kilobytes.
 */
object InterfaceCounters {

    /**
     * @param interfaceName the session's own TUN, named rather than matched by
     *   prefix: a stale TUN can still be present (a device showed testtun24
     *   beside the live testtun25) and summing both reports traffic this
     *   session never carried.
     *
     * @return zeroes when the interface is absent — the honest reading for a
     *   session that has not built its TUN yet.
     */
    fun read(interfaceName: String): Traffic {
        val line = runCatching { File(PROC_NET_DEV).readLines() }
            .getOrDefault(emptyList())
            .firstOrNull { it.trimStart().startsWith("$interfaceName:") }
            ?: return Traffic()

        // Split on the colon, not whitespace: a wide enough counter fills the
        // column and runs into the name, "testtun25:41573895 37687 0 ...".
        val fields = line.substringAfter(':').trim().split(WHITESPACE)
        if (fields.size <= TX_BYTES_FIELD) return Traffic()

        return Traffic(
            // rx/tx are named from the interface's side, inverting the user's:
            // bytes the TUN received are bytes the tethered device downloaded.
            down = fields[RX_BYTES_FIELD].toLongOrNull() ?: 0,
            up = fields[TX_BYTES_FIELD].toLongOrNull() ?: 0,
        )
    }

    private val WHITESPACE = Regex("""\s+""")
    private const val PROC_NET_DEV = "/proc/net/dev"

    /** Field order is fixed by the kernel: rx bytes first, tx bytes ninth. */
    private const val RX_BYTES_FIELD = 0
    private const val TX_BYTES_FIELD = 8
}
