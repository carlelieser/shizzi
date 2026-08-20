package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue

/**
 * Asks before emptying the log, then says what was emptied.
 *
 * A toast rather than a dialog. Nothing in this app dims the screen behind a
 * modal, and clearing a log is not grave enough to be the first thing that
 * does — but it is irreversible, so it does not happen on a single tap either.
 * The toast is the app's existing way to put a decision in front of someone
 * without taking the screen away from them: CLEAR confirms, and every other
 * way out of a toast — tapping it, swiping it, or waiting — cancels.
 *
 * That asymmetry is deliberate. The destructive path needs an explicit press;
 * the safe path is whatever happens by accident.
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

        // The host runs onDismiss on every exit, including the one the action
        // itself triggers — so a bare cancel() here would also fire on
        // confirm. This latch keeps the two answers exclusive rather than
        // relying on confirm() having already changed the state by the time
        // the dismiss lands.
        var isAnswered = false

        toasts.show(
            Toast(
                key = ToastKeys.CLEAR_LOG,
                message = "Clear the log?",
                detail = "This action cannot be undone.",
                // Indefinite: a question that timed out would be answered by
                // inattention. It leaves on an answer, and the answer to
                // ignoring it is no.
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
 * Reports what a clear actually managed to empty.
 *
 * Worth its own toast because the answer is not always "all of it". The log
 * lives in two files owned by two processes, and the shell's — which holds
 * most of it — is reachable only through Shizuku. If that call fails the app's
 * half is gone and the shell's is not, and a screen that said "Log cleared"
 * while still showing entries would be lying about which.
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
