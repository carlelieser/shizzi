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

/**
 * An optional button on the right edge of a toast.
 *
 * Present when the toast describes something the user can do about it — a
 * missing Shizuku permission is worth a GRANT button; a completed teardown is
 * not worth anything.
 */
@Immutable
data class ToastAction(val label: String, val onClick: () -> Unit)

/**
 * A message on screen.
 *
 * @param key identity for replacement. Posting a toast whose key matches one
 *   already showing updates it in place rather than stacking a second copy, so
 *   a state that changes repeatedly (the session going up, then down, then
 *   failing) occupies one slot rather than three.
 * @param detail a second line under the message, muted. For the specifics a
 *   headline should not carry — a file path, an interface name — which are
 *   worth stating exactly but not worth the weight of the first line.
 * @param isBusy draws a spinner in the leading position. Pairs with
 *   [ToastDuration.Indefinite]: a toast reporting work in progress is dismissed
 *   by the work finishing, not by a timer.
 * @param onDismiss run when the toast leaves the screen, however it leaves —
 *   tapped away, expired, or replaced by its action. For a toast whose content
 *   is owned elsewhere: without it, clearing the host would leave the state
 *   that produced the toast still set, and revisiting the screen would post the
 *   same stale result again.
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
 * Holds the toasts currently on screen.
 *
 * A list rather than a single slot because an error and a permission prompt are
 * independent facts, and hiding one behind the other loses information. Keyed
 * replacement is what keeps that list from growing without bound.
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
