package dev.shizzi.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.ShizukuState
import dev.shizzi.SessionUiState

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

    LaunchedEffect(state.lastError, state.isHotspotPossiblyOn) {
        if (state.lastError.isEmpty()) {
            toasts.dismiss(ToastKeys.SESSION)
            return@LaunchedEffect
        }

        toasts.show(
            Toast(
                key = ToastKeys.SESSION,
                message = state.lastError,
                duration = ToastDuration.Indefinite,
                action = hotspotSettingsAction(context).takeIf { state.isHotspotPossiblyOn },
            ),
        )
    }
}

private fun hotspotSettingsAction(context: Context) =
    ToastAction("Hotspot settings") { context.openTetherSettings() }

@Composable
private fun ShizukuToast(
    state: ShizukuState,
    toasts: ToastState,
    onShizukuAction: () -> Unit,
) {
    LaunchedEffect(state) {
        val message = describe(state)
        if (message.isEmpty()) {
            toasts.dismiss(ToastKeys.SHIZUKU)
            return@LaunchedEffect
        }

        toasts.show(
            Toast(
                key = ToastKeys.SHIZUKU,
                message = message,
                duration = ToastDuration.Indefinite,
                action = actionLabel(state)?.let { label -> ToastAction(label, onShizukuAction) },
            ),
        )
    }
}

private fun describe(state: ShizukuState): String = when (state) {
    is ShizukuState.NotInstalled -> "Shizuku is not installed"
    is ShizukuState.NotRunning -> "Shizuku is installed but not running"
    is ShizukuState.PermissionRequired -> "Shizuku permission is required"
    is ShizukuState.Ready -> ""
}

private fun actionLabel(state: ShizukuState): String? = when (state) {
    is ShizukuState.NotRunning -> "Open Shizuku"
    is ShizukuState.PermissionRequired -> "Grant"
    else -> null
}
