package dev.shizzi.spike.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.shizzi.spike.R

/**
 * Both faces are variable fonts, so one file covers every weight.
 *
 * Three static JetBrains Mono weights measured 808 KB against 296 KB for the
 * variable file, and the variable file also leaves every intermediate weight
 * available if a later screen needs one.
 *
 * FontVariation is experimental in Compose but the underlying platform support
 * has shipped since API 26, well below this app's minSdk of 29.
 */
@OptIn(ExperimentalTextApi::class)
private fun monoWeight(weight: Int) = Font(
    R.font.jetbrains_mono,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Mono = FontFamily(
    monoWeight(400),
    monoWeight(500),
    monoWeight(700),
)

@OptIn(ExperimentalTextApi::class)
private val Sans = FontFamily(
    Font(
        R.font.inter,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

/**
 * The type scale, split by role: structure is mono, prose is sans.
 *
 * Anything that names a thing — a screen title, a settings item, a status
 * badge, a log line — is JetBrains Mono, which is the register this design
 * wants and what makes uppercase tracked text read as deliberate. Anything
 * read as a sentence is Inter.
 *
 * That split keeps mono off the only strings long enough for its cost to
 * matter: uniform advance widths flatten the word shapes readers recognise,
 * which slows continuous reading. Irrelevant for a two-word label, real for a
 * paragraph.
 */
@Immutable
data class ShizziTypography(
    val display: TextStyle,
    val title: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val log: TextStyle,
    val body: TextStyle,
)

val Typography = ShizziTypography(
    display = TextStyle(fontFamily = Mono, fontSize = 28.sp, fontWeight = FontWeight.W700),
    title = TextStyle(fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.W700),
    label = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.W500),

    // Uppercase everywhere it is used; the tracking is what makes 11sp read as
    // deliberate rather than merely small.
    caption = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.08.em,
    ),

    log = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.W400),
    body = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.W400),
)
