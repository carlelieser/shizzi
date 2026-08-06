package dev.shizzi.spike.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.spike.ui.theme.ScreenPadding
import dev.shizzi.spike.ui.theme.ShizziTheme

/**
 * The settings screen, still carrying the spike's two rows.
 *
 * Rebuilt against the design tokens in a later commit; moved here now so it
 * stops growing the home screen's file.
 */
@Composable
fun SettingsPage(
    isDebugLogging: Boolean,
    onSetDebugLogging: (Boolean) -> Unit,
    onRunProbes: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
            Spacer(Modifier.height(ShizziTheme.spacing.lg))

            SettingsToggle(
                title = "Debug logging",
                subtitle = "Records extra detail while a session runs",
                isChecked = isDebugLogging,
                onCheckedChange = onSetDebugLogging,
            )

            SettingsAction(
                title = "Run diagnostics",
                subtitle = "Runs the full probe sequence and writes a report",
                onClick = onRunProbes,
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ShizziTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLabel(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    SettingsLabel(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ShizziTheme.spacing.md),
    )
}

/** The label pair every settings row carries: name, then what it does. */
@Composable
private fun SettingsLabel(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurface,
        )
        Text(
            text = subtitle,
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}
