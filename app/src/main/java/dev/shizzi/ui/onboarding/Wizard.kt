package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/** One dot per step. Sized to read as a marker rather than a tappable control. */
private val ProgressDotSize = 10.dp

/**
 * One screen of the wizard.
 *
 * A step says what it shows and what its buttons do; it does not place them.
 * The frame is [Wizard]'s, so a step added later cannot drift from the others
 * by laying out its own title or footer.
 *
 * [title] is empty for a step whose content is its own heading — Welcome sets
 * the word at display size as the thing being introduced, and a title above it
 * would be that word twice.
 *
 * [content] is given the width and left to fill it — the frame contributes the
 * screen padding, the title, and the space the footer occupies, nothing else.
 */
data class WizardStep(
    val title: String,
    val content: @Composable () -> Unit,
    val primary: WizardAction,
    val secondary: WizardAction? = null,
)

/**
 * A footer button.
 *
 * [isEnabled] rather than omitting a button that cannot yet be pressed: a
 * footer that gains a control when a check finishes moves the one the user was
 * reaching for.
 */
data class WizardAction(
    val label: String,
    val isEnabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Content, progress, footer — the same three bands on every step.
 *
 * The content band takes the remaining height rather than being centred in it,
 * so a step whose content grows (the compatibility list, as its checks report)
 * extends downward instead of shifting what is already on screen.
 */
@Composable
fun Wizard(step: WizardStep, currentIndex: Int, stepCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(ScreenPadding),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column {
                if (step.title.isNotEmpty()) StepTitle(step.title)
                step.content()
            }
        }

        ProgressDots(
            currentIndex = currentIndex,
            stepCount = stepCount,
            modifier = Modifier.padding(vertical = ShizziTheme.spacing.xl),
        )

        WizardFooter(primary = step.primary, secondary = step.secondary)
    }
}

/**
 * Filled for where the user is, outlined for everywhere else — the same
 * "this one is active" vocabulary [ThemePicker] uses, so the app has one.
 *
 * Steps behind the user are not marked as done: this wizard cannot be
 * navigated backward, so a third state would distinguish nothing actionable.
 */
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

/**
 * Square, like every other surface here. A circle would be the one round
 * element in an app whose corners are 0dp without exception.
 */
@Composable
private fun ProgressDot(isCurrent: Boolean) {
    val colors = ShizziTheme.colors

    Box(
        modifier = Modifier
            .size(ProgressDotSize)
            .brutalSurface(fill = if (isCurrent) colors.primary else Color.Transparent),
    )
}

/**
 * The primary sits above the secondary rather than beside it: the buttons are
 * full-width, and a row of two would halve the primary to give equal weight to
 * the lesser choice.
 */
@Composable
private fun WizardFooter(primary: WizardAction, secondary: WizardAction?) {
    Column(verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md)) {
        secondary?.let { action ->
            WizardButton(action = action, isPrimary = false)
        }

        WizardButton(action = primary, isPrimary = true)
    }
}
