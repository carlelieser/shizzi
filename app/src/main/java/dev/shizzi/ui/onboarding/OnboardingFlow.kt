package dev.shizzi.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import dev.shizzi.AppPermission
import dev.shizzi.CompatibilityState
import dev.shizzi.isCompatible
import dev.shizzi.isOnFixPath
import dev.shizzi.ui.PermissionRowSources
import dev.shizzi.ui.PermissionRowState
import dev.shizzi.ui.permissionRows

enum class OnboardingStep { WELCOME, PERMISSIONS, COMPATIBILITY }

data class OnboardingActions(
    val onRequestAllPermissions: () -> Unit,
    val onGrantPermission: (AppPermission) -> Unit,
    val onShizukuAction: () -> Unit,
    val onCheckCompatibility: () -> Unit,
    val onDownloadTetheringApex: () -> Unit,
    val onInstallTetheringApex: () -> Unit,
    val onRebootDevice: () -> Unit,
    val onFinish: () -> Unit,
)

@Composable
fun OnboardingFlow(state: OnboardingState, actions: OnboardingActions) {
    val current = rememberOnboardingStep()

    LaunchedEffect(current.value) {
        if (current.value == OnboardingStep.COMPATIBILITY) actions.onCheckCompatibility()
    }

    val step = when (current.value) {
        OnboardingStep.WELCOME ->
            welcomeStep(onNext = { current.value = OnboardingStep.PERMISSIONS })

        OnboardingStep.PERMISSIONS -> permissionsStep(
            state = state,
            actions = actions,
            onNext = { current.value = OnboardingStep.COMPATIBILITY },
        )

        OnboardingStep.COMPATIBILITY -> compatibilityStep(
            state = state.compatibility,
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

private fun permissionsStep(
    state: OnboardingState,
    actions: OnboardingActions,
    onNext: () -> Unit,
): WizardStep {
    val rows = permissionRows(
        sources = PermissionRowSources(
            shizuku = state.shizuku,
            permissions = state.permissions,
        ),
        onGrantPermission = actions.onGrantPermission,
        onShizukuAction = actions.onShizukuAction,
    )

    return WizardStep(
        title = "Permissions",
        content = {
            PermissionsStep(
                shizuku = state.shizuku,
                rows = rows,
                onShizukuAction = actions.onShizukuAction,
            )
        },
        primary = permissionsAction(rows = rows, actions = actions, onNext = onNext),
    )
}

private fun permissionsAction(
    rows: List<PermissionRowState>,
    actions: OnboardingActions,
    onNext: () -> Unit,
): WizardAction {
    if (rows.all { it.isGranted }) return WizardAction(label = "Continue", onClick = onNext)

    return WizardAction(label = "Grant permissions", onClick = actions.onRequestAllPermissions)
}

private fun compatibilityStep(
    state: CompatibilityState,
    actions: OnboardingActions,
) = WizardStep(
    title = "Compatibility",
    content = { CompatibilityStep(state) },
    primary = when {
        state.isCompatible -> WizardAction(label = "Finish", onClick = actions.onFinish)
        state.isOnFixPath -> fixPathAction(state, actions)

        else -> WizardAction(
            label = "Check",
            isEnabled = state !is CompatibilityState.Checking,
            onClick = actions.onCheckCompatibility,
        )
    },
)

private fun fixPathAction(
    state: CompatibilityState,
    actions: OnboardingActions,
): WizardAction = when (state) {
    is CompatibilityState.Downloaded ->
        WizardAction(label = "Install", onClick = actions.onInstallTetheringApex)

    is CompatibilityState.Installing ->
        WizardAction(label = "Installing", isEnabled = false, onClick = {})

    is CompatibilityState.Staged ->
        WizardAction(label = "Restart", onClick = actions.onRebootDevice)

    is CompatibilityState.InstallFailed ->
        WizardAction(label = "Check", onClick = actions.onCheckCompatibility)

    is CompatibilityState.DownloadFailed ->
        WizardAction(label = "Retry", onClick = actions.onDownloadTetheringApex)

    else -> WizardAction(
        label = "Download",
        isEnabled = state !is CompatibilityState.Downloading,
        onClick = actions.onDownloadTetheringApex,
    )
}
