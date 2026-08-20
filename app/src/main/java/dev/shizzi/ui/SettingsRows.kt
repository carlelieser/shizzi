package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
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
import dev.shizzi.ui.theme.Spacing

/** Trailing glyph size, smaller than a header icon since it only marks a row. */
private val RowIconSize = 20.dp

/** Vertical padding shared by every settings row. */
private val RowPadding = Spacing.md

/**
 * Roughly a single-line label's, so the toggle row matches the text rows.
 * requiredHeight overrides the incoming constraint rather than negotiating
 * within it, which is what lets the control draw larger than it occupies.
 */
private val SwitchLayoutHeight = 24.dp

/**
 * `subheading` rather than `label` so the name outranks its own description —
 * the two were 13sp over 14sp, which inverted the hierarchy.
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
 * The break is carried by the gap and the change of register rather than a
 * rule, which would put a second horizontal line under the header's.
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

/**
 * The switch is 48dp tall against a title's ~22dp, so a row centred on it takes
 * its height from the control and puts ~13dp of air around the label that the
 * text-only rows do not have. The row is padded to match [SettingsAction] and
 * the switch overhangs it instead, keeping the section on one rhythm.
 *
 * Invisible while the rows carried subtitles, where a two-line label came close
 * enough to the switch's height to hide the difference.
 */
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

        // Laid out at the label's height, still drawing and taking touch at its
        // natural 48dp.
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.requiredHeight(SwitchLayoutHeight),
            colors = SwitchDefaults.colors(
                checkedThumbColor = ShizziTheme.colors.onPrimary,
                checkedTrackColor = ShizziTheme.colors.primary,
                checkedBorderColor = ShizziTheme.colors.border,
            ),
        )
    }
}

/**
 * The trailing glyph says where the tap goes — onward within the app, or out of
 * the corner and outside it — which is worth knowing before the browser opens.
 *
 * The outward arrow is not auto-mirrored: it means "leaves the app" rather than
 * a direction of travel, and flipping it in an RTL locale would lose that.
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
private fun TrailingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.size(RowIconSize),
    )
}

/** Grouped so a row stays inside the parameter limit with a control and a callback. */
data class SettingsText(val title: String, val subtitle: String = "")
