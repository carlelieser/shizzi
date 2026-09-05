package dev.shizzi.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.ShizukuState
import dev.shizzi.ui.PermissionRowState
import dev.shizzi.ui.ShizukuCard
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

private val MarkSize = 20.dp

@Composable
fun PermissionsStep(
    shizuku: ShizukuState,
    rows: List<PermissionRowState>,
    onShizukuAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        ShizukuCard(state = shizuku, onGrant = onShizukuAction)

        rows.forEach { row -> PermissionCard(row) }
    }
}

@Composable
private fun PermissionCard(row: PermissionRowState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .clickable(enabled = !row.isGranted, onClick = row.onAct)
            .padding(ShizziTheme.spacing.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
        ) {
            Text(
                text = row.title,
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )

            Text(
                text = row.rationale,
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        PermissionMark(isGranted = row.isGranted)
    }
}

@Composable
private fun PermissionMark(isGranted: Boolean) {
    Icon(
        imageVector = when {
            isGranted -> Icons.Filled.Check
            else -> Icons.AutoMirrored.Filled.ArrowForward
        },
        contentDescription = null,
        tint = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.size(MarkSize),
    )
}
