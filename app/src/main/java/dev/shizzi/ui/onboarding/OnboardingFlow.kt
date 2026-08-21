package dev.shizzi.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import dev.shizzi.CompatibilityState
import dev.shizzi.ShizukuState
import dev.shizzi.isCompatible

/**
 * The steps, in order. Adding one is an entry here plus the composable it
 * names; the wizard reads the count from this and needs no other change.
 */
enum class OnboardingStep { WELCOME, SHIZUKU, COMPATIBILITY }

/** What the flow needs from the app, grouped so it stays within the parameter limit. */
data class OnboardingActions(
    val onRequestPermission: () -> Unit,
    val onCheckCompatibility: () -> Unit,
    val onFinish: () -> Unit,
)

/**
 * Runs the three steps and hands off to the app when the last one passes.
 *
 * No back navigation: each step is a precondition for the one after it, so
 * returning to an earlier one offers a user nothing to change. The system back
 * gesture leaves the app, as it does on Home.
 */
@Composable
fun OnboardingFlow(
    shizukuState: ShizukuState,
    compatibility: CompatibilityState,
    actions: OnboardingActions,
) {
    val current = rememberOnboardingStep()

    // Started with the step rather than by a button, so a device that passes
    // shows its verdict on arrival. Check then re-runs it for one that did not.
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

/** Saveable so a rotation mid-onboarding does not return to Welcome. */
@Composable
private fun rememberOnboardingStep(): MutableState<OnboardingStep> =
    rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }

private fun welcomeStep(onNext: () -> Unit) = WizardStep(
    title = "",
    content = { WelcomeStep() },
    primary = WizardAction(label = "Get started", onClick = onNext),
)

/**
 * Continue is gated on Shizuku being ready, not merely installed: the next step
 * asks the privileged process a question, and arriving there unable to bind
 * would report the device incompatible over a Shizuku that is simply not
 * running.
 */
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

/**
 * Finish appears only for a device that passed. One that did not keeps Check,
 * which is the only action left worth offering — Shizuku may have been granted
 * since, and nothing else on this screen can change the answer.
 */
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
