package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.shizzi.CompatibilityState
import dev.shizzi.DownloadProgress
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/**
 * Offers the tethering module to a device that only needs that.
 *
 * The copy names what the device gains rather than what the file is: "tethering
 * APEX" is the accurate term and tells a user nothing about why they should
 * accept a download.
 *
 * Offline is a state here, not an error. With no validated network the card says
 * so and the action is disabled, rather than presenting a button whose only
 * outcome is a failure the user could have been warned about.
 */
@Composable
fun TetheringProviderDownloadCard(state: CompatibilityState, hasNetwork: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        Text(
            text = "Tethering module",
            style = ShizziTheme.typography.subheading,
            color = ShizziTheme.colors.onSurface,
        )

        Text(
            text = bodyFor(state, hasNetwork),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )

        when (state) {
            is CompatibilityState.Downloading -> DownloadBar(state.progress)
            is CompatibilityState.DownloadFailed -> FailureDetail(state.failure.reason)
            else -> Unit
        }
    }
}

/**
 * Determinate wherever the total is known, which is always after the first
 * chunk — the expected size is a constant, not a header the server may omit.
 */
@Composable
private fun DownloadBar(progress: DownloadProgress) {
    val fraction = when {
        progress.totalBytes > 0 -> progress.bytesRead.toFloat() / progress.totalBytes
        else -> 0f
    }

    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = ShizziTheme.colors.primary,
        trackColor = ShizziTheme.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ShizziTheme.spacing.md),
    )
}

/** The verbatim reason, in the log face — quoted from the platform, not copy. */
@Composable
private fun FailureDetail(reason: String) {
    Text(
        text = breakableIdentifiers(reason),
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.xs),
    )
}

/**
 * A connectivity failure gets its own headline because it is the only one the
 * user can act on. A 404 or a digest mismatch keeps a headline of its own —
 * telling someone to check their connection would send them after the wrong
 * thing entirely.
 */
private fun bodyFor(state: CompatibilityState, hasNetwork: Boolean): String = when {
    state is CompatibilityState.Downloading -> "Downloading the module…"

    state is CompatibilityState.DownloadFailed && state.failure.isConnectivity ->
        "Couldn't reach the network to download the module. Reconnect and try again."

    state is CompatibilityState.DownloadFailed ->
        "The download couldn't be verified, so nothing was installed."

    !hasNetwork ->
        "Your phone needs the newer tethering module to route the hotspot. " +
            "Connect to a network to download it."

    else ->
        "Your phone can run this app once it has a newer tethering module. " +
            "It's about 3 MB, and installing it needs one restart."
}
