package dev.shizzi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.ui.AutomationActions
import dev.shizzi.ui.AutomationState
import dev.shizzi.ui.DiagnosticsToast
import dev.shizzi.ui.EasterEggPage
import dev.shizzi.ui.HomeActions
import dev.shizzi.ui.HomePage
import dev.shizzi.ui.LogActions
import dev.shizzi.ui.LogPage
import dev.shizzi.ui.PredictiveBackProgress
import dev.shizzi.ui.Screen
import dev.shizzi.ui.ScreenHost
import dev.shizzi.ui.SessionToasts
import dev.shizzi.ui.SettingsActions
import dev.shizzi.ui.SettingsPage
import dev.shizzi.ui.SettingsState
import dev.shizzi.ui.ToastHost
import dev.shizzi.ui.ToastState
import dev.shizzi.ui.rememberLogEntries
import dev.shizzi.ui.rememberNavigator
import dev.shizzi.ui.rememberToastState
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ThemeChoice

data class AppActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onRequestAllPermissions: () -> Unit,
    val onGrantPermission: (AppPermission) -> Unit,
    val onSetPrivilegeBackend: (PrivilegeBackendType) -> Unit,
    val onShizukuAction: () -> Unit,
    val onStartLocalAdbPairing: () -> Unit,
    val onCancelLocalAdbPairing: () -> Unit,
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetDesign: (DesignLanguage) -> Unit,
    val onSetAccent: (AccentChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onSetVpnMode: (VpnMode) -> Unit,
    val onRunProbes: () -> Unit,
    val onCancelProbes: () -> Unit,
    val onDismissDiagnostics: () -> Unit,
    val onClearLog: (onCleared: (String?) -> Unit) -> Unit,
    val onRestartOnboarding: () -> Unit,
    val onSetAutomation: (Boolean) -> Unit,
    val onRegenerateAutomationToken: () -> Unit,
)

@Composable
fun HomeScreen(state: AppState, actions: AppActions) {
    val current = rememberNavigator()
    val goHome = { current.value = Screen.HOME }
    val goBack = { current.value = if (current.value == Screen.LOG) Screen.SETTINGS else Screen.HOME }
    val backProgress = PredictiveBackProgress(current.value, goBack)
    val toasts = rememberToastState()
    val navigation = Navigation(goHome, goBack) { current.value = it }

    SessionToasts(state.session, toasts)
    DiagnosticsToast(state.diagnostics, toasts, actions.onDismissDiagnostics, actions.onCancelProbes)

    Box(Modifier.fillMaxSize()) {
        ScreenHost(current.value, backProgress) { screen ->
            ScreenBody(screen, ScreenContext(state, actions, toasts, navigation))
        }
        ToastHost(toasts, Modifier.align(Alignment.BottomCenter).systemBarsPadding())
    }
}

private data class Navigation(val goHome: () -> Unit, val goBack: () -> Unit, val open: (Screen) -> Unit)
private data class ScreenContext(val state: AppState, val actions: AppActions, val toasts: ToastState, val navigation: Navigation)

@Composable private fun ScreenBody(screen: Screen, context: ScreenContext) = when (screen) {
    Screen.HOME -> HomePage(context.state.session, HomeActions(
        context.actions.onToggle, context.actions.onCancel,
        { context.navigation.open(Screen.SETTINGS) }, { context.navigation.open(Screen.EASTER_EGG) },
    ))
    Screen.SETTINGS -> SettingsPage(settingsState(context.state), settingsActions(context), context.toasts, context.navigation.goHome)
    Screen.LOG -> LogRoute(context)
    Screen.EASTER_EGG -> EasterEggPage(context.navigation.goHome)
}

private fun settingsState(state: AppState) = SettingsState(
    backend = state.settings.privilegeBackend,
    privilegeState = state.session.privilegeState,
    permissions = state.permissions,
    theme = state.settings.theme,
    design = state.settings.design,
    accent = state.settings.accent,
    isLogging = state.settings.isLogging,
    vpnMode = state.settings.vpnMode,
    isRunningDiagnostics = state.diagnostics is DiagnosticsState.Running,
    automation = AutomationState(state.settings.isAutomationEnabled, state.settings.automationToken),
)

private fun settingsActions(context: ScreenContext): SettingsActions = with(context.actions) {
    SettingsActions(
        onSetTheme, onSetDesign, onSetAccent, onSetLogging, onSetVpnMode,
        onOpenLog = { context.navigation.open(Screen.LOG) },
        onRunProbes = onRunProbes,
        onCancelProbes = onCancelProbes,
        onGrantPermission = onGrantPermission,
        onSetPrivilegeBackend = onSetPrivilegeBackend,
        onShizukuAction = onShizukuAction,
        onStartLocalAdbPairing = onStartLocalAdbPairing,
        onCancelLocalAdbPairing = onCancelLocalAdbPairing,
        onRestartOnboarding = onRestartOnboarding,
        automation = AutomationActions(onSetAutomation, onRegenerateAutomationToken),
    )
}

@Composable private fun LogRoute(context: ScreenContext) {
    LogPage(
        log = rememberLogEntries(),
        toasts = context.toasts,
        isLogging = context.state.settings.isLogging,
        actions = LogActions(
            onClear = context.actions.onClearLog,
            onEnableLogging = { context.actions.onSetLogging(true) },
            onStartSession = { context.navigation.goHome(); context.actions.onToggle() },
            onBack = context.navigation.goBack,
        ),
    )
}
