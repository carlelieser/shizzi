package dev.shizzi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.shizzi.R

/**
 * Declares one weight of a variable font. All three faces ship as single
 * variable files — three static JetBrains Mono weights measured 808 KB against
 * 296 KB.
 *
 * The variation axis never reaches the typeface: Compose's resource [Font]
 * loads through `ResourcesCompat.getFont(context, resId)`, which takes an ID
 * and caches on it, so every entry pointing at one file resolves to that file's
 * *default* weight whatever was requested. Hence [Display] being rebased. The
 * settings below record intent and cost nothing; the declared [FontWeight] does
 * still matter, as how a family matches a request to an entry.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableWeight(resource: Int, weight: Int) = Font(
    resource,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Mono = FontFamily(
    variableWeight(R.font.jetbrains_mono, 400),
    variableWeight(R.font.jetbrains_mono, 500),
    variableWeight(R.font.jetbrains_mono, 700),
)

/**
 * Rebased to default to Medium: upstream the axis is 300-700 defaulting to 300,
 * which rendered every heading Light. The bundled file is a partial instance
 * limited to `wght=500:500:700`.
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
 * Split three ways by role: Space Grotesk for headings, where letterforms are
 * visible and a neutral face says nothing; Inter for strings long enough that
 * reading them is the point; JetBrains Mono for labels, badges, and log lines,
 * which name a thing rather than being read as a sentence.
 *
 * The split is what keeps mono off prose, where uniform advance widths flatten
 * the word shapes readers recognise.
 */
@Immutable
data class ShizziTypography(
    val display: TextStyle,
    val heading: TextStyle,
    val subheading: TextStyle,
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

    /**
     * Names a row: a settings item, a section. Above [body] so a name outranks
     * its own description — borrowing `label` put a 13sp name over a 14sp
     * subtitle and inverted the hierarchy.
     */
    subheading = TextStyle(
        fontFamily = Display,
        fontSize = 17.sp,
        fontWeight = FontWeight.W500,
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
