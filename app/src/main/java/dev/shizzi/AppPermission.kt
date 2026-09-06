package dev.shizzi

import android.Manifest
import android.os.Build

enum class AppPermission {
    NOTIFICATIONS,
}

data class PermissionStatus(
    val permission: AppPermission,
    val isGranted: Boolean,
)

val AppPermission.title: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> "Notifications"
    }

val AppPermission.rationale: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> "Required to keep your session running"
    }

val AppPermission.manifestName: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
    }

val AppPermission.isApplicable: Boolean
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
