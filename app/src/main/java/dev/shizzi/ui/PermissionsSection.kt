package dev.shizzi.ui

import androidx.compose.runtime.Composable
import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.rationale
import dev.shizzi.title

@Composable
fun PermissionsSection(
    statuses: List<PermissionStatus>,
    onGrant: (AppPermission) -> Unit,
) {
    statuses.forEach { status ->
        PermissionRow(status = status, onGrant = onGrant)
    }
}

@Composable
private fun PermissionRow(status: PermissionStatus, onGrant: (AppPermission) -> Unit) {
    val label = SettingsText(
        title = status.permission.title,
        subtitle = subtitleFor(status),
    )

    if (status.isGranted) {
        SettingsStatusRow(label = label)
        return
    }

    SettingsAction(label = label, onClick = { onGrant(status.permission) })
}

private fun subtitleFor(status: PermissionStatus): String = when {
    status.isGranted -> "Granted"
    else -> status.permission.rationale
}
