package dev.shizzi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.HeaderHeight
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.emphasizedSpring

private val HeaderRule = 1.dp

/** Draws the rule across on entry so the header resolves rather than appearing. */
@Composable
private fun rememberRuleExtent(hasRule: Boolean): Float {
    var hasDrawn by remember { mutableStateOf(false) }

    val extent by animateFloatAsState(
        targetValue = if (hasDrawn) 1f else 0f,
        animationSpec = emphasizedSpring(),
        label = "headerRule",
    )

    LaunchedEffect(hasRule) { hasDrawn = true }

    return extent
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit = {},
) {
    val border = ShizziTheme.colors.border
    val hasRule = ShizziTheme.design == DesignLanguage.NEOBRUTALISM
    val ruleExtent = rememberRuleExtent(hasRule)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)

            .drawBehind {
                if (!hasRule) return@drawBehind

                val thickness = HeaderRule.toPx()
                val baseline = size.height - thickness / 2f
                drawLine(
                    color = border,
                    start = Offset(0f, baseline),
                    end = Offset(size.width * ruleExtent, baseline),
                    strokeWidth = thickness,
                )
            }
            .padding(horizontal = ShizziTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        BackButton(onBack)

        Text(
            text = title,
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )

        action()
        Spacer(Modifier.width(ShizziTheme.spacing.xs))
    }
}
