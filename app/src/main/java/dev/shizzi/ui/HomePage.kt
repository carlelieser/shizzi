package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.SessionUiState
import dev.shizzi.UiStatus
import dev.shizzi.ui.theme.HeaderHeight
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme

private fun buttonLabel(status: UiStatus): String =
    if (status == UiStatus.CONNECTED) "Stop" else "Start"

private fun buttonState(state: SessionUiState): ConnectButtonState = when {
    state.status == UiStatus.LOADING -> ConnectButtonState.LOADING
    state.status == UiStatus.CONNECTED -> ConnectButtonState.STOP
    state.canStart -> ConnectButtonState.START
    else -> ConnectButtonState.DISABLED
}

@Composable
fun HomePage(
    state: SessionUiState,
    actions: HomeActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        HomeHeader(
            state = state,
            onOpenSettings = actions.onOpenSettings,
        )

        HomeBody(state = state, actions = actions)

        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Box(
                modifier = Modifier.height(ShizziTheme.spacing.xxxl),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (isShowingVpn(state)) VpnChip()
            }

            StatusRow(state = state)
        }
    }
}

data class HomeActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
private fun HomeHeader(
    state: SessionUiState,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = ShizziTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(start = ShizziTheme.spacing.sm)) {
            ShizukuBadge(state.shizukuState)
        }

        Spacer(Modifier.weight(1f))

        ShizziIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
            tint = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}

private fun isShowingVpn(state: SessionUiState): Boolean =
    state.isVpnBound && state.status == UiStatus.CONNECTED

@Composable
private fun HomeBody(state: SessionUiState, actions: HomeActions) {
    val isStarting = state.status == UiStatus.LOADING

    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusIcon(status = state.status)

        Spacer(Modifier.height(ShizziTheme.spacing.xxxl * 2))

        ConnectButton(
            label = buttonLabel(state.status),
            state = buttonState(state),
            onClick = actions.onToggle,
        )

        Box(modifier = Modifier.height(ShizziTheme.spacing.xxxl)) {
            if (isStarting) CancelButton(onClick = actions.onCancel)
        }
    }
}
