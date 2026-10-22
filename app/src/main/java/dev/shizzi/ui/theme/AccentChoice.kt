package dev.shizzi.ui.theme

sealed interface AccentChoice {
    data object Default : AccentChoice
    data object Expressive : AccentChoice
    data class Custom(val argb: Int) : AccentChoice
}

val PresetAccents: List<Int> = listOf(
    0xFF748B7A.toInt(), // sage
    0xFF72839A.toInt(), // slate blue
    0xFF827A98.toInt(), // muted violet
    0xFF9A7E86.toInt(), // dusty rose
    0xFF9A8A72.toInt(), // warm sand
    0xFF6F8E8E.toInt(), // mist teal
    0xFF7E896C.toInt(), // moss
    0xFF947E91.toInt(), // mauve
    0xFF9B8173.toInt(), // muted clay
)
