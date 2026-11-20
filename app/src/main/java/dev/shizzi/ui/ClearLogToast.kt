package dev.shizzi.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import dev.shizzi.R

@Composable
fun ClearLogToast(
    isConfirming: Boolean,
    toasts: ToastState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {

    val confirm by rememberUpdatedState(onConfirm)
    val cancel by rememberUpdatedState(onCancel)
    val question = stringResource(R.string.clear_log_question)
    val warning = stringResource(R.string.clear_log_warning)
    val clear = stringResource(R.string.clear)

    LaunchedEffect(isConfirming) {
        if (!isConfirming) {
            toasts.dismiss(ToastKeys.CLEAR_LOG)
            return@LaunchedEffect
        }

        var isAnswered = false

        toasts.show(
            Toast(
                key = ToastKeys.CLEAR_LOG,
                message = question,
                detail = warning,

                duration = ToastDuration.Indefinite,
                action = ToastAction(clear) {
                    isAnswered = true
                    confirm()
                },
                onDismiss = { if (!isAnswered) cancel() },
            ),
        )
    }
}

fun clearedToast(resources: Resources, problem: String?): Toast = when (problem) {
    null -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = resources.getString(R.string.log_cleared),
    )

    else -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = resources.getString(R.string.app_log_entries_cleared),
        detail = problem,
        duration = ToastDuration.Indefinite,
    )
}
