package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DefaultAccentColor
import dev.shizzi.ui.theme.PresetAccents
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.SurfaceElevation
import dev.shizzi.ui.theme.emphasizedSpring
import dev.shizzi.ui.theme.standardTween
import dev.shizzi.ui.theme.themedSurface

private val SwatchSize = 48.dp

private val SwatchIconSize = 20.dp

private const val MarkEnterScale = 0.4f

private const val ContrastThreshold = 0.5f

fun accentLabel(accent: AccentChoice): String = when (accent) {
    AccentChoice.Default -> "Default"
    AccentChoice.Expressive -> "Wallpaper"
    is AccentChoice.Custom -> "#%06X".format(accent.argb and 0x00FFFFFF)
}

@Composable
fun AccentPicker(state: AccentPickerState, actions: AccentPickerActions) {
    var isPickingColor by remember { mutableStateOf(false) }

    ThemedBottomSheet(onDismiss = actions.onDismiss) {
        Text(
            text = "Accent",
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        Spacer(Modifier.height(ShizziTheme.spacing.lg))

        AccentSwatches(
            state = state,
            onSelect = actions.onSelect,
            onAddColor = { isPickingColor = true },
        )

        Spacer(Modifier.height(ShizziTheme.spacing.lg))
    }

    if (!isPickingColor) return

    ColorPickerSheet(
        onConfirm = { argb ->
            actions.onAddCustom(argb)
            actions.onSelect(AccentChoice.Custom(argb))
            isPickingColor = false
        },
        onDismiss = { isPickingColor = false },
    )
}

data class AccentPickerState(
    val selected: AccentChoice,
    val customAccents: List<Int>,
)

data class AccentPickerActions(
    val onSelect: (AccentChoice) -> Unit,
    val onAddCustom: (Int) -> Unit,
    val onDismiss: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(
    state: AccentPickerState,
    onSelect: (AccentChoice) -> Unit,
    onAddColor: () -> Unit,
) {
    val colors = ShizziTheme.colors
    val customChoices = state.customAccents.map(AccentChoice::Custom)
    val presetChoices = PresetAccents.map(AccentChoice::Custom)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Swatch(
            style = SwatchStyle(
                fill = DefaultAccentColor,
                isSelected = state.selected == AccentChoice.Default,
            ),
            onClick = { onSelect(AccentChoice.Default) },
        )

        Swatch(
            style = SwatchStyle(
                fill = colors.surfaceContainer,
                isSelected = state.selected == AccentChoice.Expressive,
                glyph = Icons.Filled.Palette,
            ),
            onClick = { onSelect(AccentChoice.Expressive) },
        )

        (presetChoices + customChoices).forEach { choice ->
            Swatch(
                style = SwatchStyle(
                    fill = Color(choice.argb),
                    isSelected = state.selected == choice,
                ),
                onClick = { onSelect(choice) },
            )
        }

        Swatch(
            style = SwatchStyle(
                fill = colors.primary,
                isSelected = false,
                glyph = Icons.Filled.Add,
            ),
            onClick = onAddColor,
        )
    }
}

// A swatch carries an arbitrary color, so its glyph takes whichever of black or
// white reads against that fill rather than a fixed theme role.
private fun contrastAgainst(fill: Color): Color =
    if (fill.luminance() > ContrastThreshold) Color.Black else Color.White

@Immutable
private data class SwatchStyle(
    val fill: Color,
    val isSelected: Boolean,
    val glyph: ImageVector? = null,
)

@Composable
private fun Swatch(style: SwatchStyle, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .themedSurface(fill = style.fill, elevation = SurfaceElevation.FLAT)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SwatchMark(style)
    }
}

@Composable
private fun SwatchMark(style: SwatchStyle) {
    val scaleSpec = emphasizedSpring<Float>()
    val fadeSpec = standardTween<Float>()

    AnimatedVisibility(
        visible = !style.isSelected && style.glyph != null,
        enter = fadeIn(fadeSpec),
        exit = fadeOut(fadeSpec),
    ) {
        style.glyph?.let { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = contrastAgainst(style.fill),
                modifier = Modifier.size(SwatchIconSize),
            )
        }
    }

    AnimatedVisibility(
        visible = style.isSelected,
        enter = fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = MarkEnterScale),
        exit = fadeOut(fadeSpec) + scaleOut(scaleSpec, targetScale = MarkEnterScale),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = contrastAgainst(style.fill),
            modifier = Modifier.size(SwatchIconSize),
        )
    }
}
