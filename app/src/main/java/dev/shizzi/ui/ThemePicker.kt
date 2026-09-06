package dev.shizzi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.emphasizedSpring
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.themedIndication
import dev.shizzi.ui.theme.ThemeChoice
import dev.shizzi.ui.theme.themedSurface
import dev.shizzi.ui.theme.isPressed

private val OptionHeight = 44.dp

private val OptionIconSize = 20.dp

private const val PressSpin = 360f

private fun glyphFor(choice: ThemeChoice): ImageVector = when (choice) {
    ThemeChoice.SYSTEM -> Icons.Filled.Brightness4
    ThemeChoice.LIGHT -> Icons.Filled.LightMode
    ThemeChoice.DARK -> Icons.Filled.DarkMode
}

private fun labelFor(choice: ThemeChoice): String = when (choice) {
    ThemeChoice.SYSTEM -> "Match system"
    ThemeChoice.LIGHT -> "Light"
    ThemeChoice.DARK -> "Dark"
}

@Composable
fun ThemePicker(selected: ThemeChoice, onSelect: (ThemeChoice) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ShizziTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        ThemeChoice.entries.forEach { choice ->
            ThemeOption(
                choice = choice,
                isSelected = choice == selected,
                onSelect = { onSelect(choice) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemeOption(
    choice: ThemeChoice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = ShizziTheme.colors
    val isPressed = interaction.isPressed()
    val spin = rememberPressSpin(isPressed)

    val fill by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.surface,
        animationSpec = standardSpring(),
        label = "themeFill",
    )

    val tint by animateColorAsState(
        targetValue = if (isSelected) colors.onPrimary else colors.onSurfaceMuted,
        animationSpec = standardSpring(),
        label = "themeTint",
    )

    Box(
        modifier = modifier
            .height(OptionHeight)
            .themedSurface(fill = fill, isPressed = isPressed)
            .clickable(
                interactionSource = interaction,
                indication = themedIndication(),
            ) {
                onSelect()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyphFor(choice),
            contentDescription = labelFor(choice),
            tint = tint,
            modifier = Modifier
                .size(OptionIconSize)
                .graphicsLayer { rotationZ = spin },
        )
    }
}

/** Spins the glyph a full turn on each press, including a re-press of the active mode. */
@Composable
private fun rememberPressSpin(isPressed: Boolean): Float {
    val rotation = remember { Animatable(0f) }
    val turns = remember { mutableIntStateOf(0) }
    val spinSpec = emphasizedSpring<Float>()

    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect

        turns.intValue += 1
        rotation.animateTo(
            targetValue = turns.intValue * PressSpin,
            animationSpec = spinSpec,
        )
    }

    return rotation.value
}
