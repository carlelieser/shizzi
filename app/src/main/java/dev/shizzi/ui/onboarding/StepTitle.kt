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

/** Well past [ShizziTypography.display]'s 28sp, which the app's screens use. */
private val TitleSize = 72.sp

/**
 * The floor for a title that has to shrink. Still well above the app's own
 * 28sp headings, so a wrapped step does not stop reading as a wizard title.
 */
private val MinTitleSize = 40.sp

/** Fine enough that the drop to a fitting size is not visible as a jump. */
private val TitleStep = 1.sp

/**
 * The onboarding heading treatment, shared by every step.
 *
 * Built on `display` rather than replacing it: the app's own screens set titles
 * at 28sp, and a wizard step is a different kind of page — no back button, no
 * rule, the title alone at the top of an open screen.
 */
val StepTitleStyle: TextStyle
    @Composable get() = ShizziTheme.typography.display.copy(
        fontSize = TitleSize,
        // Breaks between words only. The default strategy is free to break
        // inside one when it does not fit, which at 72sp is most of them.
        lineBreak = LineBreak.Heading,
        // The display style's tracking is set for 28sp; at this size the same
        // proportion opens the word up rather than closing it.
        letterSpacing = (-0.04).em,
        // Trims the font's built-in leading, which at 72sp leaves a visible gap
        // above the cap height and unbalances whatever sits with it.
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

/**
 * Names a step above its content.
 *
 * [TitleSize] is a ceiling rather than a fixed size: "Compatibility" is wider
 * than the screen at 72sp, and with no legal break inside a word the layout
 * splits it mid-word. Shrinking only the titles that do not fit keeps every
 * other step at full size.
 */
@Composable
fun StepTitle(text: String) {
    BasicText(
        text = text,
        style = StepTitleStyle.copy(color = ShizziTheme.colors.onSurface),
        // One line is what forces the shrink: given unbounded height, autoSize
        // fits by wrapping — and with no legal break inside "Compatibility",
        // wrapping means splitting it mid-word.
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MinTitleSize,
            maxFontSize = TitleSize,
            stepSize = TitleStep,
        ),
        modifier = Modifier.padding(bottom = ShizziTheme.spacing.xl),
    )
}
