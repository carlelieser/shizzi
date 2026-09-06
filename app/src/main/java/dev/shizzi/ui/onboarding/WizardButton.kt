package dev.shizzi.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween
import dev.shizzi.ui.theme.themedLabel
import dev.shizzi.ui.theme.themedIndication
import dev.shizzi.ui.theme.themedSurface
import dev.shizzi.ui.theme.isPressed

private val ButtonHeight = 56.dp

@Composable
fun WizardButton(action: WizardAction, isPrimary: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val colors = ShizziTheme.colors
    val isFilled = isPrimary && action.isEnabled

    val fill by animateColorAsState(
        targetValue = if (isFilled) colors.primary else colors.surface,
        animationSpec = standardSpring(),
        label = "wizardButtonFill",
    )

    val content by animateColorAsState(
        targetValue = contentColor(isFilled = isFilled, isEnabled = action.isEnabled),
        animationSpec = standardSpring(),
        label = "wizardButtonContent",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .themedSurface(
                fill = fill,
                isPressed = action.isEnabled && interaction.isPressed(),
            )
            .clickable(
                enabled = action.isEnabled,
                interactionSource = interaction,
                indication = themedIndication(),
                onClick = action.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WizardButtonLabel(label = action.label, color = content)
    }
}

@Composable
private fun contentColor(isFilled: Boolean, isEnabled: Boolean): Color = when {
    isFilled -> ShizziTheme.colors.onPrimary
    isEnabled -> ShizziTheme.colors.onSurface
    else -> ShizziTheme.colors.onSurfaceMuted
}

@Composable
private fun WizardButtonLabel(label: String, color: Color) {
    val fadeSpec = standardTween<Float>()

    AnimatedContent(
        targetState = label,
        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
        label = "wizardButtonLabel",
    ) { text ->
        Text(
            text = themedLabel(text),
            style = ShizziTheme.typography.title,
            color = color,
        )
    }
}
