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
import androidx.compose.ui.text.font.FontWeight
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
 * A fraction of the toast's width, so the gesture asks the same proportion on
 * any screen. Low enough for a flick, high enough that a thumb brushing
 * sideways during a tap does not.
 */
private const val DismissFraction = 0.35f

/**
 * Bottom-anchored and stacked upward, so the newest sits closest to the thumb
 * and older ones rise out of the way rather than shifting it under a finger
 * already moving toward it.
 *
 * Overlay this in a Box rather than nesting content: toasts float over the
 * screen and must not join its layout.
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
 * The timer is keyed on the message as well as the key, so a replacement gets
 * a full dwell rather than inheriting what was left of the message it replaced.
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
 * Drags sideways, dismissing past [DismissFraction].
 *
 * Not SwipeToDismissBox: that frames the gesture as revealing a background and
 * wraps the content in its own layout to draw it, so for a toast — nothing
 * behind it, and an offset shadow of its own to keep — the machinery would all
 * be spent being suppressed.
 *
 * Fades with distance so a partial drag shows the dismissal coming, and springs
 * back short of the threshold, which is what makes the fade read as progress.
 * Disabled while busy, matching the tap.
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

    // The handler is keyed on `isEnabled` alone, so without this it would
    // capture the callback from the composition that installed it.
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
            // An indefinite toast has no other way out. Not while busy, where
            // dismissing hides the only sign of work still in flight.
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
 * Mono throughout: a toast reports machine facts — an exception, a path, a
 * refusal from the framework — which this app sets in mono everywhere else.
 * `log` rather than `caption`, which is tracked and uppercase wherever it is
 * used and wrong for a path.
 *
 * The two lines separate by weight, not size. A second size in a surface this
 * small reads as two unrelated things stacked.
 */
@Composable
private fun ToastText(toast: Toast, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        // W500, the weight a settings row's name carries: message-to-detail is
        // the same relationship as name-to-description.
        Text(
            text = toast.message,
            style = ShizziTheme.typography.log.copy(fontWeight = FontWeight.W500),
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
 * Muted, matching the connect button's spinner: turquoise means a session is
 * up, and a spinner is the wait before anyone knows whether it will be.
 *
 * Indeterminate because a probe sequence has no measurable fraction complete —
 * it waits on upstream selection, which either settles or times out.
 */
@Composable
private fun ToastSpinner() {
    CircularProgressIndicator(
        color = ShizziTheme.colors.onSurfaceMuted,
        strokeWidth = SpinnerStroke,
        modifier = Modifier.size(SpinnerSize),
    )
}

/**
 * Text, not a second bordered box, which inside a bordered surface would read
 * as a box in a box — so weight carries the affordance instead.
 *
 * Not turquoise: the accent means a session is up, and a toast is already the
 * most prominent thing on screen without also taking the loudest colour.
 */
@Composable
private fun ToastActionButton(action: ToastAction, onDismiss: () -> Unit) {
    Text(
        text = action.label.uppercase(),
        style = ShizziTheme.typography.label.copy(fontWeight = FontWeight.W700),
        color = ShizziTheme.colors.onSurface,
        textAlign = TextAlign.End,
        modifier = Modifier
            .clickable {
                action.onClick()
                onDismiss()
            }
            .padding(ShizziTheme.spacing.sm),
    )
}
