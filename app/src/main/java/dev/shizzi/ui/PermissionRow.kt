package dev.shizzi.ui

import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.ShizukuState
import dev.shizzi.rationale
import dev.shizzi.title

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
            title = status.permission.title,
            rationale = status.permission.rationale,
            isGranted = status.isGranted,
            onAct = { onGrantPermission(status.permission) },
        )
    }
}

private const val SHIZUKU_RATIONALE = "Required for core functionality"

private fun shizukuRow(state: ShizukuState, onAct: () -> Unit) = PermissionRowState(
    title = "Shizuku",
    rationale = SHIZUKU_RATIONALE,
    isGranted = state is ShizukuState.Ready,
    onAct = onAct,
)
