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

private fun shizukuRow(state: ShizukuState, onAct: () -> Unit) = PermissionRowState(
    title = "Shizuku",
    rationale = shizukuRationale(state),
    isGranted = state is ShizukuState.Ready,
    onAct = onAct,
)

private fun shizukuRationale(state: ShizukuState): String = when (state) {
    ShizukuState.NotInstalled -> "Install Shizuku to give Shizzi the access it runs on"
    ShizukuState.NotRunning -> "Start Shizuku so Shizzi can connect to it"
    ShizukuState.PermissionRequired -> "Allow Shizzi to use Shizuku"
    is ShizukuState.Ready -> "Grants Shizzi the system access tethering needs"
}
