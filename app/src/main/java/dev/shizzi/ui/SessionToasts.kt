package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.shizzi.SessionUiState

@Composable
fun SessionToasts(state: SessionUiState, toasts: ToastState) {
    LaunchedEffect(state.lastError) {
        if (state.lastError.isEmpty()) {
            toasts.dismiss(ToastKeys.SESSION)
        } else {
            toasts.show(Toast(ToastKeys.SESSION, state.lastError, duration = ToastDuration.Indefinite))
        }
    }
}
