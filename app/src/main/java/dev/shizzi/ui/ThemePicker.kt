package dev.shizzi.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.ThemeChoice
import dev.shizzi.ui.theme.brutalSurface
import dev.shizzi.ui.theme.isPressed

/** Tall enough to take a finger, shorter than the connect button. */
private val OptionHeight = 44.dp

/** Sized like a settings row's trailing glyph rather than a header icon. */
private val OptionIconSize = 20.dp

/**
 * The glyph for each choice.
 *
 * A sun and a moon for the two explicit themes, and both at once for the one
 * that follows the device — Brightness4 is the rayed sun with a crescent cut
 * into it, which reads as "either, depending" and puts the three options in one
 * visual family rather than pairing two celestial glyphs with an unrelated
 * third.
 *
 * Not BrightnessAuto or SettingsBrightness, which read as automatic
 * *brightness* — a different setting that also exists on this device.
 */
private fun glyphFor(choice: ThemeChoice): ImageVector = when (choice) {
    ThemeChoice.SYSTEM -> Icons.Filled.Brightness4
    ThemeChoice.LIGHT -> Icons.Filled.LightMode
    ThemeChoice.DARK -> Icons.Filled.DarkMode
}

/**
 * What the option is called, for anything that cannot see the glyph.
 *
 * Written out rather than derived from the enum name: with the text gone this
 * is the only thing naming the option, which makes it real copy rather than a
 * debug string that happens to be readable.
 */
private fun labelFor(choice: ThemeChoice): String = when (choice) {
    ThemeChoice.SYSTEM -> "Match system"
    ThemeChoice.LIGHT -> "Light"
    ThemeChoice.DARK -> "Dark"
}

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
        // The name moves to the description rather than being dropped. It is
        // the only thing naming this option once the glyph replaces the word,
        // so a screen reader has nothing else to announce.
        Icon(
            imageVector = glyphFor(choice),
            contentDescription = labelFor(choice),
            tint = if (isSelected) colors.onPrimary else colors.onSurfaceMuted,
            modifier = Modifier.size(OptionIconSize),
        )
    }
}
