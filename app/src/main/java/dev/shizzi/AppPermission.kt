package dev.shizzi

import android.Manifest
import android.os.Build
import androidx.annotation.StringRes

enum class AppPermission {
    NOTIFICATIONS,
    BATTERY_EXEMPTION,
}

data class PermissionStatus(
    val permission: AppPermission,
    val isGranted: Boolean,
)

@get:StringRes
val AppPermission.titleRes: Int
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> R.string.permission_notifications
        AppPermission.BATTERY_EXEMPTION -> R.string.permission_background_activity
    }

@get:StringRes
val AppPermission.rationaleRes: Int
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> R.string.permission_notifications_rationale
        AppPermission.BATTERY_EXEMPTION -> R.string.permission_background_activity_rationale
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
