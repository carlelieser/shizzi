package dev.shizzi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.PrivilegeBackendType
import dev.shizzi.PrivilegeState
import dev.shizzi.R
import dev.shizzi.str
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.emphasizedSpring
import dev.shizzi.ui.theme.standardTween
import dev.shizzi.ui.theme.themedSurface

data class PrivilegeAccessActions(
    val onSelectBackend: (PrivilegeBackendType) -> Unit,
    val onShizukuAction: () -> Unit,
    val onStartLocalAdbPairing: () -> Unit,
    val onCancelLocalAdbPairing: () -> Unit,
)

@Composable
fun PrivilegeAccessCard(
    backend: PrivilegeBackendType,
    state: PrivilegeState,
    actions: PrivilegeAccessActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg)
            .animateContentSize(animationSpec = emphasizedSpring()),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Text(str(R.string.privilege_access), style = ShizziTheme.typography.subheading)
        Text(
            str(R.string.privilege_provider_description),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm)) {
            ProviderButton(
                label = str(R.string.shizuku),
                selected = backend == PrivilegeBackendType.SHIZUKU,
                onClick = { actions.onSelectBackend(PrivilegeBackendType.SHIZUKU) },
                modifier = Modifier.weight(1f),
            )
            ProviderButton(
                label = str(R.string.wireless_debugging),
                selected = backend == PrivilegeBackendType.LOCAL_ADB,
                onClick = { actions.onSelectBackend(PrivilegeBackendType.LOCAL_ADB) },
                modifier = Modifier.weight(1f),
            )
        }

        val contentTween = standardTween<Float>()
        AnimatedContent(
            targetState = backend,
            transitionSpec = { fadeIn(contentTween) togetherWith fadeOut(contentTween) },
            label = "privilegeBackendDetails",
        ) { selected ->
            when (selected) {
                PrivilegeBackendType.SHIZUKU -> ShizukuDetails(state, actions)
                PrivilegeBackendType.LOCAL_ADB -> LocalAdbDetails(state, actions)
            }
        }
    }
}

@Composable
private fun ShizukuDetails(state: PrivilegeState, actions: PrivilegeAccessActions) {
    Column(verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm)) {
        Text(
            str(R.string.shizuku_backend_description),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
        GuideStep(1, str(R.string.shizuku_guide_1))
        GuideStep(2, str(R.string.shizuku_guide_2))
        Text(
            privilegeStatus(state),
            style = ShizziTheme.typography.label,
            color = if (state is PrivilegeState.Ready) ShizziTheme.colors.primary else ShizziTheme.colors.onSurfaceMuted,
        )
        if (state !is PrivilegeState.Ready) {
            Button(onClick = actions.onShizukuAction) {
                Text(str(R.string.grant_permission))
            }
        }
    }
}

@Composable
private fun LocalAdbDetails(state: PrivilegeState, actions: PrivilegeAccessActions) {
    Column(verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm)) {
        Text(
            str(R.string.local_adb_backend_description),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
        GuideStep(1, str(R.string.local_adb_guide_1))
        GuideStep(2, str(R.string.local_adb_guide_2))
        GuideStep(3, str(R.string.local_adb_guide_3))
        GuideStep(4, str(R.string.local_adb_guide_4))

        Text(
            privilegeStatus(state),
            style = ShizziTheme.typography.label,
            color = if (state is PrivilegeState.Ready) ShizziTheme.colors.primary else ShizziTheme.colors.onSurfaceMuted,
        )

        if (state is PrivilegeState.Connecting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = actions.onCancelLocalAdbPairing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(str(R.string.cancel_pairing))
            }
        } else if (state !is PrivilegeState.Ready) {
            Button(
                onClick = actions.onStartLocalAdbPairing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(str(R.string.open_wireless_debugging))
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Text(
        text = number.toString() + ". " + text,
        style = ShizziTheme.typography.body,
        color = ShizziTheme.colors.onSurfaceMuted,
    )
}

@Composable
private fun ProviderButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val radius by animateDpAsState(
        targetValue = if (selected) 14.dp else 28.dp,
        animationSpec = emphasizedSpring(),
        label = "providerButtonShape",
    )
    val shape = RoundedCornerShape(radius)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, shape = shape) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = shape) { Text(label) }
    }
}

@Composable
private fun privilegeStatus(state: PrivilegeState): String = when (state) {
    is PrivilegeState.Ready -> str(R.string.provider_ready)
    is PrivilegeState.SetupRequired -> str(R.string.provider_setup_required)
    is PrivilegeState.Connecting -> if (state.detail.isBlank()) {
        str(R.string.local_adb_connecting)
    } else {
        state.detail
    }
    is PrivilegeState.Unsupported -> str(R.string.local_adb_unsupported)
    is PrivilegeState.Error -> str(R.string.local_adb_error, state.message)
}
