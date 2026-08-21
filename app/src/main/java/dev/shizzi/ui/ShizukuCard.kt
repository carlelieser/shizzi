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

private fun serviceText(state: ShizukuState): String = when (state) {
    is ShizukuState.Ready -> ShizukuGate.shortUid(state.uid)
    else -> "—"
}
