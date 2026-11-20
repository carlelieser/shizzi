package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.shizzi.CompatibilityState
import dev.shizzi.R
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

@Composable
@ReadOnlyComposable
private fun titleFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Staged -> stringResource(R.string.module_restart_to_finish)
    is CompatibilityState.InstallFailed -> stringResource(R.string.module_not_accepted)
    else -> stringResource(R.string.tethering_module)
}

@Composable
@ReadOnlyComposable
private fun bodyFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Installing -> stringResource(R.string.module_installing)

    is CompatibilityState.Staged ->
        stringResource(R.string.module_staged_description)

    is CompatibilityState.InstallFailed ->
        stringResource(R.string.module_install_failed_description)

    else -> stringResource(R.string.module_ready_to_install)
}
