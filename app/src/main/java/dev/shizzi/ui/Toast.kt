package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How long a toast stays up. Indefinite ones are dismissed by hand or replaced. */
@Immutable
sealed interface ToastDuration {
    data class Timed(val duration: Duration) : ToastDuration
    data object Indefinite : ToastDuration
}

/** The default dwell for informational toasts. */
val ToastShort = ToastDuration.Timed(4.seconds)

/** For a toast the user can act on — a missing permission is worth a GRANT. */
@Immutable
data class ToastAction(val label: String, val onClick: () -> Unit)

/**
 * A message on screen.
 *
 * @param key identity for replacement: a repeatedly changing state (session up,
 *   then down, then failed) occupies one slot rather than three.
 * @param detail a muted second line, for specifics a headline should not carry
 *   — a file path, an interface name.
 * @param isBusy draws a leading spinner. Pairs with [ToastDuration.Indefinite],
 *   since work in progress is ended by the work, not a timer.
 * @param onDismiss run however the toast leaves — tapped away, expired, or
 *   replaced by its action. For content owned elsewhere: without it the state
 *   that produced the toast stays set and posts the same stale result again.
 */
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

/** Keys for toasts that are posted from more than one place. */
object ToastKeys {
    /** Session lifecycle: starting, connected, stopped, failed. */
    const val SESSION = "session"

    /** Shizuku availability and permission. */
    const val SHIZUKU = "shizuku"

    /** A diagnostics run: in progress, then its outcome. */
    const val DIAGNOSTICS = "diagnostics"

    /** Clearing the log: the confirmation, then what it managed to clear. */
    const val CLEAR_LOG = "clear-log"
}

/**
 * A list rather than one slot: an error and a permission prompt are independent
 * facts, and hiding one behind the other loses information. Keyed replacement
 * is what bounds the list.
 */
class ToastState {
    private val entries = mutableStateListOf<Toast>()

    val toasts: List<Toast> get() = entries

    /** Adds [toast], or replaces the existing one with the same key in place. */
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
