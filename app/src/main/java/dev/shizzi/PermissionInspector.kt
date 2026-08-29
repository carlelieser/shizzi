package dev.shizzi

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager

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

        return when (permission) {
            AppPermission.BATTERY_EXEMPTION -> isIgnoringBatteryOptimizations()
            else -> isManifestPermissionGranted(permission)
        }
    }

    private fun isManifestPermissionGranted(permission: AppPermission): Boolean {
        val name = permission.manifestName ?: return true

        return context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    }

    private fun isIgnoringBatteryOptimizations(): Boolean = context
        .getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
}
