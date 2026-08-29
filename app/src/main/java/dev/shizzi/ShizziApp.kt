package dev.shizzi

import androidx.compose.runtime.Composable
import dev.shizzi.ui.onboarding.OnboardingActions
import dev.shizzi.ui.onboarding.OnboardingFlow
import dev.shizzi.ui.onboarding.OnboardingState

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
    val permissions: List<PermissionStatus>,
)

@Composable
fun ShizziApp(
    state: AppState,
    onboarding: OnboardingEntry,
    actions: AppActions,
) {
    if (!state.settings.hasCompletedOnboarding) {
        OnboardingFlow(
            state = OnboardingState(
                shizuku = state.session.shizukuState,
                compatibility = onboarding.compatibility,
                permissions = state.permissions,
            ),
            actions = OnboardingActions(
                onRequestPermission = actions.onRequestPermission,
                onGrantPermission = actions.onGrantPermission,
                onCheckCompatibility = onboarding.onCheckCompatibility,
                onDownloadTetheringApex = onboarding.onDownloadTetheringApex,
                onInstallTetheringApex = onboarding.onInstallTetheringApex,
                onRebootDevice = onboarding.onRebootDevice,
                onFinish = onboarding.onComplete,
            ),
        )
        return
    }

    HomeScreen(
        state = state,
        actions = actions,
    )
}
