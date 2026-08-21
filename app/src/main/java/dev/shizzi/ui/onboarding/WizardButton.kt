package dev.shizzi.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface
import dev.shizzi.ui.theme.isPressed

/** Matches the connect button, so the two read as the same control. */
private val ButtonHeight = 56.dp

/**
 * Full-width footer button.
 *
 * Turquoise for the step's own action and neutral for the way past it, which
 * keeps the app's rule that the saturated element is the one worth acting on.
 * A disabled button keeps its footprint and loses its fill, so a footer whose
 * primary becomes available does not reflow.
 */
@Composable
fun WizardButton(action: WizardAction, isPrimary: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val colors = ShizziTheme.colors
    val isFilled = isPrimary && action.isEnabled

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .brutalSurface(
                fill = if (isFilled) colors.primary else colors.surface,
                isPressed = action.isEnabled && interaction.isPressed(),
            )
            .clickable(
                enabled = action.isEnabled,
                interactionSource = interaction,
                indication = null,
                onClick = action.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = action.label.uppercase(),
            style = ShizziTheme.typography.title,
            color = when {
                isFilled -> colors.onPrimary
                action.isEnabled -> colors.onSurface
                else -> colors.onSurfaceMuted
            },
        )
    }
}
