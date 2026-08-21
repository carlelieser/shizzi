package dev.shizzi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ShizziColors(
    val primary: Color,
    val onPrimary: Color,

    val primaryBright: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val border: Color,
    val shadow: Color,
    val isDark: Boolean,
)

private val OnPrimary = Color(0xFF000000)

val LightColors = ShizziColors(
    primary = Color(0xFF14B8A6),
    onPrimary = OnPrimary,
    primaryBright = Color(0xFF5EEAD4),
    background = Color(0xFFFAFAF9),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C0A09),
    onSurfaceMuted = Color(0xFF57534E),
    border = Color(0xFF000000),
    shadow = Color(0xFF000000),
    isDark = false,
)

val DarkColors = ShizziColors(
    primary = Color(0xFF2DD4BF),
    onPrimary = OnPrimary,

    primaryBright = Color(0xFF99F6E4),
    background = Color(0xFF0C0A09),
    surface = Color(0xFF1C1917),
    onSurface = Color(0xFFFAFAF9),
    onSurfaceMuted = Color(0xFFA8A29E),
    border = Color(0xFFFFFFFF),
    shadow = Color(0xFFFFFFFF),
    isDark = true,
)
