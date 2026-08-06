package dev.shizzi.spike.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.shizzi.spike.ui.theme.ScreenPadding
import dev.shizzi.spike.ui.theme.ShizziTheme
import dev.shizzi.spike.ui.theme.brutalSurface
import kotlinx.coroutines.delay
import androidx.compose.material3.Text

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
            ToastRow(toast = toast, onExpire = { state.dismiss(toast.key) })
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
            .clickable(onClick = onDismiss)
            .padding(ShizziTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Text(
            text = toast.message,
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )

        toast.action?.let { action ->
            ToastActionButton(action = action, onDismiss = onDismiss)
        }
    }
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
