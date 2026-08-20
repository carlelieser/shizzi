package dev.shizzi.ui

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
import dev.shizzi.ui.theme.MinTouchTarget
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface
import dev.shizzi.ui.theme.isPressed

private val ButtonWidth = 200.dp
private val ButtonHeight = 56.dp
private val SpinnerSize = 24.dp

/**
 * Turquoise to start, neutral to stop: only starting is the primary action, and
 * a filled Stop would compete with the connected status glyph for the eye.
 * Not red either — ending your own session is not destructive.
 *
 * Goes neutral with a spinner while coming up, since a turquoise button that
 * ignores taps invites them; Cancel is the live control in that window. The
 * footprint never changes, so nothing reflows.
 */
@Composable
fun ConnectButton(
    label: String,
    state: ConnectButtonState,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = ShizziTheme.colors
    val isEnabled = state != ConnectButtonState.DISABLED &&
        state != ConnectButtonState.LOADING

    Box(
        modifier = Modifier
            .width(ButtonWidth)
            .height(ButtonHeight)
            .brutalSurface(
                fill = when (state) {
                    ConnectButtonState.START -> colors.primary
                    else -> colors.surface
                },
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
        when (state) {
            ConnectButtonState.LOADING -> CircularProgressIndicator(
                color = colors.onSurfaceMuted,
                strokeWidth = 2.dp,
                modifier = Modifier.size(SpinnerSize),
            )

            else -> Text(
                text = label.uppercase(),
                style = ShizziTheme.typography.title,
                // onPrimary only on the filled button; the neutral fill needs a
                // colour that reads against the surface instead.
                color = when (state) {
                    ConnectButtonState.START -> colors.onPrimary
                    ConnectButtonState.STOP -> colors.onSurface
                    else -> colors.onSurfaceMuted
                },
            )
        }
    }
}

/** What the button is currently offering, which decides its fill and label colour. */
enum class ConnectButtonState { START, STOP, LOADING, DISABLED }

/**
 * Plain text rather than a second bordered box: two stacked surfaces would read
 * as equal choices, and cancelling is the lesser one.
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
