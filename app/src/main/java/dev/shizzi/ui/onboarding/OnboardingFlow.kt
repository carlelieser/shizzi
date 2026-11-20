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
    primary = WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.get_started), onClick = onNext),
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
        title = dev.shizzi.L10n.string(dev.shizzi.R.string.permissions),
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
    if (rows.all { it.isGranted }) return WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.continue_label), onClick = onNext)

    return WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.grant_permissions), onClick = actions.onRequestAllPermissions)
}

private fun compatibilityStep(
    state: CompatibilityState,
    actions: OnboardingActions,
) = WizardStep(
    title = dev.shizzi.L10n.string(dev.shizzi.R.string.compatibility),
    content = { CompatibilityStep(state) },
    primary = when {
        state.isCompatible -> WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.finish), onClick = actions.onFinish)
        state.isOnFixPath -> fixPathAction(state, actions)

        else -> WizardAction(
            label = dev.shizzi.L10n.string(dev.shizzi.R.string.check),
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
        WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.install), onClick = actions.onInstallTetheringApex)

    is CompatibilityState.Installing ->
        WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.installing), isEnabled = false, onClick = {})

    is CompatibilityState.Staged ->
        WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.restart), onClick = actions.onRebootDevice)

    is CompatibilityState.InstallFailed ->
        WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.check), onClick = actions.onCheckCompatibility)

    is CompatibilityState.DownloadFailed ->
        WizardAction(label = dev.shizzi.L10n.string(dev.shizzi.R.string.retry), onClick = actions.onDownloadTetheringApex)

    else -> WizardAction(
        label = dev.shizzi.L10n.string(dev.shizzi.R.string.download),
        isEnabled = state !is CompatibilityState.Downloading,
        onClick = actions.onDownloadTetheringApex,
    )
}
