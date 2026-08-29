package dev.shizzi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.shizzi.SessionLog

fun Context.requestIgnoreBatteryOptimizations() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        startActivity(intent)
    } catch (absent: ActivityNotFoundException) {
        SessionLog.warn("no battery optimization screen on this device: ${absent.message}")
        openBatterySettings()
    }
}

private fun Context.openBatterySettings() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        startActivity(intent)
    } catch (absent: ActivityNotFoundException) {
        SessionLog.warn("no battery settings screen: ${absent.message}")
    }
}
