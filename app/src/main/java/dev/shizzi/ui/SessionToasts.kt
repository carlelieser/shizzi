package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import dev.shizzi.R
import dev.shizzi.SessionUiState
import dev.shizzi.ShizukuState
import dev.shizzi.SessionError

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
    val message = SessionError.describe(lastError).resolve()

    LaunchedEffect(lastError) {
        if (lastError.isEmpty()) {
            toasts.dismiss(ToastKeys.SESSION)
            return@LaunchedEffect
        }

        toasts.show(
            Toast(
                key = ToastKeys.SESSION,
                message = message,
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
    val message = describe(state)
    val grant = stringResource(R.string.grant)

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
                action = ToastAction(grant, onRequestPermission)
                    .takeIf { state is ShizukuState.PermissionRequired },
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
