package dev.shizzi.spike.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.spike.ui.theme.MinTouchTarget
import dev.shizzi.spike.ui.theme.ShizziTheme
import dev.shizzi.spike.ui.theme.brutalSurface
import dev.shizzi.spike.ui.theme.isPressed

private val ButtonWidth = 200.dp
private val ButtonHeight = 56.dp
private val SpinnerSize = 24.dp

/**
 * The one primary action, in the one primary colour.
 *
 * Turquoise whether it starts or stops: colour here would be decoration, since
 * the label already says which it does, and a red stop button would imply the
 * destructive-action treatment that tearing down your own tunnel does not
 * warrant. Only the label and the enabled state change.
 *
 * While a session is coming up the button becomes a progress indicator, keeping
 * its footprint so the layout does not reflow around a spinner.
 */
@Composable
fun ConnectButton(
    label: String,
    isEnabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = ShizziTheme.colors

    Box(
        modifier = Modifier
            .width(ButtonWidth)
            .height(ButtonHeight)
            .brutalSurface(
                fill = if (isEnabled || isLoading) colors.primary else colors.surface,
                isPressed = interaction.isPressed(),
            )
            .clickable(
                enabled = isEnabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                color = colors.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(SpinnerSize),
            )

            else -> Text(
                text = label.uppercase(),
                style = ShizziTheme.typography.title,
                color = if (isEnabled) colors.onPrimary else colors.onSurfaceMuted,
            )
        }
    }
}

/**
 * The way out of a start that is taking too long.
 *
 * Plain text rather than a second bordered box: two surfaces stacked under each
 * other would read as two equal choices, and cancelling is the lesser one.
 */
@Composable
fun CancelButton(onClick: () -> Unit) {
    Box(
        // Sized to the touch minimum rather than to the text, which is shorter
        // than a finger.
        modifier = Modifier
            .height(MinTouchTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "CANCEL",
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}
