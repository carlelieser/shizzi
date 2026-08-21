package dev.shizzi.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import dev.shizzi.CompatibilityState
import dev.shizzi.ShizukuState
import dev.shizzi.isCompatible

enum class OnboardingStep { WELCOME, SHIZUKU, COMPATIBILITY }

data class OnboardingActions(
    val onRequestPermission: () -> Unit,
    val onCheckCompatibility: () -> Unit,
    val onFinish: () -> Unit,
)

@Composable
fun OnboardingFlow(
    shizukuState: ShizukuState,
    compatibility: CompatibilityState,
    actions: OnboardingActions,
) {
    val current = rememberOnboardingStep()

    LaunchedEffect(current.value) {
        if (current.value == OnboardingStep.COMPATIBILITY) actions.onCheckCompatibility()
    }

    val step = when (current.value) {
        OnboardingStep.WELCOME -> welcomeStep(onNext = { current.value = OnboardingStep.SHIZUKU })

        OnboardingStep.SHIZUKU -> shizukuStep(
            state = shizukuState,
            onGrant = actions.onRequestPermission,
            onNext = { current.value = OnboardingStep.COMPATIBILITY },
        )

        OnboardingStep.COMPATIBILITY -> compatibilityStep(
            state = compatibility,
            actions = actions,
        )
    }

    Wizard(
        step = step,
        currentIndex = current.value.ordinal,
        stepCount = OnboardingStep.entries.size,
    )
}

@Composable
private fun rememberOnboardingStep(): MutableState<OnboardingStep> =
    rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }

private fun welcomeStep(onNext: () -> Unit) = WizardStep(
    title = "",
    content = { WelcomeStep() },
    primary = WizardAction(label = "Get started", onClick = onNext),
)

private fun shizukuStep(
    state: ShizukuState,
    onGrant: () -> Unit,
    onNext: () -> Unit,
) = WizardStep(
    title = "Shizuku",
    content = { ShizukuStep(state = state, onGrant = onGrant) },
    primary = WizardAction(
        label = "Continue",
        isEnabled = state is ShizukuState.Ready,
        onClick = onNext,
    ),
)

private fun compatibilityStep(
    state: CompatibilityState,
    actions: OnboardingActions,
) = WizardStep(
    title = "Compatibility",
    content = { CompatibilityStep(state) },
    primary = when {
        state.isCompatible -> WizardAction(label = "Finish", onClick = actions.onFinish)

        else -> WizardAction(
            label = "Check",
            isEnabled = state !is CompatibilityState.Checking,
            onClick = actions.onCheckCompatibility,
        )
    },
)
