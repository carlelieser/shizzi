package dev.shizzi.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.shizzi.SessionLog

/**
 * Opens the stock Hotspot & tethering screen, where the hotspot name, password
 * and band live. The direct action is hidden API, so fall back to the activity
 * and then to the general network settings. False when none of them opened.
 */
fun Context.openTetherSettings(): Boolean {
    val candidates = listOf(
        Intent(TETHER_SETTINGS_ACTION),
        Intent().setComponent(ComponentName(SETTINGS_PACKAGE, "$SETTINGS_PACKAGE.TetherSettings")),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )

    val didOpen = candidates.any { intent ->
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (absent: ActivityNotFoundException) {
            false
        } catch (denied: SecurityException) {
            false
        }
    }

    if (!didOpen) SessionLog.warn("no settings screen for hotspot & tethering")
    return didOpen
}

private const val TETHER_SETTINGS_ACTION = "android.settings.TETHER_SETTINGS"
private const val SETTINGS_PACKAGE = "com.android.settings"
