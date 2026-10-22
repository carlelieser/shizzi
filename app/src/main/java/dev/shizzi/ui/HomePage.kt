package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.shizzi.PrivilegeBackendType
import dev.shizzi.PrivilegeState
import dev.shizzi.R
import dev.shizzi.SessionUiState
import dev.shizzi.Traffic
import dev.shizzi.UiStatus
import dev.shizzi.str
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween

private fun buttonLabel(status: UiStatus): String = if (status == UiStatus.CONNECTED) str(R.string.action_stop) else str(R.string.action_start)
private fun buttonState(state: SessionUiState): ConnectButtonState = when {
    state.status == UiStatus.LOADING -> ConnectButtonState.LOADING
    state.status == UiStatus.CONNECTED -> ConnectButtonState.STOP
    state.canStart -> ConnectButtonState.START
    else -> ConnectButtonState.DISABLED
}

@Composable
fun HomePage(state: SessionUiState, actions: HomeActions) {
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        HomeBody(state, actions, Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.height(ShizziTheme.spacing.xxxl), contentAlignment = Alignment.BottomCenter) {
                RiseIn(isShowingVpn(state)) { VpnChip(state.isVpnBypassed) }
            }
            StatusRow(state, actions.onOpenEasterEgg)
        }
        ShizziIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = str(R.string.settings),
            onClick = actions.onOpenSettings,
            tint = ShizziTheme.colors.onSurface,
            containerColor = ShizziTheme.colors.surfaceContainer,
            modifier = Modifier.align(Alignment.TopEnd).zIndex(3f).padding(top = ShizziTheme.spacing.sm, end = ShizziTheme.spacing.sm),
        )
    }
}

data class HomeActions(val onToggle: () -> Unit, val onCancel: () -> Unit, val onOpenSettings: () -> Unit, val onOpenEasterEgg: () -> Unit)
private fun isShowingVpn(state: SessionUiState) = (state.isVpnBound || state.isVpnBypassed) && state.status == UiStatus.CONNECTED

@Composable private fun RiseIn(isVisible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(standardTween()) + slideInVertically(standardSpring()) { it / 2 },
        exit = fadeOut(standardTween()) + slideOutVertically(standardSpring()) { it / 2 },
    ) { content() }
}

@Composable
private fun HomeBody(state: SessionUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.padding(ScreenPadding).padding(top = 56.dp, bottom = ShizziTheme.spacing.xxxl * 2)) {
        val compact = maxHeight < 700.dp
        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 680.dp).align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(if (compact) .35f else .7f))
            StatusIcon(state.status)
            Spacer(Modifier.height(if (compact) ShizziTheme.spacing.md else ShizziTheme.spacing.lg))
            ConnectButton(buttonLabel(state.status), buttonState(state), actions.onToggle)
            Box(Modifier.height(ShizziTheme.spacing.xxl)) { RiseIn(state.status == UiStatus.LOADING) { CancelButton(actions.onCancel) } }
            Spacer(Modifier.height(if (compact) ShizziTheme.spacing.md else ShizziTheme.spacing.lg))
            HomeOverviewCard(state, Modifier.fillMaxWidth().widthIn(max = 640.dp))
            Spacer(Modifier.weight(if (compact) .45f else 1f))
        }
    }
}

@Composable
private fun HomeOverviewCard(state: SessionUiState, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = ShizziTheme.colors.surfaceContainer)) {
        Column(Modifier.padding(ShizziTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md)) {
            Text(str(R.string.session_overview), style = ShizziTheme.typography.subheading, color = ShizziTheme.colors.onSurface)
            OverviewRow(str(R.string.privilege_provider), providerStatus(state.privilegeState))
            OverviewRow(str(R.string.clients), if (state.status == UiStatus.CONNECTED) str(R.string.value_devices_2, state.clientCount) else str(R.string.waiting_for_session))
            OverviewRow(str(R.string.traffic), "↑ ${Traffic.format(state.traffic.up)}   ↓ ${Traffic.format(state.traffic.down)}")
            OverviewRow(str(R.string.network), when {
                state.interfaceName.isNotBlank() -> state.interfaceName
                state.isVpnBypassed -> str(R.string.vpn_bypassed)
                state.isVpnBound -> str(R.string.vpn_bound)
                else -> str(R.string.no_upstream_yet)
            })
        }
    }
}

@Composable private fun OverviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ShizziTheme.typography.body, color = ShizziTheme.colors.onSurfaceMuted)
        Text(value, style = ShizziTheme.typography.body, color = ShizziTheme.colors.onSurface, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun providerStatus(state: PrivilegeState): String = when (state) {
    is PrivilegeState.Ready -> when (state.backend) {
        PrivilegeBackendType.SHIZUKU -> str(R.string.shizuku)
        PrivilegeBackendType.LOCAL_ADB -> str(R.string.wireless_debugging)
    }
    is PrivilegeState.Connecting -> str(R.string.local_adb_connecting)
    is PrivilegeState.Error -> str(R.string.local_adb_error, state.message)
    is PrivilegeState.Unsupported -> str(R.string.local_adb_unsupported)
    is PrivilegeState.SetupRequired -> str(R.string.provider_setup_required)
}
