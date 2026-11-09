package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.CompatibilityState
import dev.shizzi.DownloadProgress
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.themedSurface

@Composable
fun TetheringProviderDownloadCard(state: CompatibilityState, hasNetwork: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        ModuleStateIcon(state)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
        ) {
            Text(
                text = dev.shizzi.L10n.string(dev.shizzi.R.string.tethering_module),
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
}

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

@Composable
private fun FailureDetail(reason: String) {
    Text(
        text = breakableIdentifiers(reason),
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.xs),
    )
}

private fun bodyFor(state: CompatibilityState, hasNetwork: Boolean): String = when {
    state is CompatibilityState.Downloading -> dev.shizzi.L10n.string(dev.shizzi.R.string.module_downloading)

    state is CompatibilityState.DownloadFailed && state.failure.isConnectivity ->
        dev.shizzi.L10n.string(dev.shizzi.R.string.module_download_network_error)

    state is CompatibilityState.DownloadFailed ->
        dev.shizzi.L10n.string(dev.shizzi.R.string.module_download_verify_error)

    !hasNetwork ->
        dev.shizzi.L10n.string(dev.shizzi.R.string.module_download_needed)

    else ->
        dev.shizzi.L10n.string(dev.shizzi.R.string.module_download_available)
}
