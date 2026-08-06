package dev.shizzi.spike.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, named by role rather than by hue.
 *
 * Turquoise is the only colour in the app; everything else is neutral. A state
 * worth acting on is turquoise, a state that is merely true is not — so the
 * connect button and the connected status icon are the only saturated things
 * on screen, and an error is communicated by the text of its toast rather than
 * by a red icon that carries alarm without information.
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
    val borderPrimary: Color,
    val shadow: Color,
    val isDark: Boolean,
)

/**
 * Black on turquoise in both themes.
 *
 * Turquoise is light enough that white text on it fails contrast at body
 * sizes, so the button label is black whichever theme is active.
 */
private val OnPrimary = Color(0xFF000000)

val LightColors = ShizziColors(
    primary = Color(0xFF14B8A6),
    onPrimary = OnPrimary,
    background = Color(0xFFFAFAF9),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C0A09),
    onSurfaceMuted = Color(0xFF57534E),
    border = Color(0xFF000000),
    borderPrimary = Color(0xFF000000),
    shadow = Color(0xFF000000),
    isDark = false,
)

/**
 * Dark mode inverts the structural colours rather than recolouring them.
 *
 * The flat offset shadow is the load-bearing element of the whole design, and
 * a black shadow on a near-black background is invisible. So borders and
 * shadows go white for neutral elements and turquoise for primary ones, which
 * is a different look from the light theme rather than a tinted copy of it.
 */
val DarkColors = ShizziColors(
    primary = Color(0xFF2DD4BF),
    onPrimary = OnPrimary,
    background = Color(0xFF0C0A09),
    surface = Color(0xFF1C1917),
    onSurface = Color(0xFFFAFAF9),
    onSurfaceMuted = Color(0xFFA8A29E),
    border = Color(0xFFFFFFFF),
    borderPrimary = Color(0xFF2DD4BF),
    shadow = Color(0xFFFFFFFF),
    isDark = true,
)
