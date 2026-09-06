package dev.shizzi.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val ProgressDotSize = 10.dp

private val ActiveDotWidth = 28.dp

private const val StepSlideFraction = 6

private const val InactiveDotAlpha = 0.6f

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
fun Wizard(step: WizardStep, currentIndex: Int, stepCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(ScreenPadding),
    ) {
        StepContent(step = step, currentIndex = currentIndex, modifier = Modifier.weight(1f))

        ProgressDots(
            currentIndex = currentIndex,
            stepCount = stepCount,
            modifier = Modifier.padding(vertical = ShizziTheme.spacing.xl),
        )

        WizardFooter(primary = step.primary, secondary = step.secondary)
    }
}

/** Slides step content horizontally in the direction the wizard is travelling. */
@Composable
private fun StepContent(step: WizardStep, currentIndex: Int, modifier: Modifier = Modifier) {
    val slideSpec = standardTween<IntOffset>()
    val fadeSpec = standardTween<Float>()

    AnimatedContent(
        targetState = currentIndex,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            val shift = { width: Int -> width * direction / StepSlideFraction }

            val enter = slideInHorizontally(slideSpec, shift) + fadeIn(fadeSpec)
            val exit = slideOutHorizontally(slideSpec) { -shift(it) } + fadeOut(fadeSpec)

            enter togetherWith exit
        },
        label = "wizardStep",
    ) { _ ->
        Box(contentAlignment = Alignment.Center) {
            Column {
                if (step.title.isNotEmpty()) StepTitle(step.title)
                step.content()
            }
        }
    }
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
