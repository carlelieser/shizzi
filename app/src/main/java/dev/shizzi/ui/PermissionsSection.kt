package dev.shizzi.ui

import androidx.compose.runtime.Composable
import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.ShizukuState

data class PermissionsSectionState(
    val shizuku: ShizukuState,
    val permissions: List<PermissionStatus>,
)

@Composable
fun PermissionsSection(
    state: PermissionsSectionState,
    onGrantPermission: (AppPermission) -> Unit,
    onShizukuAction: () -> Unit,
) {
    ShizukuCard(state = state.shizuku, onGrant = onShizukuAction)

    val rows = permissionRows(
        sources = PermissionRowSources(
            shizuku = state.shizuku,
            permissions = state.permissions,
        ),
        onGrantPermission = onGrantPermission,
        onShizukuAction = onShizukuAction,
    )

    rows.forEach { row -> PermissionSettingsRow(row) }
}

@Composable
private fun PermissionSettingsRow(row: PermissionRowState) {
    val label = SettingsText(
        title = row.title,
        subtitle = if (row.isGranted) "Granted" else row.rationale,
    )

    if (row.isGranted) {
        SettingsStatusRow(label = label)
        return
    }

    SettingsAction(label = label, onClick = row.onAct)
}
