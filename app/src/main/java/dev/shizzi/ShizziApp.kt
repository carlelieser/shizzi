package dev.shizzi

import androidx.compose.runtime.Composable
import dev.shizzi.ui.onboarding.OnboardingActions
import dev.shizzi.ui.onboarding.OnboardingFlow

data class OnboardingEntry(
    val compatibility: CompatibilityState,
    val onCheckCompatibility: () -> Unit,
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
                onFinish = onboarding.onComplete,
            ),
        )
        return
    }

    HomeScreen(
        state = state.session,
        settings = state.settings,
        diagnostics = state.diagnostics,
        actions = actions,
    )
}
