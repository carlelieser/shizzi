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
import dev.shizzi.ui.ExternalControlActions
import dev.shizzi.ui.ExternalControlState
import dev.shizzi.ui.SettingsPage
import dev.shizzi.ui.SettingsState
import dev.shizzi.ui.ToastHost
import dev.shizzi.ui.rememberLogEntries
import dev.shizzi.ui.rememberNavigator
import dev.shizzi.ui.rememberToastState
import dev.shizzi.ui.theme.ThemeChoice

data class AppActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onRunProbes: () -> Unit,
    val onDismissDiagnostics: () -> Unit,
    val onClearLog: (onCleared: (String?) -> Unit) -> Unit,
    val onRestartOnboarding: () -> Unit,
    val onSetExternalControl: (Boolean) -> Unit,
    val onSetExternalControlToken: (String) -> Unit,
    val onRegenerateExternalControlToken: () -> Unit,
)

@Composable
fun HomeScreen(
    state: SessionUiState,
    settings: Settings,
    diagnostics: DiagnosticsState,
    actions: AppActions,
) {
    val current = rememberNavigator()
    val goHome = { current.value = Screen.HOME }

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
                    externalControl = ExternalControlState(
                        isEnabled = settings.isExternalControlEnabled,
                        token = settings.externalControlToken,
                    ),
                ),
                actions = SettingsActions(
                    onSetTheme = actions.onSetTheme,
                    onSetLogging = actions.onSetLogging,
                    onOpenLog = { current.value = Screen.LOG },
                    onRunProbes = actions.onRunProbes,
                    onRequestPermission = actions.onRequestPermission,
                    onRestartOnboarding = actions.onRestartOnboarding,
                    externalControl = ExternalControlActions(
                        onSetEnabled = actions.onSetExternalControl,
                        onRegenerateToken = actions.onRegenerateExternalControlToken,
                        onClearToken = { actions.onSetExternalControlToken("") },
                    ),
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

        ToastHost(
            state = toasts,
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding(),
        )
    }
}
