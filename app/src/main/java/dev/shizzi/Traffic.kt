package dev.shizzi

import kotlin.math.roundToLong

data class Traffic(val up: Long = 0, val down: Long = 0) {

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

            val rendered = when {
                value < 10 -> String.format("%.1f", value)
                else -> value.roundToLong().toString()
            }
            return "$rendered ${SCALE[step]}"
        }
    }
}

