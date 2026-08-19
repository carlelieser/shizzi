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
import androidx.compose.material.icons.automirrored.filled.TextSnippet
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

/**
 * How the button presents itself.
 *
 * Derived here rather than inside the button so the rule lives next to the
 * state it reads: only an available start is the primary action.
 */
private fun buttonState(state: SessionUiState): ConnectButtonState = when {
    state.status == UiStatus.LOADING -> ConnectButtonState.LOADING
    state.status == UiStatus.CONNECTED -> ConnectButtonState.STOP
    state.canStart -> ConnectButtonState.START
    else -> ConnectButtonState.DISABLED
}

/**
 * The screen the app opens on.
 *
 * Three things, vertically: the status glyph, the one button, and — pinned to
 * the bottom edge rather than floating under the button — whatever detail the
 * session reports. Errors and Shizuku prompts are toasts, so nothing here
 * moves when one arrives.
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
            onOpenLog = actions.onOpenLog,
            onOpenSettings = actions.onOpenSettings,
        )

        HomeBody(state = state, actions = actions)

        // The VPN line rides with the status row rather than with the button,
        // so both readings of "what is this session doing" sit together at the
        // bottom edge instead of one floating mid-screen.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Reserved whether or not it is showing: a VPN can be adopted
            // mid-session with the screen open, and the status row must not
            // shift down when it is.
            //
            // Bottom-aligned inside the band so the line sits close to the
            // status row it belongs with, rather than floating in the middle
            // of its own reserved space.
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

/**
 * The callbacks the home screen needs, grouped so it takes two parameters
 * rather than five.
 */
data class HomeActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenLog: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Badge on the left, navigation on the right.
 *
 * No title: the app has one home screen and naming it would state the obvious
 * at the largest type size on the page.
 */
@Composable
private fun HomeHeader(
    state: SessionUiState,
    onOpenLog: () -> Unit,
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
            icon = Icons.AutoMirrored.Filled.TextSnippet,
            contentDescription = "Log",
            onClick = onOpenLog,
        )
        ShizziIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
        )
    }
}

/**
 * Whether the VPN line should show.
 *
 * Guarded on the status as well as the flag: a session torn down for VPN loss
 * holds ERROR alongside the last VPN reading until the next publish, and the
 * line must not outlive the session it describes.
 */
private fun isShowingVpn(state: SessionUiState): Boolean =
    state.isVpnBound && state.status == UiStatus.CONNECTED

/**
 * The centred stack: glyph, button, and the cancel affordance.
 *
 * Cancel appears only while starting. Stopping is already the fast path and
 * cancelling it would leave the session in the state this app exists to avoid.
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

        // Two of the largest step rather than one arbitrary value, so the
        // gap stays on the spacing scale. It preserves the distance from when
        // a reserved VPN band sat between the glyph and the button.
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
