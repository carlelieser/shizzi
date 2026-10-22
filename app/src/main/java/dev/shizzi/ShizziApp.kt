package dev.shizzi

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import dev.shizzi.ui.onboarding.OnboardingActions
import dev.shizzi.ui.onboarding.OnboardingFlow
import dev.shizzi.ui.onboarding.OnboardingState
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardTween

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

private const val HandoffScale = 0.94f

@Composable
fun ShizziApp(state: AppState, onboarding: OnboardingEntry, actions: AppActions) {
    val fadeSpec = standardTween<Float>()
    val scaleSpec = tween<Float>(ShizziTheme.motion.slowMillis, easing = ShizziTheme.motion.easing)
    AnimatedContent(
        targetState = state.settings.hasCompletedOnboarding,
        transitionSpec = { (fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = HandoffScale)) togetherWith fadeOut(fadeSpec) },
        label = "onboardingHandoff",
    ) { completed ->
        if (completed) HomeScreen(state, actions) else OnboardingRoute(state, onboarding, actions)
    }
}

@Composable
private fun OnboardingRoute(state: AppState, onboarding: OnboardingEntry, actions: AppActions) {
    OnboardingFlow(
        state = OnboardingState(
            backend = state.settings.privilegeBackend,
            privilegeState = state.session.privilegeState,
            compatibility = onboarding.compatibility,
            permissions = state.permissions,
        ),
        actions = OnboardingActions(
            onRequestAllPermissions = actions.onRequestAllPermissions,
            onGrantPermission = actions.onGrantPermission,
            onSelectPrivilegeBackend = actions.onSetPrivilegeBackend,
            onShizukuAction = actions.onShizukuAction,
            onStartLocalAdbPairing = actions.onStartLocalAdbPairing,
            onCancelLocalAdbPairing = actions.onCancelLocalAdbPairing,
            onCheckCompatibility = onboarding.onCheckCompatibility,
            onDownloadTetheringApex = onboarding.onDownloadTetheringApex,
            onInstallTetheringApex = onboarding.onInstallTetheringApex,
            onRebootDevice = onboarding.onRebootDevice,
            onFinish = onboarding.onComplete,
        ),
    )
}
