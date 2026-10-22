package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DefaultAccentColor
import dev.shizzi.ui.theme.PresetAccents
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.emphasizedSpring
import dev.shizzi.ui.theme.fastSpring
import dev.shizzi.ui.theme.standardTween

private val SwatchSize = 48.dp
private val SwatchIconSize = 20.dp
private val RestCorner = 24.dp
private val ActiveCorner = 12.dp
private const val MarkEnterScale = 0.4f
private const val ContrastThreshold = 0.5f

fun accentLabel(accent: AccentChoice): String = when (accent) {
    AccentChoice.Default -> str(R.string.wallpaper_colors)
    AccentChoice.Expressive -> str(R.string.wallpaper_colors)
    is AccentChoice.Custom -> "#%06X".format(accent.argb and 0x00FFFFFF)
}

@Composable
fun AccentPicker(state: AccentPickerState, actions: AccentPickerActions) {
    ThemedBottomSheet(onDismiss = actions.onDismiss) {
        Text(str(R.string.accent_color), style = ShizziTheme.typography.heading, color = ShizziTheme.colors.onSurface)
        Spacer(Modifier.height(ShizziTheme.spacing.lg))
        AccentSwatches(state = state, onSelect = actions.onSelect)
        Spacer(Modifier.height(ShizziTheme.spacing.lg))
    }
}

data class AccentPickerState(val selected: AccentChoice)
data class AccentPickerActions(val onSelect: (AccentChoice) -> Unit, val onDismiss: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(state: AccentPickerState, onSelect: (AccentChoice) -> Unit) {
    val colors = ShizziTheme.colors
    val presetChoices = PresetAccents.map(AccentChoice::Custom)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Swatch(
            style = SwatchStyle(
                fill = colors.surfaceContainer,
                isSelected = state.selected == AccentChoice.Default || state.selected == AccentChoice.Expressive,
                glyph = Icons.Filled.Palette,
            ),
            onClick = { onSelect(AccentChoice.Default) },
        )
        presetChoices.forEach { choice ->
            Swatch(SwatchStyle(Color(choice.argb), state.selected == choice), onClick = { onSelect(choice) })
        }
    }
}

private fun contrastAgainst(fill: Color): Color = if (fill.luminance() > ContrastThreshold) Color.Black else Color.White

@Immutable
private data class SwatchStyle(val fill: Color, val isSelected: Boolean, val glyph: ImageVector? = null)

@Composable
private fun Swatch(style: SwatchStyle, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed || style.isSelected) ActiveCorner else RestCorner,
        animationSpec = fastSpring(),
        label = "accentSwatchCorner",
    )
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .clip(RoundedCornerShape(corner))
            .background(style.fill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { SwatchMark(style) }
}

@Composable
private fun SwatchMark(style: SwatchStyle) {
    val scaleSpec = emphasizedSpring<Float>()
    val fadeSpec = standardTween<Float>()
    AnimatedVisibility(!style.isSelected && style.glyph != null, enter = fadeIn(fadeSpec), exit = fadeOut(fadeSpec)) {
        style.glyph?.let { Icon(it, null, tint = contrastAgainst(style.fill), modifier = Modifier.size(SwatchIconSize)) }
    }
    AnimatedVisibility(
        visible = style.isSelected,
        enter = fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = MarkEnterScale),
        exit = fadeOut(fadeSpec) + scaleOut(scaleSpec, targetScale = MarkEnterScale),
    ) {
        Icon(Icons.Filled.Check, null, tint = contrastAgainst(style.fill), modifier = Modifier.size(SwatchIconSize))
    }
}
