package dev.shizzi.spike.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.spike.ui.theme.ShizziTheme
import dev.shizzi.spike.ui.theme.ThemeChoice
import dev.shizzi.spike.ui.theme.brutalSurface
import dev.shizzi.spike.ui.theme.isPressed

/** Tall enough to take a finger, shorter than the connect button. */
private val OptionHeight = 44.dp

/**
 * The three theme options, as one row of adjacent surfaces.
 *
 * Not a radio group: this design has no unfilled-circle vocabulary, and
 * inventing one for a single screen would sit oddly beside everything else,
 * which is bordered boxes. A filled box is already how the app says "this one
 * is active" on the connect button.
 */
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

    Box(
        modifier = modifier
            .height(OptionHeight)
            .brutalSurface(
                fill = if (isSelected) colors.primary else colors.surface,
                isPressed = interaction.isPressed(),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onSelect,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = choice.name,
            style = ShizziTheme.typography.caption,
            color = if (isSelected) colors.onPrimary else colors.onSurfaceMuted,
        )
    }
}
