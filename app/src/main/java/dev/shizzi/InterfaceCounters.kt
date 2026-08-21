package dev.shizzi

import java.io.File

object InterfaceCounters {

    fun read(interfaceName: String): Traffic {
        val line = runCatching { File(PROC_NET_DEV).readLines() }
            .getOrDefault(emptyList())
            .firstOrNull { it.trimStart().startsWith("$interfaceName:") }
            ?: return Traffic()

        val fields = line.substringAfter(':').trim().split(WHITESPACE)
        if (fields.size <= TX_BYTES_FIELD) return Traffic()

        return Traffic(

            down = fields[RX_BYTES_FIELD].toLongOrNull() ?: 0,
            up = fields[TX_BYTES_FIELD].toLongOrNull() ?: 0,
        )
    }

    private val WHITESPACE = Regex("""\s+""")
    private const val PROC_NET_DEV = "/proc/net/dev"

    private const val RX_BYTES_FIELD = 0
    private const val TX_BYTES_FIELD = 8
}
