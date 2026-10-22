package dev.shizzi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.fastSpring
import dev.shizzi.ui.theme.themedIndication
import dev.shizzi.ui.theme.Spacing

private val RowIconSize = 20.dp

private val RowPadding = Spacing.md

private val SwitchLayoutHeight = 24.dp

@Composable
fun SettingsLabel(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        Text(
            text = title,
            style = ShizziTheme.typography.subheading,
            color = ShizziTheme.colors.onSurface,
        )

        if (subtitle.isEmpty()) return@Column

        Text(
            text = subtitle,
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(
            top = ShizziTheme.spacing.xl,
            bottom = ShizziTheme.spacing.sm,
        ),
    )
}

@Composable
fun SettingsToggle(
    label: SettingsText,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(
            title = label.title,
            subtitle = label.subtitle,
            modifier = Modifier.weight(1f),
        )

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.requiredHeight(SwitchLayoutHeight),
            colors = switchColors(),
        )
    }
}

@Composable
fun SettingsAction(
    label: SettingsText,
    isExternal: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = fastSpring(),
        label = "settingsActionScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = themedIndication(),
                onClick = onClick,
            )
            .padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(
            title = label.title,
            subtitle = label.subtitle,
            modifier = Modifier.weight(1f),
        )

        TrailingIcon(
            icon = if (isExternal) {
                Icons.Filled.ArrowOutward
            } else {
                Icons.AutoMirrored.Filled.ArrowForward
            },
        )
    }
}

@Composable
fun SettingsChoice(label: SettingsText, value: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = fastSpring(),
        label = "settingsChoiceScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = themedIndication(),
                onClick = onClick,
            )
            .padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(
            title = label.title,
            subtitle = label.subtitle,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(end = ShizziTheme.spacing.sm),
        )

        TrailingIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
    }
}

@Composable
fun SettingsStatusRow(label: SettingsText) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(
            title = label.title,
            subtitle = label.subtitle,
            modifier = Modifier.weight(1f),
        )

        TrailingIcon(icon = Icons.Filled.Check)
    }
}

@Composable
private fun TrailingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.size(RowIconSize),
    )
}

@Composable
private fun switchColors() = when (ShizziTheme.design) {
    DesignLanguage.MATERIAL_EXPRESSIVE -> SwitchDefaults.colors()
    DesignLanguage.NEOBRUTALISM -> SwitchDefaults.colors(
        checkedThumbColor = ShizziTheme.colors.onPrimary,
        checkedTrackColor = ShizziTheme.colors.primary,
        checkedBorderColor = ShizziTheme.colors.border,
    )
}

data class SettingsText(val title: String, val subtitle: String = "")
