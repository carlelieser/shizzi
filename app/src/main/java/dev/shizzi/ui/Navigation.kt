package dev.shizzi.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import dev.shizzi.ui.theme.fastTween
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween

/** [depth] orders screens so a transition can derive its own direction. */
enum class Screen(val depth: Int) {
    HOME(0),
    SETTINGS(1),
    LOG(2),
    EASTER_EGG(1),
}

private const val OutgoingShiftFraction = 4

private val ScreenSaver = Saver<MutableState<Screen>, String>(
    save = { it.value.name },
    restore = { name ->
        mutableStateOf(runCatching { Screen.valueOf(name) }.getOrDefault(Screen.HOME))
    },
)

@Composable
fun rememberNavigator(): MutableState<Screen> =
    rememberSaveable(saver = ScreenSaver) { mutableStateOf(Screen.HOME) }

@Composable
fun HandleBack(current: Screen, onBack: () -> Unit) {
    BackHandler(enabled = current != Screen.HOME, onBack = onBack)
}

/**
 * Cross-fades between screens, sliding in the direction implied by their depth:
 * a deeper screen enters from the trailing edge, a shallower one from the leading.
 */
@Composable
fun ScreenHost(
    current: Screen,
    modifier: Modifier = Modifier,
    content: @Composable (Screen) -> Unit,
) {
    val slideSpec = standardSpring<IntOffset>()
    val enterFade = fastTween<Float>()
    val exitFade = standardTween<Float>()

    AnimatedContent(
        targetState = current,
        modifier = modifier,
        transitionSpec = {
            screenTransform(
                isForward = isDescending(initialState, targetState),
                specs = TransitionSpecs(slideSpec, enterFade, exitFade),
            )
        },
        label = "screen",
    ) { screen ->
        content(screen)
    }
}

private data class TransitionSpecs(
    val slide: FiniteAnimationSpec<IntOffset>,
    val enterFade: FiniteAnimationSpec<Float>,
    val exitFade: FiniteAnimationSpec<Float>,
)

/** Equal depths sit side by side, so ordinal breaks the tie consistently. */
private fun isDescending(from: Screen, to: Screen): Boolean = when {
    to.depth != from.depth -> to.depth > from.depth
    else -> to.ordinal > from.ordinal
}

private fun screenTransform(isForward: Boolean, specs: TransitionSpecs): ContentTransform {
    val direction = if (isForward) 1 else -1

    val enter = slideInHorizontally(animationSpec = specs.slide) { width ->
        direction * width
    } + fadeIn(animationSpec = specs.enterFade)

    val exit = slideOutHorizontally(animationSpec = specs.slide) { width ->
        -direction * width / OutgoingShiftFraction
    } + fadeOut(animationSpec = specs.exitFade)

    return enter togetherWith exit
}
