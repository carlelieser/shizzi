package dev.shizzi.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import dev.shizzi.AppPermission
import dev.shizzi.CompatibilityState
import dev.shizzi.PrivilegeBackendType
import dev.shizzi.PrivilegeState
import dev.shizzi.R
import dev.shizzi.isCompatible
import dev.shizzi.isOnFixPath
import dev.shizzi.str
import dev.shizzi.ui.PermissionRowState
import dev.shizzi.ui.PrivilegeAccessActions
import dev.shizzi.ui.permissionRows

enum class OnboardingStep { WELCOME, PERMISSIONS, COMPATIBILITY }

data class OnboardingActions(
    val onRequestAllPermissions: () -> Unit,
    val onGrantPermission: (AppPermission) -> Unit,
    val onSelectPrivilegeBackend: (PrivilegeBackendType) -> Unit,
    val onShizukuAction: () -> Unit,
    val onStartLocalAdbPairing: () -> Unit,
    val onCancelLocalAdbPairing: () -> Unit,
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
        OnboardingStep.WELCOME -> welcomeStep { current.value = OnboardingStep.PERMISSIONS }
        OnboardingStep.PERMISSIONS -> permissionsStep(state, actions) { current.value = OnboardingStep.COMPATIBILITY }
        OnboardingStep.COMPATIBILITY -> compatibilityStep(state.compatibility, actions)
    }
    Wizard(
        step = step,
        currentIndex = current.value.ordinal,
        stepCount = OnboardingStep.entries.size,
        onSwipe = { delta ->
            val entries = OnboardingStep.entries
            val target = (current.value.ordinal + delta).coerceIn(0, entries.lastIndex)
            current.value = entries[target]
        },
    )
}

@Composable private fun rememberOnboardingStep(): MutableState<OnboardingStep> =
    rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }

private fun welcomeStep(onNext: () -> Unit) = WizardStep(
    title = "", content = { WelcomeStep() }, primary = WizardAction(str(R.string.get_started), onClick = onNext),
)

private fun permissionsStep(state: OnboardingState, actions: OnboardingActions, onNext: () -> Unit): WizardStep {
    val rows = permissionRows(state.permissions, actions.onGrantPermission)
    val privilegeActions = PrivilegeAccessActions(
        onSelectBackend = actions.onSelectPrivilegeBackend,
        onShizukuAction = actions.onShizukuAction,
        onStartLocalAdbPairing = actions.onStartLocalAdbPairing,
        onCancelLocalAdbPairing = actions.onCancelLocalAdbPairing,
    )
    return WizardStep(
        title = str(R.string.permissions),
        content = { PermissionsStep(state.backend, state.privilegeState, rows, privilegeActions) },
        primary = permissionsAction(rows, state.privilegeState, actions, onNext),
    )
}

private fun permissionsAction(
    rows: List<PermissionRowState>,
    privilegeState: PrivilegeState,
    actions: OnboardingActions,
    onNext: () -> Unit,
): WizardAction {
    if (rows.all { it.isGranted } && privilegeState is PrivilegeState.Ready) {
        return WizardAction(str(R.string.action_continue), onClick = onNext)
    }
    return WizardAction(str(R.string.grant_permission), onClick = actions.onRequestAllPermissions)
}

private fun compatibilityStep(state: CompatibilityState, actions: OnboardingActions) = WizardStep(
    title = str(R.string.compatibility),
    content = { CompatibilityStep(state) },
    primary = when {
        state.isCompatible -> WizardAction(str(R.string.action_finish), onClick = actions.onFinish)
        state.isOnFixPath -> fixPathAction(state, actions)
        else -> WizardAction(str(R.string.action_check), state !is CompatibilityState.Checking, actions.onCheckCompatibility)
    },
)

private fun fixPathAction(state: CompatibilityState, actions: OnboardingActions): WizardAction = when (state) {
    is CompatibilityState.Downloaded -> WizardAction(str(R.string.action_install), onClick = actions.onInstallTetheringApex)
    is CompatibilityState.Installing -> WizardAction(str(R.string.installing), isEnabled = false, onClick = {})
    is CompatibilityState.Staged -> WizardAction(str(R.string.action_reboot), onClick = actions.onRebootDevice)
    is CompatibilityState.InstallFailed -> WizardAction(str(R.string.action_check), onClick = actions.onCheckCompatibility)
    is CompatibilityState.DownloadFailed -> WizardAction(str(R.string.action_retry), onClick = actions.onDownloadTetheringApex)
    else -> WizardAction(str(R.string.action_download), state !is CompatibilityState.Downloading, actions.onDownloadTetheringApex)
}
