package dev.shizzi.ui

import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.ShizukuState
import dev.shizzi.rationaleRes
import dev.shizzi.titleRes

data class PermissionRowState(
    val title: String,
    val rationale: String,
    val isGranted: Boolean,
    val onAct: () -> Unit,
)

data class PermissionRowSources(
    val shizuku: ShizukuState,
    val permissions: List<PermissionStatus>,
)

fun permissionRows(
    sources: PermissionRowSources,
    onGrantPermission: (AppPermission) -> Unit,
    onShizukuAction: () -> Unit,
): List<PermissionRowState> {
    val shizuku = shizukuRow(state = sources.shizuku, onAct = onShizukuAction)

    return listOf(shizuku) + sources.permissions.map { status ->
        PermissionRowState(
            title = dev.shizzi.L10n.string(status.permission.titleRes),
            rationale = dev.shizzi.L10n.string(status.permission.rationaleRes),
            isGranted = status.isGranted,
            onAct = { onGrantPermission(status.permission) },
        )
    }
}

private fun shizukuRow(state: ShizukuState, onAct: () -> Unit) = PermissionRowState(
    title = dev.shizzi.L10n.string(dev.shizzi.R.string.shizuku),
    rationale = dev.shizzi.L10n.string(dev.shizzi.R.string.shizuku_rationale),
    isGranted = state is ShizukuState.Ready,
    onAct = onAct,
)
