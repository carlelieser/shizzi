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
import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.rationale
import dev.shizzi.title
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

private val MarkSize = 20.dp

@Composable
fun PermissionsStep(
    statuses: List<PermissionStatus>,
    onGrant: (AppPermission) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        if (statuses.isEmpty()) {
            Text(
                text = "Nothing to grant on this version of Android.",
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
            return@Column
        }

        statuses.forEach { status ->
            PermissionCard(status = status, onGrant = onGrant)
        }
    }
}

@Composable
private fun PermissionCard(status: PermissionStatus, onGrant: (AppPermission) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .clickable(enabled = !status.isGranted) { onGrant(status.permission) }
            .padding(ShizziTheme.spacing.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
        ) {
            Text(
                text = status.permission.title,
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )

            Text(
                text = status.permission.rationale,
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        PermissionMark(isGranted = status.isGranted)
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
