package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Immutable
sealed interface ToastDuration {
    data class Timed(val duration: Duration) : ToastDuration
    data object Indefinite : ToastDuration
}

val ToastShort = ToastDuration.Timed(4.seconds)

@Immutable
data class ToastAction(val label: String, val onClick: () -> Unit)

@Immutable
data class Toast(
    val key: String,
    val message: String,
    val detail: String = "",
    val duration: ToastDuration = ToastShort,
    val action: ToastAction? = null,
    val isBusy: Boolean = false,
    val onDismiss: (() -> Unit)? = null,
)

object ToastKeys {

    const val SESSION = "session"

    const val SHIZUKU = "shizuku"

    const val DIAGNOSTICS = "diagnostics"

    const val CLEAR_LOG = "clear-log"
}

class ToastState {
    private val entries = mutableStateListOf<Toast>()

    val toasts: List<Toast> get() = entries

    fun show(toast: Toast) {
        val existing = entries.indexOfFirst { it.key == toast.key }
        if (existing >= 0) entries[existing] = toast else entries.add(toast)
    }

    fun dismiss(key: String) {
        entries.removeAll { it.key == key }
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }
