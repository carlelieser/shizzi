package dev.shizzi.ui

import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.rationale
import dev.shizzi.title

data class PermissionRowState(
    val title: String,
    val rationale: String,
    val isGranted: Boolean,
    val onAct: () -> Unit,
)

fun permissionRows(
    permissions: List<PermissionStatus>,
    onGrantPermission: (AppPermission) -> Unit,
): List<PermissionRowState> = permissions.map { status ->
    PermissionRowState(
        title = status.permission.title,
        rationale = status.permission.rationale,
        isGranted = status.isGranted,
        onAct = { onGrantPermission(status.permission) },
    )
}
