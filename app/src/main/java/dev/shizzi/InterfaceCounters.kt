package dev.shizzi

import java.io.File

/**
 * Reads an interface's byte counters straight from the kernel.
 *
 * Exists to keep the byte half of a status read off `dumpsys`. Measured on
 * device, spawning a subprocess costs ~127ms — of which ~75ms is fork/exec
 * alone — and status is polled for the life of a session. Reading this file in
 * process is a few hundred microseconds, which is what makes a fast refresh
 * affordable at all.
 *
 * The counters are the same ones the tethering BPF stats report: sampled
 * together across a 52 MB transfer, the TUN's rx and ForwardedStats' rxb
 * tracked each other to within a few kilobytes.
 */
object InterfaceCounters {

    /**
     * @param interfaceName the session's own TUN. Named rather than matched by
     *   prefix because a stale TUN from an earlier session can still be present
     *   — a device showed testtun24 alongside the live testtun25 — and summing
     *   both would report traffic the current session never carried.
     *
     * @return zeroes when the interface is absent, which is the honest reading
     *   for a session that has not built its TUN yet.
     */
    fun read(interfaceName: String): Traffic {
        val line = runCatching { File(PROC_NET_DEV).readLines() }
            .getOrDefault(emptyList())
            .firstOrNull { it.trimStart().startsWith("$interfaceName:") }
            ?: return Traffic()

        // "testtun25: 41573895 37687 0 ..." — the name and its colon run
        // together when the counter is wide enough to fill the column, so the
        // split is on the colon rather than on whitespace.
        val fields = line.substringAfter(':').trim().split(WHITESPACE)
        if (fields.size <= TX_BYTES_FIELD) return Traffic()

        return Traffic(
            // Receive and transmit are named from the interface's point of
            // view, which inverts the user's: bytes the TUN received are bytes
            // the tethered device downloaded.
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
