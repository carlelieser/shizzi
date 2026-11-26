package dev.shizzi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.AdbPairActivity
import dev.shizzi.Privilege
import androidx.compose.ui.res.stringResource
import dev.shizzi.AppPermission
import dev.shizzi.PermissionStatus
import dev.shizzi.R
import dev.shizzi.ShizukuState
import dev.shizzi.ui.theme.ShizziTheme

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
    Box(modifier = Modifier.padding(vertical = ShizziTheme.spacing.md)) {
        ShizukuCard(state = state.shizuku, onGrant = onShizukuAction)
    }

    val rows = permissionRows(
        sources = PermissionRowSources(
            shizuku = state.shizuku,
            permissions = state.permissions,
        ),
        onGrantPermission = onGrantPermission,
        onShizukuAction = onShizukuAction,
    )

    rows.forEach { row -> PermissionSettingsRow(row) }

    val context = LocalContext.current
    val viaAdb = (state.shizuku as? ShizukuState.Ready)?.via == Privilege.ADB
    SettingsAction(
        label = SettingsText(
            title = stringResource(R.string.adb_entry_title),
            subtitle = stringResource(if (viaAdb) R.string.adb_entry_active else R.string.adb_entry_hint),
        ),
        onClick = { context.startActivity(Intent(context, AdbPairActivity::class.java)) },
    )
}

@Composable
private fun PermissionSettingsRow(row: PermissionRowState) {
    val label = SettingsText(
        title = stringResource(row.title),
        subtitle = stringResource(row.rationale),
    )

    if (row.isGranted) {
        SettingsStatusRow(label = label)
        return
    }

    SettingsAction(label = label, onClick = row.onAct)
}
