package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.isPressed
import dev.shizzi.ui.theme.themedIndication
import dev.shizzi.ui.theme.themedSurface

private val PreviewHeight = 56.dp

private val ButtonHeight = 44.dp

@Composable
fun ColorPickerSheet(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var selection by remember { mutableStateOf(HueSaturation(hue = 0f, saturation = 1f)) }
    var value by remember { mutableFloatStateOf(1f) }
    val color = Color.hsv(selection.hue, selection.saturation, value)

    ThemedBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "Color picker",
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        Spacer(Modifier.height(ShizziTheme.spacing.lg))

        ColorWheel(selection = selection, value = value, onSelect = { selection = it })

        Spacer(Modifier.height(ShizziTheme.spacing.lg))

        Slider(value = value, onValueChange = { value = it })

        Spacer(Modifier.height(ShizziTheme.spacing.md))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PreviewHeight)
                .themedSurface(fill = color),
        )

        Spacer(Modifier.height(ShizziTheme.spacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AddColorButton(onClick = { onConfirm(color.toArgb()) })
        }

        Spacer(Modifier.height(ShizziTheme.spacing.lg))
    }
}

@Composable
private fun AddColorButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val colors = ShizziTheme.colors

    Box(
        modifier = Modifier
            .height(ButtonHeight)
            .themedSurface(fill = colors.primary, isPressed = interaction.isPressed())
            .clickable(
                interactionSource = interaction,
                indication = themedIndication(),
                onClick = onClick,
            )
            .padding(horizontal = ShizziTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Add color".uppercase(),
            style = ShizziTheme.typography.title,
            color = colors.onPrimary,
        )
    }
}
