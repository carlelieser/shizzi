package dev.shizzi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings

class PermissionRequest(private val context: Context) {

    fun settingsIntentFor(permission: AppPermission): Intent = when (permission) {
        AppPermission.NOTIFICATIONS -> appNotificationSettings()
    }

    fun open(permission: AppPermission) {
        val intent = settingsIntentFor(permission)

        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (absent: ActivityNotFoundException) {
            SessionLog.warn("no screen for ${permission.name}: ${absent.message}")
        }
    }

    private fun appNotificationSettings() =
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
}
