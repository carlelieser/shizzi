package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.shizzi.R
import dev.shizzi.SessionError
import dev.shizzi.SessionUiState
import dev.shizzi.ShizukuState

@Composable
fun SessionToasts(
    state: SessionUiState,
    toasts: ToastState,
    onShizukuAction: () -> Unit,
) {
    ErrorToast(state, toasts)
    ShizukuToast(state.shizukuState, toasts, onShizukuAction)
}

@Composable
private fun ErrorToast(state: SessionUiState, toasts: ToastState) {
    val context = LocalContext.current
    val message = SessionError.describe(state.lastError).resolve()
    val hotspotSettings = stringResource(R.string.hotspot_settings)

    LaunchedEffect(state.lastError, state.isHotspotPossiblyOn) {
        if (state.lastError.isEmpty()) {
            toasts.dismiss(ToastKeys.SESSION)
            return@LaunchedEffect
        }

        val openSettings = ToastAction(hotspotSettings) { context.openTetherSettings() }

        toasts.show(
            Toast(
                key = ToastKeys.SESSION,
                message = message,
                duration = ToastDuration.Indefinite,
                action = openSettings.takeIf { state.isHotspotPossiblyOn },
            ),
        )
    }
}

@Composable
private fun ShizukuToast(
    state: ShizukuState,
    toasts: ToastState,
    onShizukuAction: () -> Unit,
) {
    val message = describe(state)
    val action = actionLabel(state)?.let { label -> ToastAction(label, onShizukuAction) }

    LaunchedEffect(state) {
        if (message.isEmpty()) {
            toasts.dismiss(ToastKeys.SHIZUKU)
            return@LaunchedEffect
        }

        toasts.show(
            Toast(
                key = ToastKeys.SHIZUKU,
                message = message,
                duration = ToastDuration.Indefinite,
                action = action,
            ),
        )
    }
}

@Composable
@ReadOnlyComposable
private fun describe(state: ShizukuState): String = when (state) {
    is ShizukuState.NotInstalled -> stringResource(R.string.shizuku_not_installed)
    is ShizukuState.NotRunning -> stringResource(R.string.shizuku_not_running)
    is ShizukuState.PermissionRequired -> stringResource(R.string.shizuku_permission_needed)
    is ShizukuState.Ready -> ""
}

@Composable
@ReadOnlyComposable
private fun actionLabel(state: ShizukuState): String? = when (state) {
    is ShizukuState.NotRunning -> stringResource(R.string.open_shizuku)
    is ShizukuState.PermissionRequired -> stringResource(R.string.grant)
    else -> null
}
