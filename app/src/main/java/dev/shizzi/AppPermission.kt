package dev.shizzi

import android.Manifest
import android.os.Build

enum class AppPermission {
    NOTIFICATIONS,
    BATTERY_EXEMPTION,
}

data class PermissionStatus(
    val permission: AppPermission,
    val isGranted: Boolean,
)

val AppPermission.title: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> "Notifications"
        AppPermission.BATTERY_EXEMPTION -> "Background activity"
    }

val AppPermission.rationale: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS ->
            "Shows session status and lets you stop from the shade"
        AppPermission.BATTERY_EXEMPTION ->
            "Exempts Shizzi from battery optimisation so it can act while closed"
    }

val AppPermission.manifestName: String?
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
        AppPermission.BATTERY_EXEMPTION -> null
    }

val AppPermission.isApplicable: Boolean
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        AppPermission.BATTERY_EXEMPTION -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
