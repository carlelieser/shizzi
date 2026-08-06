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
 * All three faces are variable fonts, so one file covers every weight.
 *
 * Three static JetBrains Mono weights measured 808 KB against 296 KB for the
 * variable file, and the variable file also leaves every intermediate weight
 * available if a later screen needs one.
 *
 * FontVariation is experimental in Compose but the underlying platform support
 * has shipped since API 26, well below this app's minSdk of 29.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableWeight(resource: Int, weight: Int) = Font(
    resource,
    // Also passed as the declared FontWeight so the family can resolve a style
    // to the right file. Setting only the variation axis leaves every entry
    // claiming W400, and the family then answers every request with the first.
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Mono = FontFamily(
    variableWeight(R.font.jetbrains_mono, 400),
    variableWeight(R.font.jetbrains_mono, 500),
    variableWeight(R.font.jetbrains_mono, 700),
)

/**
 * Space Grotesk's weight axis spans 300-700 and *defaults to 300*, so every
 * style below names its weight rather than relying on the file's default, which
 * would otherwise render headings Light.
 */
private val Display = FontFamily(
    variableWeight(R.font.space_grotesk, 500),
    variableWeight(R.font.space_grotesk, 700),
)

@OptIn(ExperimentalTextApi::class)
private val Sans = FontFamily(
    variableWeight(R.font.inter, 400),
    variableWeight(R.font.inter, 500),
)

/**
 * The type scale, split three ways by role.
 *
 * Headings are Space Grotesk: it carries the geometric, slightly odd character
 * this design wants at the sizes where letterforms are actually visible, which
 * is exactly where a neutral face says nothing.
 *
 * Prose is Inter, on the strings long enough that reading them is the point.
 *
 * Labels are JetBrains Mono — button text, badges, captions, log lines. These
 * name a thing rather than being read as a sentence, and mono is what makes
 * uppercase tracked text read as deliberate rather than shouted.
 *
 * The split keeps mono off continuous prose, where uniform advance widths
 * flatten the word shapes readers recognise: irrelevant for a two-word label,
 * real for a paragraph.
 */
@Immutable
data class ShizziTypography(
    val display: TextStyle,
    val heading: TextStyle,
    val title: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val log: TextStyle,
    val body: TextStyle,
)

val Typography = ShizziTypography(
    // Slight negative tracking: Space Grotesk sets a touch loose at display
    // sizes, and the default spacing reads as a gap rather than a word.
    display = TextStyle(
        fontFamily = Display,
        fontSize = 28.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.02).em,
    ),

    /** Screen titles. */
    heading = TextStyle(
        fontFamily = Display,
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.01).em,
    ),

    /** Button text, which is uppercase wherever it is used. */
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
