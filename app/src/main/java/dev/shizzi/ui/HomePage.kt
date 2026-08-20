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

/** What the button does, given what the session is doing. */
private fun buttonLabel(status: UiStatus): String =
    if (status == UiStatus.CONNECTED) "Stop" else "Start"

/** Derived here so the rule lives beside the state it reads. */
private fun buttonState(state: SessionUiState): ConnectButtonState = when {
    state.status == UiStatus.LOADING -> ConnectButtonState.LOADING
    state.status == UiStatus.CONNECTED -> ConnectButtonState.STOP
    state.canStart -> ConnectButtonState.START
    else -> ConnectButtonState.DISABLED
}

/**
 * Three things vertically: the status glyph, the one button, and the session
 * detail pinned to the bottom edge. Errors and Shizuku prompts are toasts, so
 * nothing here moves when one arrives.
 */
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

        // The VPN line rides with the status row, so both readings of "what is
        // this session doing" sit together at the bottom edge.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Space reserved either way: a VPN can be adopted mid-session with
            // the screen open, and the status row must not shift down. Bottom-
            // aligned so the line sits with the row it belongs to.
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

/** Grouped so the screen takes two parameters rather than five. */
data class HomeActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Badge on the left, navigation on the right. No title — naming the one home
 * screen would state the obvious at the largest size on the page.
 *
 * One destination: the log now lives under Developer in settings, beside the
 * toggle deciding whether it records anything.
 */
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

        // Muted: the connect button is what the screen is for, and a header
        // glyph at full strength reads as its peer.
        ShizziIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
            tint = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}

/**
 * Guarded on the status as well as the flag: a session torn down for VPN loss
 * holds ERROR alongside the last VPN reading until the next publish.
 */
private fun isShowingVpn(state: SessionUiState): Boolean =
    state.isVpnBound && state.status == UiStatus.CONNECTED

/**
 * Cancel appears only while starting: stopping is already the fast path, and
 * cancelling that would leave the session in the state this app exists to avoid.
 */
@Composable
private fun HomeBody(state: SessionUiState, actions: HomeActions) {
    val isStarting = state.status == UiStatus.LOADING

    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusIcon(status = state.status)

        // Two of the largest step rather than an arbitrary value, keeping the
        // gap on the scale. Preserves the distance from when a reserved VPN
        // band sat here.
        Spacer(Modifier.height(ShizziTheme.spacing.xxxl * 2))

        ConnectButton(
            label = buttonLabel(state.status),
            state = buttonState(state),
            onClick = actions.onToggle,
        )

        // Reserved whether or not it is showing, so the button does not shift
        // up and down as a session starts.
        Box(modifier = Modifier.height(ShizziTheme.spacing.xxxl)) {
            if (isStarting) CancelButton(onClick = actions.onCancel)
        }
    }
}
