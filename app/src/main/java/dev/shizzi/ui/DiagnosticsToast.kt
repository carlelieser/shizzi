package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.shizzi.DiagnosticsState
import dev.shizzi.R

@Composable
fun DiagnosticsToast(
    state: DiagnosticsState,
    toasts: ToastState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val current by rememberUpdatedState(state)
    val running = stringResource(R.string.running_diagnostics)
    val completed = stringResource(R.string.diagnostics_completed)
    val failed = stringResource(R.string.diagnostics_failed)
    val export = stringResource(R.string.export)

    LaunchedEffect(state) {
        val toast = when (val phase = state) {
            is DiagnosticsState.Idle -> {
                toasts.dismiss(ToastKeys.DIAGNOSTICS)
                return@LaunchedEffect
            }

            is DiagnosticsState.Running -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = running,
                duration = ToastDuration.Indefinite,
                isBusy = true,
            )

            is DiagnosticsState.Complete -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = completed,
                detail = phase.path,

                duration = ToastDuration.Indefinite,
                action = ToastAction(export) {
                    (current as? DiagnosticsState.Complete)
                        ?.let { context.exportReport(it.report) }
                },
                onDismiss = onDismiss,
            )

            is DiagnosticsState.Failed -> Toast(
                key = ToastKeys.DIAGNOSTICS,
                message = failed,
                detail = phase.problem,
                duration = ToastDuration.Indefinite,
                onDismiss = onDismiss,
            )
        }

        toasts.show(toast)
    }
}
