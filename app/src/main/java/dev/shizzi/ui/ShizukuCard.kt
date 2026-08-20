package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.shizzi.ShizukuGate
import dev.shizzi.ShizukuState
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/**
 * A card rather than a row: this is what a user opens Settings to check when a
 * session will not start, and the version matters in particular — 13.5.4 on
 * Android 16 crashes within minutes.
 */
@Composable
fun ShizukuCard(state: ShizukuState, onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        StatusRow(name = "Status", value = statusText(state))
        StatusRow(name = "Service", value = serviceText(state))
        StatusRow(name = "Version", value = ShizukuGate.installedVersion() ?: "Not installed")

        if (state is ShizukuState.PermissionRequired) {
            GrantButton(onGrant)
        }
    }
}

/**
 * The value takes the remaining width so a long version string wraps in its own
 * column instead of pushing the name off the row.
 */
@Composable
private fun StatusRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = name,
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurfaceMuted,
        )

        Text(
            text = value,
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = ShizziTheme.spacing.md),
        )
    }
}

/**
 * Offered only when permission is what stands in the way. R1.3 forbids
 * requesting on launch, so this is the explicit action that triggers it.
 */
@Composable
private fun GrantButton(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ShizziTheme.spacing.sm)
            .brutalSurface(fill = ShizziTheme.colors.primary)
            .clickable(onClick = onGrant)
            .padding(vertical = ShizziTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "GRANT PERMISSION",
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onPrimary,
        )
    }
}

private fun statusText(state: ShizukuState): String = when (state) {
    is ShizukuState.Ready -> "Ready"
    ShizukuState.NotInstalled -> "Not installed"
    ShizukuState.NotRunning -> "Not running"
    ShizukuState.PermissionRequired -> "Permission required"
}

/**
 * What the privileged service runs as, known only once it is Ready. A dash
 * rather than a hidden row, which would change the card's height and read as
 * information it is not.
 */
private fun serviceText(state: ShizukuState): String = when (state) {
    is ShizukuState.Ready -> ShizukuGate.shortUid(state.uid)
    else -> "—"
}
