package dev.shizzi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Named by role rather than hue. Turquoise is the only colour in the app and
 * marks a state worth acting on, not one merely true — so an error speaks
 * through the text of its toast rather than a red icon carrying alarm without
 * information.
 */
@Immutable
data class ShizziColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val border: Color,
    val shadow: Color,
    val isDark: Boolean,
)

/** Turquoise is light enough that white on it fails contrast at body sizes. */
private val OnPrimary = Color(0xFF000000)

val LightColors = ShizziColors(
    primary = Color(0xFF14B8A6),
    onPrimary = OnPrimary,
    background = Color(0xFFFAFAF9),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C0A09),
    onSurfaceMuted = Color(0xFF57534E),
    border = Color(0xFF000000),
    shadow = Color(0xFF000000),
    isDark = false,
)

/**
 * Inverts the structural colours rather than recolouring them: the flat offset
 * shadow is load-bearing, and a black shadow on a near-black background is
 * invisible. Borders and shadows go white — a different look, not a tinted copy.
 */
val DarkColors = ShizziColors(
    primary = Color(0xFF2DD4BF),
    onPrimary = OnPrimary,
    background = Color(0xFF0C0A09),
    surface = Color(0xFF1C1917),
    onSurface = Color(0xFFFAFAF9),
    onSurfaceMuted = Color(0xFFA8A29E),
    border = Color(0xFFFFFFFF),
    shadow = Color(0xFFFFFFFF),
    isDark = true,
)
