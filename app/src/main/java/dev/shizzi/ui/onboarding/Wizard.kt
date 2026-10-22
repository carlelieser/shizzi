package dev.shizzi.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.SurfaceElevation
import dev.shizzi.ui.theme.emphasizedSpring
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween
import dev.shizzi.ui.theme.themedSurface
import kotlinx.coroutines.launch

private val ProgressDotSize = 10.dp

private val ActiveDotWidth = 28.dp

private const val StepSlideFraction = 6

private const val InactiveDotAlpha = 0.6f

// A swipe flips the step once the content has travelled this share of the width.
private const val SwipeFraction = 0.25f

// An edge step cannot flip, so its drag resists with damping instead of following.
private const val BlockedDragDamping = 0.15f

data class WizardStep(
    val title: String,
    val content: @Composable () -> Unit,
    val primary: WizardAction,
    val secondary: WizardAction? = null,
)

data class WizardAction(
    val label: String,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun Wizard(
    step: WizardStep,
    currentIndex: Int,
    stepCount: Int,
    onSwipe: (delta: Int) -> Unit = {},
) {
    val canSwipeNext = currentIndex < stepCount - 1
    val canSwipeBack = currentIndex > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(ScreenPadding),
        ) {
            StepContent(
                state = StepContentState(step = step, index = currentIndex),
                canSwipeNext = canSwipeNext,
                canSwipeBack = canSwipeBack,
                onSwipe = onSwipe,
                modifier = Modifier.weight(1f),
            )

            ProgressDots(
                currentIndex = currentIndex,
                stepCount = stepCount,
                modifier = Modifier.padding(vertical = ShizziTheme.spacing.xl),
            )

            WizardFooter(primary = step.primary, secondary = step.secondary)
        }
    }
}

@Immutable
private data class StepContentState(val step: WizardStep, val index: Int)

/**
 * Slides step content horizontally in the direction the wizard is travelling.
 * Each layer renders the step it captured, so the outgoing content stays put
 * for the length of the transition.
 */
@Composable
private fun StepContent(
    state: StepContentState,
    canSwipeNext: Boolean,
    canSwipeBack: Boolean,
    onSwipe: (delta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slideSpec = standardTween<IntOffset>()
    val fadeSpec = standardTween<Float>()

    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = { it.index },
        transitionSpec = {
            val direction = if (targetState.index > initialState.index) 1 else -1
            val shift = { width: Int -> width * direction / StepSlideFraction }

            val enter = slideInHorizontally(slideSpec, shift) + fadeIn(fadeSpec)
            val exit = slideOutHorizontally(slideSpec) { -shift(it) } + fadeOut(fadeSpec)

            enter togetherWith exit
        },
        label = "wizardStep",
    ) { target ->
        Box(contentAlignment = Alignment.Center) {
            SwipeableStep(
                canSwipeNext = canSwipeNext,
                canSwipeBack = canSwipeBack,
                onSwipe = onSwipe,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    if (target.step.title.isNotEmpty()) StepTitle(target.step.title)
                    target.step.content()
                }
            }
        }
    }
}

/**
 * Steps swipe horizontally like pages: dragging follows the finger, releasing
 * past the swipe threshold flips the step, and dragging on an edge step resists
 * with damping instead of following. The vertical scroll inside a step is left
 * untouched because the drag detector only claims horizontally dominant moves.
 */
@Composable
private fun SwipeableStep(
    canSwipeNext: Boolean,
    canSwipeBack: Boolean,
    onSwipe: (delta: Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }

    // The gesture callbacks leave composable context, so the spring they
    // settle with is resolved up front.
    val resetSpec = standardSpring<Float>()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .graphicsLayer { translationX = offset.value }
            .pointerInput(canSwipeNext, canSwipeBack) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val travelled = offset.value
                        val delta = when {
                            canSwipeNext && width > 0 && travelled <= -width * SwipeFraction -> 1
                            canSwipeBack && width > 0 && travelled >= width * SwipeFraction -> -1
                            else -> 0
                        }
                        if (delta != 0) onSwipe(delta)
                        scope.launch { offset.animateTo(0f, resetSpec) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, resetSpec) } },
                ) { change, dragAmount ->
                    change.consume()
                    // Leftward drag reads as "go to the next step"; a direction
                    // the wizard cannot take damps the drag instead of following.
                    val draggingForward = dragAmount < 0f
                    val allowed = (draggingForward && canSwipeNext) || (!draggingForward && canSwipeBack)
                    val damped = offset.value + dragAmount * if (allowed) 1f else BlockedDragDamping
                    scope.launch { offset.snapTo(damped) }
                }
            },
    ) { content() }
}

@Composable
private fun ProgressDots(currentIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = ShizziTheme.spacing.sm,
            alignment = Alignment.CenterHorizontally,
        ),
    ) {
        repeat(stepCount) { index ->
            ProgressDot(isCurrent = index == currentIndex)
        }
    }
}

// Neobrutalism renders an inactive dot as an outline, which its border supplies,
// and casts the active dot into its own shadow. Expressive draws neither, so an
// inactive dot is drawn from the muted foreground: the container roles track the
// background too closely to read against it, and a dot this small casts no shadow.
@Composable
private fun ProgressDot(isCurrent: Boolean) {
    val colors = ShizziTheme.colors
    val isBrutal = ShizziTheme.design == DesignLanguage.NEOBRUTALISM
    val inactiveFill = when {
        isBrutal -> Color.Transparent
        else -> colors.onSurfaceMuted.copy(alpha = InactiveDotAlpha)
    }
    val hasShadow = isBrutal && isCurrent

    val fill by animateColorAsState(
        targetValue = if (isCurrent) colors.primary else inactiveFill,
        animationSpec = standardSpring(),
        label = "dotFill",
    )

    val width by animateDpAsState(
        targetValue = if (isCurrent) ActiveDotWidth else ProgressDotSize,
        animationSpec = emphasizedSpring(),
        label = "dotWidth",
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(ProgressDotSize)
            .themedSurface(
                fill = fill,
                elevation = if (hasShadow) SurfaceElevation.RAISED else SurfaceElevation.FLAT,
            ),
    )
}

@Composable
private fun WizardFooter(primary: WizardAction, secondary: WizardAction?) {
    Column(verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md)) {
        secondary?.let { action ->
            WizardButton(action = action, isPrimary = false)
        }

        WizardButton(action = primary, isPrimary = true)
    }
}
