package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.fastSpring

private val ButtonWidth = 200.dp
private val ButtonHeight = 56.dp
private val SpinnerSize = 24.dp
private val RestCorner = 28.dp
private val ActiveCorner = 16.dp
private val PressedCorner = 12.dp

@Composable
fun ConnectButton(
    label: String,
    state: ConnectButtonState,
    onClick: () -> Unit,
) {
    val colors = ShizziTheme.colors
    val isEnabled = state != ConnectButtonState.DISABLED && state != ConnectButtonState.LOADING
    val isPrimary = state == ConnectButtonState.START
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            isPressed -> PressedCorner
            state == ConnectButtonState.STOP -> ActiveCorner
            else -> RestCorner
        },
        animationSpec = fastSpring(),
        label = "connectButtonCorner",
    )

    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier.width(ButtonWidth).height(ButtonHeight),
        shape = RoundedCornerShape(corner),
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) colors.primary else colors.surfaceContainer,
            contentColor = if (isPrimary) colors.onPrimary else colors.onSurface,
            disabledContainerColor = colors.surfaceContainer,
            disabledContentColor = colors.onSurfaceMuted,
        ),
    ) {
        if (state == ConnectButtonState.LOADING) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(SpinnerSize),
            )
        } else {
            Text(text = label, style = ShizziTheme.typography.title)
        }
    }
}

enum class ConnectButtonState { START, STOP, LOADING, DISABLED }

@Composable
fun CancelButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (isPressed) 10.dp else 20.dp,
        animationSpec = fastSpring(),
        label = "cancelButtonCorner",
    )

    TextButton(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
    ) {
        Text(
            text = str(R.string.action_cancel),
            style = ShizziTheme.typography.label,
        )
    }
}
