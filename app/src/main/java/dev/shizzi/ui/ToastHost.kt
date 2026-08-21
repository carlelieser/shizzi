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

private val SpinnerSize = 18.dp

private val SpinnerStroke = 2.dp

private const val DismissFraction = 0.35f

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {

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

private fun Modifier.swipeToDismiss(
    isEnabled: Boolean,
    onDismiss: () -> Unit,
): Modifier = composed {

    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val dismiss by rememberUpdatedState(onDismiss)

    var width by remember { mutableIntStateOf(0) }

    if (!isEnabled) return@composed this

    this
        .onSizeChanged { width = it.width }
        .graphicsLayer {
            translationX = offset.value

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

@Composable
private fun ToastText(toast: Toast, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {

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

@Composable
private fun ToastSpinner() {
    CircularProgressIndicator(
        color = ShizziTheme.colors.onSurfaceMuted,
        strokeWidth = SpinnerStroke,
        modifier = Modifier.size(SpinnerSize),
    )
}

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
