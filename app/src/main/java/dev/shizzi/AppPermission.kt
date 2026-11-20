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

// English fallback text keeps this model usable in plain JVM tests and non-Android tooling.
// Android UI resolves titleRes/rationaleRes so the visible text follows the device locale.
val AppPermission.title: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> "Notifications"
        AppPermission.BATTERY_EXEMPTION -> "Background activity"
    }

val AppPermission.titleRes: Int
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> R.string.permission_notifications
        AppPermission.BATTERY_EXEMPTION -> R.string.permission_background_activity
    }

val AppPermission.rationale: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> "Required to keep your session running"
        AppPermission.BATTERY_EXEMPTION -> "Required for external session management"
    }

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
