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
 * Declares one weight of a variable font.
 *
 * All three faces ship as single variable files: three static JetBrains Mono
 * weights measured 808 KB against 296 KB for the variable file.
 *
 * The catch is that the variation axis never reaches the typeface. Compose's
 * resource [Font] loads through `ResourcesCompat.getFont(context, resId)`,
 * which takes an ID and nothing else, and caches on it — so every entry
 * pointing at one file resolves to the same typeface at that file's *default*
 * weight, whatever weight was requested. The settings below are kept because
 * they cost nothing and record intent, but a family renders at one weight and
 * it is the file's, not this one's. That is why the bundled Space Grotesk is
 * rebased to default to Medium (see [Display]).
 *
 * The declared [FontWeight] still matters: it is how a family matches a
 * requested weight to an entry.
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
 * Space Grotesk, rebased to default to Medium.
 *
 * Upstream the weight axis is 300-700 defaulting to 300, and since a family
 * renders at its file's default (see [variableWeight]) every heading came out
 * Light. The bundled file is a partial instance limited to `wght=500:500:700`,
 * which moves the default onto Medium and keeps the axis, so the family renders
 * Medium without shipping a second file.
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
     * Names a row within a screen: a settings item, a section.
     *
     * Sits above [body] so a row's name outranks its own description. Borrowing
     * `label` here put the name at 13sp over a 14sp subtitle, which inverted the
     * hierarchy and read as small rather than as a heading.
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
