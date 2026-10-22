package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

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
    onCancel: () -> Unit = onDismiss,
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
                message = str(R.string.running_diagnostics),
                duration = ToastDuration.Indefinite,
                isBusy = true,
                action = ToastAction(str(R.string.cancel_diagnostics), onCancel),
            )

            is DiagnosticsState.Complete -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = str(R.string.diagnostics_complete),
                detail = phase.path,

                duration = ToastDuration.Indefinite,
                action = ToastAction(str(R.string.action_export)) {
                    (current as? DiagnosticsState.Complete)
                        ?.let { context.exportReport(it.report) }
                },
                onDismiss = onDismiss,
            )

            is DiagnosticsState.Failed -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = str(R.string.diagnostics_failed),
                detail = phase.problem,
                duration = ToastDuration.Indefinite,
                onDismiss = onDismiss,
            )
        }

        toasts.show(toast)
    }
}
