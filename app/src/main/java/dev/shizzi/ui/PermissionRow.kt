package dev.shizzi.ui

import androidx.annotation.StringRes
import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.R
import dev.shizzi.ShizukuState
import dev.shizzi.rationaleRes
import dev.shizzi.titleRes

data class PermissionRowState(
    @StringRes val title: Int,
    @StringRes val rationale: Int,
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
            title = status.permission.titleRes,
            rationale = status.permission.rationaleRes,
            isGranted = status.isGranted,
            onAct = { onGrantPermission(status.permission) },
        )
    }
}

private fun shizukuRow(state: ShizukuState, onAct: () -> Unit) = PermissionRowState(
    title = R.string.shizuku,
    rationale = R.string.shizuku_rationale,
    isGranted = state is ShizukuState.Ready,
    onAct = onAct,
)
