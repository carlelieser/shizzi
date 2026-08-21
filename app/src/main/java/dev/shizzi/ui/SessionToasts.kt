package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.shizzi.ShizukuState
import dev.shizzi.SessionUiState

@Composable
fun SessionToasts(
    state: SessionUiState,
    toasts: ToastState,
    onRequestPermission: () -> Unit,
) {
    ErrorToast(state.lastError, toasts)
    ShizukuToast(state.shizukuState, toasts, onRequestPermission)
}

@Composable
private fun ErrorToast(lastError: String, toasts: ToastState) {
    LaunchedEffect(lastError) {
        if (lastError.isEmpty()) {
            toasts.dismiss(ToastKeys.SESSION)
            return@LaunchedEffect
        }

        toasts.show(
            Toast(
                key = ToastKeys.SESSION,
                message = lastError,
                duration = ToastDuration.Indefinite,
            ),
        )
    }
}

@Composable
private fun ShizukuToast(
    state: ShizukuState,
    toasts: ToastState,
    onRequestPermission: () -> Unit,
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
                action = ToastAction("Grant", onRequestPermission)
                    .takeIf { state is ShizukuState.PermissionRequired },
            ),
        )
    }
}

private fun describe(state: ShizukuState): String = when (state) {
    is ShizukuState.NotInstalled -> "Shizuku is not installed"
    is ShizukuState.NotRunning -> "Shizuku is installed but not running"
    is ShizukuState.PermissionRequired -> "Shizzi needs permission from Shizuku"
    is ShizukuState.Ready -> ""
}
