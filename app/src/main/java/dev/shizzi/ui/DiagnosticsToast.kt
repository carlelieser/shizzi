package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.DiagnosticsState

/**
 * One toast across all three phases, keyed so each replaces the last: a run is
 * one event, and stacking "running" under "complete" claims both at once.
 *
 * Beside [SessionToasts] rather than inside it, since that derives from session
 * state — which a probe run does not touch, and both can be on screen together.
 */
@Composable
fun DiagnosticsToast(
    state: DiagnosticsState,
    toasts: ToastState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // EXPORT may be pressed several recompositions later; capturing directly
    // would export the state from when the toast was built.
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
                // The export button is why this toast exists; four seconds to
                // notice and reach it would make the action decorative.
                duration = ToastDuration.Indefinite,
                action = ToastAction("Export") {
                    (current as? DiagnosticsState.Complete)
                        ?.let { context.exportReport(it.report) }
                },
                onDismiss = onDismiss,
            )

            // A failed run produced no report, and the reason is in the log.
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
