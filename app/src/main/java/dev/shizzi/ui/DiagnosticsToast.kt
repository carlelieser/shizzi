package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.DiagnosticsState

/**
 * Reports a diagnostics run, from the moment it starts to what it produced.
 *
 * One toast across all three phases, keyed so each replaces the last in place:
 * a run is one event, and stacking "running" under "complete" would leave the
 * screen claiming both at once.
 *
 * Kept beside [SessionToasts] rather than inside it. That one derives from
 * session state, which a probe run deliberately does not touch — the two are
 * independent facts and can be on screen together.
 */
@Composable
fun DiagnosticsToast(
    state: DiagnosticsState,
    toasts: ToastState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // The result is read when EXPORT is pressed, which can be after this
    // composable has recomposed with a newer state. Capturing it in the lambda
    // directly would export whatever the state was when the toast was built.
    val current by rememberUpdatedState(state)

    LaunchedEffect(state) {
        val toast = when (val phase = state) {
            is DiagnosticsState.Idle -> {
                toasts.dismiss(ToastKeys.DIAGNOSTICS)
                return@LaunchedEffect
            }

            // Indefinite and undismissable: it ends when the run does.
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
                // Indefinite: the export button is the reason this toast
                // exists, and a four-second window to notice and reach it
                // would make the action decorative.
                duration = ToastDuration.Indefinite,
                action = ToastAction("Export") {
                    (current as? DiagnosticsState.Complete)
                        ?.let { context.exportReport(it.report) }
                },
                onDismiss = onDismiss,
            )

            // No export: a run that failed produced no report to export, and
            // the reason is already in the log.
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
