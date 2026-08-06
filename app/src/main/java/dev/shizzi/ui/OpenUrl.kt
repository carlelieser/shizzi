package dev.shizzi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.shizzi.SessionLog

/**
 * Opens a link in whatever handles it, without taking the app down if nothing
 * does.
 *
 * A device with no browser is unusual but not impossible, and an unhandled
 * ActivityNotFoundException on a tap in Settings would crash the app over a
 * link to a homepage. The failure is logged instead, where the log screen can
 * show it.
 */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        startActivity(intent)
    } catch (absent: ActivityNotFoundException) {
        SessionLog.warn("no app to open $url: ${absent.message}")
    }
}
