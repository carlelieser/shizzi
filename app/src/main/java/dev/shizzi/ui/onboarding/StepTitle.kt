package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.shizzi.ui.theme.ShizziTheme

/**
 * Names a step above its content.
 *
 * `display` rather than [ScreenHeader]'s `heading`: a wizard step has no back
 * button or rule to share, and the title is the only thing at the top of an
 * otherwise open screen.
 */
@Composable
fun StepTitle(text: String) {
    Text(
        text = text,
        style = ShizziTheme.typography.display,
        color = ShizziTheme.colors.onSurface,
        modifier = Modifier.padding(bottom = ShizziTheme.spacing.xl),
    )
}
