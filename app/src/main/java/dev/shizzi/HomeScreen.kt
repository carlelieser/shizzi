package dev.shizzi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.ui.DiagnosticsToast
import dev.shizzi.ui.HandleBack
import dev.shizzi.ui.HomeActions
import dev.shizzi.ui.HomePage
import dev.shizzi.ui.LogActions
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
    val onDismissDiagnostics: () -> Unit,
    val onClearLog: (onCleared: (String?) -> Unit) -> Unit,
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
    diagnostics: DiagnosticsState,
    actions: AppActions,
) {
    val current = rememberNavigator()
    val goHome = { current.value = Screen.HOME }

    // Back from the log returns to settings, which is now the only way into it
    // — sending it to Home would drop the user two levels from one gesture.
    val goBack = {
        current.value = if (current.value == Screen.LOG) Screen.SETTINGS else Screen.HOME
    }
    HandleBack(current.value, goBack)

    val toasts = rememberToastState()
    SessionToasts(
        state = state,
        toasts = toasts,
        onRequestPermission = actions.onRequestPermission,
    )

    // Posted here rather than from the settings screen: a run outlives a visit
    // to it, and a toast owned by that screen would vanish the moment the user
    // navigated home to wait — taking the export button with it.
    DiagnosticsToast(
        state = diagnostics,
        toasts = toasts,
        onDismiss = actions.onDismissDiagnostics,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (current.value) {
            Screen.SETTINGS -> SettingsPage(
                state = SettingsState(
                    shizuku = state.shizukuState,
                    theme = settings.theme,
                    isLogging = settings.isLogging,
                    isRunningDiagnostics = diagnostics is DiagnosticsState.Running,
                ),
                actions = SettingsActions(
                    onSetTheme = actions.onSetTheme,
                    onSetLogging = actions.onSetLogging,
                    onOpenLog = { current.value = Screen.LOG },
                    onRunProbes = actions.onRunProbes,
                    onRequestPermission = actions.onRequestPermission,
                ),
                onBack = goHome,
            )

            Screen.LOG -> LogPage(
                log = rememberLogEntries(),
                toasts = toasts,
                isLogging = settings.isLogging,
                actions = LogActions(
                    onClear = actions.onClearLog,
                    onEnableLogging = { actions.onSetLogging(true) },
                    // Goes home as well as starting: the session's progress is
                    // reported by the status glyph and the button, none of
                    // which is on this screen. Leaving the user on an empty log
                    // would hide the thing they just asked for.
                    onStartSession = {
                        goHome()
                        actions.onToggle()
                    },
                    onBack = goBack,
                ),
            )

            Screen.HOME -> HomePage(
                state = state,
                actions = HomeActions(
                    onToggle = actions.onToggle,
                    onCancel = actions.onCancel,
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
