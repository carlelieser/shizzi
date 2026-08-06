package dev.shizzi.spike.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.shizzi.spike.SpikeUiState
import dev.shizzi.spike.UiStatus
import dev.shizzi.spike.ui.theme.HeaderHeight
import dev.shizzi.spike.ui.theme.ScreenPadding
import dev.shizzi.spike.ui.theme.ShizziTheme

/** What the button does, given what the session is doing. */
private fun buttonLabel(status: UiStatus): String =
    if (status == UiStatus.CONNECTED) "Stop" else "Start"

/**
 * How the button presents itself.
 *
 * Derived here rather than inside the button so the rule lives next to the
 * state it reads: only an available start is the primary action.
 */
private fun buttonState(state: SpikeUiState): ConnectButtonState = when {
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
    state: SpikeUiState,
    actions: HomeActions,
) {
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        HomeHeader(
            state = state,
            onOpenLog = actions.onOpenLog,
            onOpenSettings = actions.onOpenSettings,
        )

        HomeBody(state = state, onToggle = actions.onToggle, onCancel = actions.onCancel)

        SessionDetail(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
    state: SpikeUiState,
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
 * The centred stack: glyph, button, and the cancel affordance.
 *
 * Cancel appears only while starting. Stopping is already the fast path and
 * cancelling it would leave the session in the state this app exists to avoid.
 */
@Composable
private fun HomeBody(state: SpikeUiState, onToggle: () -> Unit, onCancel: () -> Unit) {
    val isStarting = state.status == UiStatus.LOADING

    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusIcon(state.status)

        Spacer(Modifier.height(ShizziTheme.spacing.xxxl))

        ConnectButton(
            label = buttonLabel(state.status),
            state = buttonState(state),
            onClick = onToggle,
        )

        // Reserved whether or not it is showing, so the button does not shift
        // up and down as a session starts.
        Box(modifier = Modifier.height(ShizziTheme.spacing.xxxl)) {
            if (isStarting) CancelButton(onClick = onCancel)
        }
    }
}

/**
 * The session's own words, along the bottom edge.
 *
 * Uppercase and muted: it is a status line rather than prose, and it should be
 * findable without competing with the button for attention.
 */
@Composable
private fun SessionDetail(state: SpikeUiState, modifier: Modifier = Modifier) {
    val message = when {
        // Errors belong to the toast. applyOutcome writes the same string to
        // detail and lastError, so both are suppressed here — and detail
        // outlives lastError across a retry, which put a stale exception along
        // the bottom edge of a screen that was busy starting.
        state.status == UiStatus.ERROR || state.lastError.isNotEmpty() -> ""

        state.interfaceName.isNotEmpty() -> "via ${state.interfaceName}"
        else -> state.detail
    }

    if (message.isEmpty()) return

    Text(
        text = message.uppercase(),
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(ScreenPadding),
    )
}
