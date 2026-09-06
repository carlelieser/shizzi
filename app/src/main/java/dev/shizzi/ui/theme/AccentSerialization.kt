package dev.shizzi.ui.theme

private const val DEFAULT_NAME = "default"
private const val EXPRESSIVE_NAME = "expressive"
private const val OPAQUE = 0xFF000000.toInt()
private const val RGB_MASK = 0x00FFFFFF

fun AccentChoice.serialize(): String = when (this) {
    AccentChoice.Default -> DEFAULT_NAME
    AccentChoice.Expressive -> EXPRESSIVE_NAME
    is AccentChoice.Custom -> argb.toHex()
}

fun parseAccent(stored: String?): AccentChoice = when (stored) {
    null, "", DEFAULT_NAME -> AccentChoice.Default
    EXPRESSIVE_NAME -> AccentChoice.Expressive
    else -> parseHex(stored)?.let(AccentChoice::Custom) ?: AccentChoice.Default
}

fun serializeAccents(accents: List<Int>): String =
    accents.joinToString(separator = ",") { it.toHex() }

fun parseAccents(stored: String?): List<Int> = stored
    .orEmpty()
    .split(",")
    .mapNotNull { parseHex(it.trim()) }

private fun Int.toHex(): String = "#%06X".format(this and RGB_MASK)

private fun parseHex(value: String): Int? {
    if (!value.startsWith("#") || value.length != 7) return null

    return value.drop(1).toIntOrNull(radix = 16)?.or(OPAQUE)
}
