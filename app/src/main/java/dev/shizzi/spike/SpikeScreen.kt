package dev.shizzi.spike

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shizzi.spike.ui.HandleBack
import dev.shizzi.spike.ui.Screen
import dev.shizzi.spike.ui.ScreenHeader
import dev.shizzi.spike.ui.SessionToasts
import dev.shizzi.spike.ui.SettingsPage
import dev.shizzi.spike.ui.ShizziIconButton
import dev.shizzi.spike.ui.ToastHost
import dev.shizzi.spike.ui.rememberNavigator
import dev.shizzi.spike.ui.rememberToastState

/** Colour for each status, kept next to the label so they cannot disagree. */
private fun statusColor(status: UiStatus): Color = when (status) {
    UiStatus.READY -> Color(0xFF9E9E9E)
    UiStatus.LOADING -> Color(0xFFFFA726)
    UiStatus.CONNECTED -> Color(0xFF43A047)
    UiStatus.ERROR -> Color(0xFFE53935)
}

private fun statusLabel(status: UiStatus): String = when (status) {
    UiStatus.READY -> "Ready"
    UiStatus.LOADING -> "Loading"
    UiStatus.CONNECTED -> "Connected"
    UiStatus.ERROR -> "Error"
}

/**
 * Routes between the three screens.
 *
 * Log and Settings are screens rather than overlays, so the system back
 * gesture returns to Home rather than leaving the app.
 */
@Composable
fun SpikeScreen(
    state: SpikeUiState,
    settings: Settings,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    onSetDebugLogging: (Boolean) -> Unit,
    onRunProbes: () -> Unit,
) {
    val current = rememberNavigator()
    val goHome = { current.value = Screen.HOME }
    HandleBack(current.value, goHome)

    val toasts = rememberToastState()
    SessionToasts(state = state, toasts = toasts, onRequestPermission = onRequestPermission)

    Box(modifier = Modifier.fillMaxSize()) {
        when (current.value) {
            Screen.SETTINGS -> SettingsPage(
                isDebugLogging = settings.isDebugLogging,
                onSetDebugLogging = onSetDebugLogging,
                onRunProbes = onRunProbes,
                onBack = goHome,
            )

            // Placeholder until the log screen lands; navigation is wired now
            // so the route exists before the destination does.
            Screen.LOG -> LogPlaceholder(onBack = goHome)

            Screen.HOME -> MainPage(
                state = state,
                onToggle = onToggle,
                onOpenSettings = { current.value = Screen.SETTINGS },
                onOpenLog = { current.value = Screen.LOG },
            )
        }

        // Last child, so it draws over whichever screen is showing. Toasts are
        // app-level rather than per-screen: an error raised on Home is still
        // worth seeing after navigating to the log to investigate it.
        ToastHost(
            state = toasts,
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding(),
        )
    }
}

@Composable
private fun LogPlaceholder(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(title = "Log", onBack = onBack)
    }
}

@Composable
private fun MainPage(
    state: SpikeUiState,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShizziIconButton(
                icon = Icons.AutoMirrored.Filled.List,
                contentDescription = "Log",
                onClick = onOpenLog,
            )
            SettingsButton(onClick = onOpenSettings)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StatusIndicator(state.status)
            Spacer(Modifier.height(48.dp))
            ToggleButton(state, onToggle)
            Spacer(Modifier.height(24.dp))
            StatusDetail(state)
        }
    }
}

/** The centered status indicator: a dot and its label. */
@Composable
private fun StatusIndicator(status: UiStatus) {
    val color by animateColorAsState(statusColor(status), label = "statusColor")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(color),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = statusLabel(status),
            fontSize = 22.sp,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ToggleButton(state: SpikeUiState, onToggle: () -> Unit) {
    val isConnected = state.status == UiStatus.CONNECTED

    Button(
        onClick = onToggle,
        enabled = state.canStart || isConnected,
        modifier = Modifier.width(200.dp).height(56.dp),
        colors = when {
            isConnected -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            )

            else -> ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = if (isConnected) "Stop" else "Start",
            fontSize = 18.sp,
        )
    }
}

/**
 * Detail under the button: the active interface, or whatever the session last
 * reported.
 *
 * Errors and Shizuku prompts used to land here too. They are toasts now — a
 * message that needs an action needs somewhere to put the button, and
 * duplicating it in both places would say the same thing twice.
 */
@Composable
private fun StatusDetail(state: SpikeUiState) {
    val message = when {
        state.interfaceName.isNotEmpty() -> "via ${state.interfaceName}"

        // On a failed session the state carries the same string in both
        // fields, so rendering detail here would print the error twice: once
        // under the button and once in its toast.
        state.lastError.isNotEmpty() -> ""

        else -> state.detail
    }

    if (message.isNotEmpty()) {
        Text(
            text = message,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ShizziIconButton(
        icon = Icons.Filled.Settings,
        contentDescription = "Settings",
        onClick = onClick,
        modifier = modifier,
    )
}
