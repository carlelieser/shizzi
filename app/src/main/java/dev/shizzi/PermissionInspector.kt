package dev.shizzi

import android.content.Context
import android.content.pm.PackageManager

class PermissionInspector(private val context: Context) {

    fun statusOf(permission: AppPermission): PermissionStatus = PermissionStatus(
        permission = permission,
        isGranted = isGranted(permission),
    )

    fun observe(): List<PermissionStatus> = AppPermission.entries
        .filter { it.isApplicable }
        .map(::statusOf)

    fun isGranted(permission: AppPermission): Boolean {
        if (!permission.isApplicable) return true

        return context.checkSelfPermission(permission.manifestName) ==
            PackageManager.PERMISSION_GRANTED
    }
}
