package dev.shizzi.ui.theme

sealed interface AccentChoice {
    data object Default : AccentChoice

    data object Expressive : AccentChoice

    data class Custom(val argb: Int) : AccentChoice
}

val PresetAccents: List<Int> = listOf(
    0xFFE11D48.toInt(),
    0xFFF97316.toInt(),
    0xFFEAB308.toInt(),
    0xFF22C55E.toInt(),
    0xFF06B6D4.toInt(),
    0xFF3B82F6.toInt(),
    0xFF8B5CF6.toInt(),
    0xFFEC4899.toInt(),
)
