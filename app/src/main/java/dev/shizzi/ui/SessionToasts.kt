package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.shizzi.ShizukuState
import dev.shizzi.SessionUiState

/**
 * Turns state into toasts.
 *
 * Kept out of the screens because all three of them float the same toast host,
 * and because "what deserves a message" is a product decision worth reading in
 * one place rather than inferring from scattered call sites.
 *
 * Only two things get announced: a session failure and a Shizuku problem the
 * user can act on. Success is visible in the status icon and the button label,
 * so toasting it would narrate what the screen already shows.
 */
@Composable
fun SessionToasts(
    state: SessionUiState,
    toasts: ToastState,
    onRequestPermission: () -> Unit,
) {
    ErrorToast(state.lastError, toasts)
    ShizukuToast(state.shizukuState, toasts, onRequestPermission)
}

/**
 * Indefinite: an error the user has not read is not one that should time out
 * while they are looking away from the phone they just tried to tether from.
 */
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

/**
 * The badge in the header says Shizuku is unavailable; this says what to do.
 *
 * Only the permission case carries an action, because it is the only one the
 * app can resolve — installing or starting Shizuku happens outside it.
 */
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

/** Phrased as the user's situation rather than the API's return value. */
private fun describe(state: ShizukuState): String = when (state) {
    is ShizukuState.NotInstalled -> "Shizuku is not installed"
    is ShizukuState.NotRunning -> "Shizuku is installed but not running"
    is ShizukuState.PermissionRequired -> "Shizzi needs permission from Shizuku"
    is ShizukuState.Ready -> ""
}
