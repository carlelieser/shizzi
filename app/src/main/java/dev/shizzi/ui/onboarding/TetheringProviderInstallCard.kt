package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.CompatibilityState
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

@Composable
fun TetheringProviderInstallCard(state: CompatibilityState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
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
                text = titleFor(state),
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )

            Text(
                text = bodyFor(state),
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )

            if (state is CompatibilityState.InstallFailed) {
                InstallFailureDetail(state.reason)
            }
        }
    }
}

@Composable
private fun InstallFailureDetail(reason: String) {
    Text(
        text = breakableIdentifiers(reason),
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.xs),
    )
}

private fun titleFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Staged -> "Restart your phone to finish"
    is CompatibilityState.InstallFailed -> "The module wasn't accepted"
    else -> "Tethering module"
}

private fun bodyFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Installing -> "Installing the module…"

    is CompatibilityState.Staged ->
        "The module is ready and will finish installing the next time your " +
            "phone starts up. Come back here afterwards to check compatibility."

    is CompatibilityState.InstallFailed ->
        "Your phone refused this module, which some manufacturers' builds do. " +
            "Nothing was changed."

    else -> "The module is downloaded and verified, ready to install."
}
