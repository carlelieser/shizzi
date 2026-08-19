package dev.shizzi

import kotlin.math.roundToLong

/**
 * Bytes carried by a session, counted from the tethered device's point of view.
 *
 * "Up" is what those devices sent, "down" is what they received — the user's
 * reading, not the phone's, since the notification showing these numbers is
 * describing what their other devices are doing.
 */
data class Traffic(val up: Long = 0, val down: Long = 0) {

    /**
     * Renders a byte count the way a phone's data screen does.
     *
     * Decimal units rather than binary: 1.4 GB here has to agree with what
     * Android's own data usage screen says about the same traffic, and that
     * screen counts in powers of ten.
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

            // One decimal below 10, none above: "9.4 MB" but "94 MB", which is
            // how much precision reads as informative rather than as noise.
            val rendered = when {
                value < 10 -> String.format("%.1f", value)
                else -> value.roundToLong().toString()
            }
            return "$rendered ${SCALE[step]}"
        }
    }
}

