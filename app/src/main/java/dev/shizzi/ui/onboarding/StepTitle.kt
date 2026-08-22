package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.shizzi.ui.theme.ShizziTheme

private val TitleSize = 44.sp

private val MinTitleSize = 26.sp

private val TitleStep = 1.sp

val StepTitleStyle: TextStyle
    @Composable get() = ShizziTheme.typography.display.copy(
        fontSize = TitleSize,

        lineBreak = LineBreak.Heading,

        letterSpacing = (-0.04).em,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

@Composable
fun StepTitle(text: String) {
    BasicText(
        text = text,
        style = StepTitleStyle.copy(color = ShizziTheme.colors.onSurface),

        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MinTitleSize,
            maxFontSize = TitleSize,
            stepSize = TitleStep,
        ),
        modifier = Modifier.padding(bottom = ShizziTheme.spacing.xl),
    )
}
