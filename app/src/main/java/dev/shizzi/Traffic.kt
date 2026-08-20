package dev.shizzi

import kotlin.math.roundToLong

/**
 * Bytes carried by a session, from the tethered device's point of view: "up" is
 * what those devices sent. The notification describes what the user's other
 * devices are doing, not what the phone is doing.
 */
data class Traffic(val up: Long = 0, val down: Long = 0) {

    /**
     * Decimal units rather than binary, so 1.4 GB here agrees with Android's
     * own data usage screen, which counts in powers of ten.
     */
    companion object {
        private const val UNIT = 1000.0
        private val SCALE = listOf("B", "KB", "MB", "GB", "TB")

        fun format(bytes: Long): String {
            if (bytes < UNIT) return "$bytes B"

            var value = bytes.toDouble()
            var step = 0
            while (value >= UNIT && step < SCALE.lastIndex) {
                value /= UNIT
                step++
            }

            // "9.4 MB" but "94 MB" — past ten, the decimal is noise.
            val rendered = when {
                value < 10 -> String.format("%.1f", value)
                else -> value.roundToLong().toString()
            }
            return "$rendered ${SCALE[step]}"
        }
    }
}

