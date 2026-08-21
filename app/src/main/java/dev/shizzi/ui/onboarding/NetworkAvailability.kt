package dev.shizzi.ui.onboarding

import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.connectivityManager

@Composable
fun hasValidatedNetwork(): Boolean {
    val context = LocalContext.current
    val manager = context.connectivityManager()

    val capabilities = runCatching {
        manager.getNetworkCapabilities(manager.activeNetwork)
    }.getOrNull() ?: return false

    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
