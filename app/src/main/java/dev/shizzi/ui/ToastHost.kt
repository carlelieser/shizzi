package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import kotlin.math.abs

/** Matches the cap height of the message beside it, so the row reads as one line. */
private val SpinnerSize = 18.dp

/** Thin enough not to read as a second bordered element inside the toast. */
private val SpinnerStroke = 2.dp

/**
 * How far across a toast must be dragged to count as dismissed.
 *
 * A fraction of the toast's own width rather than a fixed distance, so the
 * gesture asks for the same proportion of a swipe on any screen. Low enough
 * that a flick clears it, high enough that a thumb brushing sideways during a
 * tap does not.
 */
private const val DismissFraction = 0.35f

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
        ToastSurface(
            toast = toast,
            onDismiss = onExpire,
            modifier = Modifier.swipeToDismiss(isEnabled = !toast.isBusy, onDismiss = onExpire),
        )
    }
}

/**
 * Drags the element sideways with a finger, dismissing past [DismissFraction].
 *
 * Written directly rather than with SwipeToDismissBox: that composable frames
 * the gesture as revealing a background — a delete action behind a list row —
 * and wraps the content in its own layout to draw it. A toast has nothing
 * behind it and needs to keep the offset shadow it draws itself, so the
 * machinery would all be spent being suppressed.
 *
 * Fades with distance, so a partial drag shows the dismissal coming rather
 * than only reporting it once the threshold is crossed. A gesture released
 * short of the threshold springs back, which is what makes the fade readable
 * as progress rather than as a glitch.
 *
 * Disabled while the toast is busy, matching the tap: work in flight should
 * not be swiped away any more than it should be tapped away.
 */
private fun Modifier.swipeToDismiss(
    isEnabled: Boolean,
    onDismiss: () -> Unit,
): Modifier = composed {
    // Every remember runs before the enabled check: a `composed` body is a
    // composable, so returning early past them would change the slot table's
    // shape when a toast goes from busy to finished.
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // The gesture handler is keyed on `isEnabled` alone, so it is not restarted
    // per recomposition; without this it would capture the callback from the
    // composition that installed it and go stale.
    val dismiss by rememberUpdatedState(onDismiss)

    var width by remember { mutableIntStateOf(0) }

    if (!isEnabled) return@composed this

    this
        .onSizeChanged { width = it.width }
        .graphicsLayer {
            translationX = offset.value
            // Fully opaque until the drag is underway, gone at the threshold.
            alpha = 1f - (abs(offset.value) / (width * DismissFraction)).coerceIn(0f, 1f)
        }
        .pointerInput(isEnabled) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val threshold = width * DismissFraction
                    when {
                        abs(offset.value) >= threshold -> dismiss()
                        else -> scope.launch { offset.animateTo(0f) }
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, dragAmount ->
                change.consume()
                scope.launch { offset.snapTo(offset.value + dragAmount) }
            }
        }
}

@Composable
private fun ToastSurface(toast: Toast, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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
