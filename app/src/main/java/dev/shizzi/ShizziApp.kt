package dev.shizzi

import androidx.compose.runtime.Composable
import dev.shizzi.ui.onboarding.OnboardingActions
import dev.shizzi.ui.onboarding.OnboardingFlow

data class OnboardingEntry(
    val compatibility: CompatibilityState,
    val onCheckCompatibility: () -> Unit,
    val onDownloadTetheringApex: () -> Unit,
    val onInstallTetheringApex: () -> Unit,
    val onRebootDevice: () -> Unit,
    val onComplete: () -> Unit,
)

data class AppState(
    val session: SessionUiState,
    val settings: Settings,
    val diagnostics: DiagnosticsState,
)

@Composable
fun ShizziApp(
    state: AppState,
    onboarding: OnboardingEntry,
    actions: AppActions,
) {
    if (!state.settings.hasCompletedOnboarding) {
        OnboardingFlow(
            shizukuState = state.session.shizukuState,
            compatibility = onboarding.compatibility,
            actions = OnboardingActions(
                onRequestPermission = actions.onRequestPermission,
                onCheckCompatibility = onboarding.onCheckCompatibility,
                onDownloadTetheringApex = onboarding.onDownloadTetheringApex,
                onInstallTetheringApex = onboarding.onInstallTetheringApex,
                onRebootDevice = onboarding.onRebootDevice,
                onFinish = onboarding.onComplete,
            ),
        )
        return
    }

    HomeScreen(state = state, actions = actions)
}
