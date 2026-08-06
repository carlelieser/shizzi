package dev.shizzi.spike

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.spike.ui.HandleBack
import dev.shizzi.spike.ui.HomeActions
import dev.shizzi.spike.ui.HomePage
import dev.shizzi.spike.ui.LogPage
import dev.shizzi.spike.ui.Screen
import dev.shizzi.spike.ui.SessionToasts
import dev.shizzi.spike.ui.SettingsPage
import dev.shizzi.spike.ui.ToastHost
import dev.shizzi.spike.ui.rememberLogEntries
import dev.shizzi.spike.ui.rememberNavigator
import dev.shizzi.spike.ui.rememberToastState

/**
 * Everything the screens need from the ViewModel, grouped so routing does not
 * take a parameter per callback.
 */
data class AppActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onSetDebugLogging: (Boolean) -> Unit,
    val onRunProbes: () -> Unit,
)

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
    actions: AppActions,
) {
    val current = rememberNavigator()
    val goHome = { current.value = Screen.HOME }
    HandleBack(current.value, goHome)

    val toasts = rememberToastState()
    SessionToasts(
        state = state,
        toasts = toasts,
        onRequestPermission = actions.onRequestPermission,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (current.value) {
            Screen.SETTINGS -> SettingsPage(
                isDebugLogging = settings.isDebugLogging,
                onSetDebugLogging = actions.onSetDebugLogging,
                onRunProbes = actions.onRunProbes,
                onBack = goHome,
            )

            Screen.LOG -> LogPage(entries = rememberLogEntries(), onBack = goHome)

            Screen.HOME -> HomePage(
                state = state,
                actions = HomeActions(
                    onToggle = actions.onToggle,
                    onCancel = actions.onCancel,
                    onOpenLog = { current.value = Screen.LOG },
                    onOpenSettings = { current.value = Screen.SETTINGS },
                ),
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
