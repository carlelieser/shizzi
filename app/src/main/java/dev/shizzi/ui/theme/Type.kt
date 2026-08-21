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

private val Display = FontFamily(
    variableWeight(R.font.space_grotesk, 500),
    variableWeight(R.font.space_grotesk, 700),
)

@OptIn(ExperimentalTextApi::class)
private val Sans = FontFamily(
    variableWeight(R.font.inter, 400),
    variableWeight(R.font.inter, 500),
)

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

    display = TextStyle(
        fontFamily = Display,
        fontSize = 28.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.02).em,
    ),

    heading = TextStyle(
        fontFamily = Display,
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.01).em,
    ),

    subheading = TextStyle(
        fontFamily = Display,
        fontSize = 17.sp,
        fontWeight = FontWeight.W500,
    ),

    title = TextStyle(fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.W700),

    label = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.W500),

    caption = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.08.em,
    ),

    log = TextStyle(fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.W400),
    body = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.W400),
)
