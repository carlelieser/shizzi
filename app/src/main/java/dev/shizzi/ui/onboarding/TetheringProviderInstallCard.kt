package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.shizzi.CompatibilityState
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/**
 * The verified module, ready to install — and afterwards, the reboot it needs.
 *
 * The restart is a prompt, never an action. An app that reboots someone's phone
 * takes a decision that is theirs, and a staged APEX applies on the next boot
 * whenever that happens to be.
 */
@Composable
fun TetheringProviderInstallCard(state: CompatibilityState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg),
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

/**
 * pm's own output, verbatim.
 *
 * This is the whole account of a rejection — on a handset carrying an
 * OEM-signed module, apexd's signature complaint arrives through here and is
 * the only thing that explains why a correct file was refused.
 */
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
