package dev.shizzi.ui.onboarding

import dev.shizzi.R
import dev.shizzi.str

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
import dev.shizzi.ui.theme.themedSurface

@Composable
fun TetheringProviderInstallCard(state: CompatibilityState) {
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
    is CompatibilityState.Staged -> str(R.string.reboot_to_finish_installation)
    is CompatibilityState.InstallFailed -> str(R.string.the_module_was_rejected_by_the_system)
    else -> str(R.string.tethering_module)
}

private fun bodyFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Installing -> str(R.string.installing_module)

    is CompatibilityState.Staged ->
        str(R.string.the_module_is_staged_and_will_finish_installing) +
            str(R.string.return_here_after_rebooting_to_check_compatibility_again)

    is CompatibilityState.InstallFailed ->
        str(R.string.the_system_rejected_this_module_this_can_happen) +
            str(R.string.no_changes_were_made_to_the_device)

    else -> str(R.string.the_module_is_downloaded_verified_and_ready_to)
}
