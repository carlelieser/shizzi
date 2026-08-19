package dev.shizzi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.ui.HandleBack
import dev.shizzi.ui.HomeActions
import dev.shizzi.ui.HomePage
import dev.shizzi.ui.LogPage
import dev.shizzi.ui.Screen
import dev.shizzi.ui.SessionToasts
import dev.shizzi.ui.SettingsActions
import dev.shizzi.ui.SettingsPage
import dev.shizzi.ui.SettingsState
import dev.shizzi.ui.ToastHost
import dev.shizzi.ui.rememberLogEntries
import dev.shizzi.ui.rememberNavigator
import dev.shizzi.ui.rememberToastState
import dev.shizzi.ui.theme.ThemeChoice

/**
 * Everything the screens need from the ViewModel, grouped so routing does not
 * take a parameter per callback.
 */
data class AppActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onRunProbes: () -> Unit,
)

/**
 * Routes between the three screens.
 *
 * Log and Settings are screens rather than overlays, so the system back
 * gesture returns to Home rather than leaving the app.
 */
@Composable
fun HomeScreen(
    state: SessionUiState,
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
                state = SettingsState(
                    shizuku = state.shizukuState,
                    theme = settings.theme,
                    isLogging = settings.isLogging,
                ),
                actions = SettingsActions(
                    onSetTheme = actions.onSetTheme,
                    onSetLogging = actions.onSetLogging,
                    onRunProbes = actions.onRunProbes,
                    onRequestPermission = actions.onRequestPermission,
                ),
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
