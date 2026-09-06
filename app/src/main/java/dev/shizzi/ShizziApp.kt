package dev.shizzi

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardTween
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

private const val HandoffScale = 0.94f

/**
 * Finishing onboarding hands off to the app proper, so the wizard dissolves
 * while home swells in behind it rather than the two hard-cutting.
 */
@Composable
fun ShizziApp(
    state: AppState,
    onboarding: OnboardingEntry,
    actions: AppActions,
) {
    val fadeSpec = standardTween<Float>()
    val scaleSpec = tween<Float>(
        durationMillis = ShizziTheme.motion.slowMillis,
        easing = ShizziTheme.motion.easing,
    )

    AnimatedContent(
        targetState = state.settings.hasCompletedOnboarding,
        transitionSpec = {
            val enter = fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = HandoffScale)

            enter togetherWith fadeOut(fadeSpec)
        },
        label = "onboardingHandoff",
    ) { hasCompletedOnboarding ->
        when {
            hasCompletedOnboarding -> HomeScreen(state = state, actions = actions)
            else -> OnboardingRoute(state = state, onboarding = onboarding, actions = actions)
        }
    }
}

@Composable
private fun OnboardingRoute(
    state: AppState,
    onboarding: OnboardingEntry,
    actions: AppActions,
) {
    OnboardingFlow(
        state = OnboardingState(
            shizuku = state.session.shizukuState,
            compatibility = onboarding.compatibility,
            permissions = state.permissions,
        ),
        actions = OnboardingActions(
            onRequestAllPermissions = actions.onRequestAllPermissions,
            onGrantPermission = actions.onGrantPermission,
            onShizukuAction = actions.onShizukuAction,
            onCheckCompatibility = onboarding.onCheckCompatibility,
            onDownloadTetheringApex = onboarding.onDownloadTetheringApex,
            onInstallTetheringApex = onboarding.onInstallTetheringApex,
            onRebootDevice = onboarding.onRebootDevice,
            onFinish = onboarding.onComplete,
        ),
    )
}
