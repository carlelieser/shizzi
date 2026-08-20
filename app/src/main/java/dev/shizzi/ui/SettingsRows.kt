package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme

/** Trailing glyph size, smaller than a header icon since it only marks a row. */
private val RowIconSize = 20.dp

/**
 * The label pair every settings row carries: name, then what it does.
 *
 * The name sits on `subheading` rather than `label` so it outranks its own
 * description — the two were 13sp over 14sp, which inverted the hierarchy.
 */
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

/**
 * Names a group of rows.
 *
 * Uppercase mono, muted, with space above it: the section break is carried by
 * the gap and the change of register rather than by a rule, which would add a
 * second horizontal line to a screen that already has one under the header.
 */
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

/** A setting that is on or off. */
@Composable
fun SettingsToggle(
    label: SettingsText,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ShizziTheme.spacing.md),
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = ShizziTheme.colors.onPrimary,
                checkedTrackColor = ShizziTheme.colors.primary,
                checkedBorderColor = ShizziTheme.colors.border,
            ),
        )
    }
}

/**
 * A row that does something when tapped.
 *
 * The trailing glyph says where the tap goes: an arrow onward for work done in
 * the app, an arrow leaving the corner for anything that opens outside it. That
 * distinction is worth drawing before the tap rather than after the browser
 * opens.
 *
 * The outward arrow is not auto-mirrored. It points away rather than forward,
 * so it reads as "leaves the app" rather than as a direction of travel, and
 * flipping it in a right-to-left locale would lose that.
 */
@Composable
fun SettingsAction(
    label: SettingsText,
    isExternal: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ShizziTheme.spacing.md),
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
private fun TrailingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.size(RowIconSize),
    )
}

/**
 * A row's two strings, grouped so a row composable stays inside the three
 * parameter limit once it also takes a control and a callback.
 */
data class SettingsText(val title: String, val subtitle: String = "")
