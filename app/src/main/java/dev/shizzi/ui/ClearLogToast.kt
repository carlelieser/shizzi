package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue

/**
 * Asks before emptying the log.
 *
 * A toast, not a dialog: nothing here dims the screen behind a modal, and
 * clearing a log is not grave enough to be the first thing that does — but it
 * is irreversible, so not a single tap either. CLEAR confirms; every other way
 * out of a toast cancels. The destructive path needs an explicit press, the
 * safe one is whatever happens by accident.
 */
@Composable
fun ClearLogToast(
    isConfirming: Boolean,
    toasts: ToastState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    // Read when CLEAR is pressed, which may be several recompositions after
    // the toast was built.
    val confirm by rememberUpdatedState(onConfirm)
    val cancel by rememberUpdatedState(onCancel)

    LaunchedEffect(isConfirming) {
        if (!isConfirming) {
            toasts.dismiss(ToastKeys.CLEAR_LOG)
            return@LaunchedEffect
        }

        // The host runs onDismiss on every exit, the action's own included, so
        // a bare cancel() here would also fire on confirm.
        var isAnswered = false

        toasts.show(
            Toast(
                key = ToastKeys.CLEAR_LOG,
                message = "Clear the log?",
                detail = "This action cannot be undone.",
                // A question that timed out would be answered by inattention.
                duration = ToastDuration.Indefinite,
                action = ToastAction("Clear") {
                    isAnswered = true
                    confirm()
                },
                onDismiss = { if (!isAnswered) cancel() },
            ),
        )
    }
}

/**
 * Reports what a clear actually emptied, which is not always all of it: the
 * shell's file holds most of the log and is reachable only through Shizuku, so
 * a failed call leaves entries that "Log cleared" would be lying about.
 */
fun clearedToast(problem: String?): Toast = when (problem) {
    null -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = "Log cleared",
    )

    else -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = "Cleared this app's entries only",
        detail = problem,
        duration = ToastDuration.Indefinite,
    )
}
