package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue

@Composable
fun ClearLogToast(
    isConfirming: Boolean,
    toasts: ToastState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {

    val confirm by rememberUpdatedState(onConfirm)
    val cancel by rememberUpdatedState(onCancel)

    LaunchedEffect(isConfirming) {
        if (!isConfirming) {
            toasts.dismiss(ToastKeys.CLEAR_LOG)
            return@LaunchedEffect
        }

        var isAnswered = false

        toasts.show(
            Toast(
                key = ToastKeys.CLEAR_LOG,
                message = str(R.string.clear_logs),
                detail = str(R.string.this_cannot_be_undone),

                duration = ToastDuration.Indefinite,
                action = ToastAction(str(R.string.action_clear)) {
                    isAnswered = true
                    confirm()
                },
                onDismiss = { if (!isAnswered) cancel() },
            ),
        )
    }
}

fun clearedToast(problem: String?): Toast = when (problem) {
    null -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = str(R.string.logs_cleared),
    )

    else -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = str(R.string.only_shizzi_logs_were_cleared),
        detail = problem,
        duration = ToastDuration.Indefinite,
    )
}
