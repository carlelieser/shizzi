package dev.shizzi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.shizzi.SessionLog

/** False when no app could open [url]. */
fun Context.openUrl(url: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return try {
        startActivity(intent)
        true
    } catch (absent: ActivityNotFoundException) {
        SessionLog.warn("no app to open $url: ${absent.message}")
        false
    }
}
