package dev.shizzi.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import dev.shizzi.ui.theme.standardTween
import kotlinx.coroutines.flow.collect

enum class Screen(val depth: Int) { HOME(0), SETTINGS(1), LOG(2), EASTER_EGG(1) }
private const val SlideFraction = 6
private const val BackScaleDistance = 0.04f
private const val BackFadeDistance = 0.12f

private val ScreenSaver = Saver<MutableState<Screen>, String>(
    save = { it.value.name },
    restore = { mutableStateOf(runCatching { Screen.valueOf(it) }.getOrDefault(Screen.HOME)) },
)

@Composable fun rememberNavigator(): MutableState<Screen> =
    rememberSaveable(saver = ScreenSaver) { mutableStateOf(Screen.HOME) }

@Composable
fun PredictiveBackProgress(current: Screen, onBack: () -> Unit): Float {
    var progress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = current != Screen.HOME) { events ->
        try {
            events.collect { event -> progress = event.progress.coerceIn(0f, 1f) }
            onBack()
        } finally {
            progress = 0f
        }
    }
    return progress
}

@Composable
fun ScreenHost(
    current: Screen,
    backProgress: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit,
) {
    val slideSpec = standardTween<IntOffset>()
    val fadeSpec = standardTween<Float>()
    AnimatedContent(
        targetState = current,
        modifier = modifier.graphicsLayer {
            val p = backProgress.coerceIn(0f, 1f)
            scaleX = 1f - p * BackScaleDistance
            scaleY = 1f - p * BackScaleDistance
            alpha = 1f - p * BackFadeDistance
        },
        transitionSpec = {
            screenTransform(
                isForward = if (targetState.depth != initialState.depth) targetState.depth > initialState.depth else targetState.ordinal > initialState.ordinal,
                specs = TransitionSpecs(slideSpec, fadeSpec),
            )
        },
        label = "screen",
    ) { content(it) }
}

private data class TransitionSpecs(val slide: FiniteAnimationSpec<IntOffset>, val fade: FiniteAnimationSpec<Float>)
private fun screenTransform(isForward: Boolean, specs: TransitionSpecs): ContentTransform {
    val direction = if (isForward) 1 else -1
    val enter = slideInHorizontally(specs.slide) { direction * it / SlideFraction } + fadeIn(specs.fade)
    val exit = slideOutHorizontally(specs.slide) { -direction * it / SlideFraction } + fadeOut(specs.fade)
    return enter togetherWith exit
}
