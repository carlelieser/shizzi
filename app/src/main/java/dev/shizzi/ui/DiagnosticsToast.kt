package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.DiagnosticsState

@Composable
fun DiagnosticsToast(
    state: DiagnosticsState,
    toasts: ToastState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val current by rememberUpdatedState(state)

    LaunchedEffect(state) {
        val toast = when (val phase = state) {
            is DiagnosticsState.Idle -> {
                toasts.dismiss(ToastKeys.DIAGNOSTICS)
                return@LaunchedEffect
            }

            is DiagnosticsState.Running -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = "Running diagnostics...",
                duration = ToastDuration.Indefinite,
                isBusy = true,
            )

            is DiagnosticsState.Complete -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = "Diagnostics completed",
                detail = phase.path,

                duration = ToastDuration.Indefinite,
                action = ToastAction("Export") {
                    (current as? DiagnosticsState.Complete)
                        ?.let { context.exportReport(it.report) }
                },
                onDismiss = onDismiss,
            )

            is DiagnosticsState.Failed -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = "Diagnostics failed",
                detail = phase.problem,
                duration = ToastDuration.Indefinite,
                onDismiss = onDismiss,
            )
        }

        toasts.show(toast)
    }
}
