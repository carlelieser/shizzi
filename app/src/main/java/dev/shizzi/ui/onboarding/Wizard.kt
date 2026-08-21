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

private val ProgressDotSize = 10.dp

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
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
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

@Composable
private fun ProgressDot(isCurrent: Boolean) {
    val colors = ShizziTheme.colors

    Box(
        modifier = Modifier
            .size(ProgressDotSize)
            .brutalSurface(fill = if (isCurrent) colors.primary else Color.Transparent),
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
