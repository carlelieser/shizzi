package dev.shizzi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.PrivilegeBackendType
import dev.shizzi.PrivilegeState
import dev.shizzi.ui.theme.ShizziTheme

data class PermissionsSectionState(
    val backend: PrivilegeBackendType,
    val privilegeState: PrivilegeState,
    val permissions: List<PermissionStatus>,
)

@Composable
fun PermissionsSection(
    state: PermissionsSectionState,
    onGrantPermission: (AppPermission) -> Unit,
    privilegeActions: PrivilegeAccessActions,
) {
    Box(modifier = Modifier.padding(vertical = ShizziTheme.spacing.md)) {
        PrivilegeAccessCard(state.backend, state.privilegeState, privilegeActions)
    }
    permissionRows(state.permissions, onGrantPermission).forEach { row -> PermissionSettingsRow(row) }
}

@Composable
private fun PermissionSettingsRow(row: PermissionRowState) {
    val label = SettingsText(title = row.title, subtitle = row.rationale)
    if (row.isGranted) SettingsStatusRow(label = label)
    else SettingsAction(label = label, onClick = row.onAct)
}
