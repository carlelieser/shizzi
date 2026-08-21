package dev.shizzi.ui.onboarding

import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.connectivityManager

/**
 * Whether a network that can actually carry the download exists.
 *
 * VALIDATED rather than merely connected: a captive portal reports a connection
 * and answers the GET with a login page, which arrives as a digest mismatch —
 * a failure that says nothing about what the user needs to do.
 *
 * Read at composition rather than observed. The download card only needs to know
 * whether to offer the button, and a mid-download loss is already reported as a
 * connectivity failure by the downloader itself.
 */
@Composable
fun hasValidatedNetwork(): Boolean {
    val context = LocalContext.current
    val manager = context.connectivityManager()

    val capabilities = runCatching {
        manager.getNetworkCapabilities(manager.activeNetwork)
    }.getOrNull() ?: return false

    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
