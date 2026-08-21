package dev.shizzi

import androidx.compose.runtime.Composable
import dev.shizzi.ui.onboarding.OnboardingActions
import dev.shizzi.ui.onboarding.OnboardingFlow

/**
 * Everything onboarding needs that [AppActions] does not already carry.
 *
 * Separate from it because the two are used at different times by different
 * trees: [HomeScreen] never runs a compatibility check, and the wizard never
 * starts a session.
 */
data class OnboardingEntry(
    val compatibility: CompatibilityState,
    val onCheckCompatibility: () -> Unit,
    val onDownloadTetheringApex: () -> Unit,
    val onInstallTetheringApex: () -> Unit,
    val onRebootDevice: () -> Unit,
    val onComplete: () -> Unit,
)

/** What the app screens render from, which the wizard mostly does not read. */
data class AppState(
    val session: SessionUiState,
    val settings: Settings,
    val diagnostics: DiagnosticsState,
)

/**
 * Chooses between the wizard and the app.
 *
 * Above [HomeScreen] rather than inside it: onboarding is not a destination in
 * the app's back stack — it precedes it, and a user who finishes does not
 * navigate back into it.
 */
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

    HomeScreen(
        state = state.session,
        settings = state.settings,
        diagnostics = state.diagnostics,
        actions = actions,
    )
}
