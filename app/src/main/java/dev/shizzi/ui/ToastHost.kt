package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface
import kotlinx.coroutines.delay
import androidx.compose.material3.Text

/** Matches the cap height of the message beside it, so the row reads as one line. */
private val SpinnerSize = 18.dp

/** Thin enough not to read as a second bordered element inside the toast. */
private val SpinnerStroke = 2.dp

/**
 * Renders the toast stack above whatever screen is showing.
 *
 * Bottom-anchored and stacked upward, so the newest toast sits closest to the
 * thumb and older ones rise out of the way rather than shifting the newest one
 * around under a finger already moving toward it.
 *
 * Callers overlay this in a Box rather than nesting content inside it: toasts
 * float over the screen and must not participate in its layout.
 */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        // Reversed so the newest is drawn last, at the bottom of the column.
        state.toasts.asReversed().forEach { toast ->
            ToastRow(
                toast = toast,
                onExpire = {
                    state.dismiss(toast.key)
                    toast.onDismiss?.invoke()
                },
            )
        }
    }
}

/**
 * One toast, with its own expiry timer.
 *
 * The timer is keyed on the message as well as the key, so replacing a toast in
 * place restarts its dwell rather than letting the replacement inherit the
 * remaining time of the message it replaced.
 */
@Composable
private fun ToastRow(toast: Toast, onExpire: () -> Unit) {
    val duration = toast.duration
    if (duration is ToastDuration.Timed) {
        LaunchedEffect(toast.key, toast.message) {
            delay(duration.duration)
            onExpire()
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        ToastSurface(toast = toast, onDismiss = onExpire)
    }
}

@Composable
private fun ToastSurface(toast: Toast, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalSurface(fill = ShizziTheme.colors.surface)
            // Tapping the body dismisses. An indefinite toast has no other way
            // out, and a timed one is often read before it expires.
            //
            // Not while busy: the toast is reporting work that is still
            // running, and dismissing it would hide the only indication that
            // anything is happening while leaving the work in flight.
            .clickable(enabled = !toast.isBusy, onClick = onDismiss)
            .padding(ShizziTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        if (toast.isBusy) ToastSpinner()

        ToastText(toast = toast, modifier = Modifier.weight(1f))

        toast.action?.let { action ->
            ToastActionButton(action = action, onDismiss = onDismiss)
        }
    }
}

/**
 * The message, and under it the detail if there is one.
 *
 * Mono throughout. A toast reports machine facts — an exception, a path, a
 * refusal from the framework — and those are the strings this app sets in mono
 * everywhere else it shows them. `body` is Inter, which put the same text in
 * prose type here and in mono on the log screen.
 *
 * The detail takes `log` rather than `caption`: both are mono, but caption is
 * tracked and uppercase wherever it is used, which is the wrong treatment for a
 * path. `log` is what the log screen already renders these in.
 */
@Composable
private fun ToastText(toast: Toast, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        Text(
            text = toast.message,
            style = ShizziTheme.typography.log,
            color = ShizziTheme.colors.onSurface,
        )

        if (toast.detail.isEmpty()) return@Column

        Text(
            text = toast.detail,
            style = ShizziTheme.typography.log,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}

/**
 * Turquoise, like every other element that means something is being worked on.
 *
 * A determinate bar would be the better shape if the work reported progress,
 * but a probe sequence has no measurable fraction complete — it waits on
 * upstream selection, which either settles or times out.
 */
@Composable
private fun ToastSpinner() {
    CircularProgressIndicator(
        color = ShizziTheme.colors.primary,
        strokeWidth = SpinnerStroke,
        modifier = Modifier.size(SpinnerSize),
    )
}

/**
 * The action label, styled as text rather than as a second bordered box.
 *
 * A button inside a bordered surface would nest two of the same treatment and
 * read as a box in a box; turquoise on the label carries the affordance.
 */
@Composable
private fun ToastActionButton(action: ToastAction, onDismiss: () -> Unit) {
    Text(
        text = action.label.uppercase(),
        style = ShizziTheme.typography.label,
        color = ShizziTheme.colors.primary,
        textAlign = TextAlign.End,
        modifier = Modifier
            .clickable {
                action.onClick()
                onDismiss()
            }
            .padding(ShizziTheme.spacing.sm),
    )
}
