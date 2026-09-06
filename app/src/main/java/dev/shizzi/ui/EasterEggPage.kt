package dev.shizzi.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import kotlin.math.ceil

private val IconSize = 56.dp
private val IconSpacing = 40.dp
private val ColumnSpacing = 40.dp

/** Extra cells past the viewport, split between the leading and trailing edge. */
private const val BleedCells = 2

private const val FlipDurationMillis = 900
private const val FlipHoldMillis = 1000

/** Column A alternates edge-on; column B alternates upright. */
private val ColumnAngles = listOf(
    listOf(-90f, 90f),
    listOf(0f, 180f),
)

@Composable
fun EasterEggPage(onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShizziTheme.colors.background)
            .clipToBounds()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TetheringPattern()
    }
}

@Composable
private fun TetheringPattern() {
    val configuration = LocalConfiguration.current

    val columns = countToFill(configuration.screenWidthDp.dp, IconSize, ColumnSpacing)
    val rows = countToFill(configuration.screenHeightDp.dp, IconSize, IconSpacing)
    val flip = rememberFlip()

    Row(
        modifier = Modifier
            .wrapContentSize(align = Alignment.Center, unbounded = true)
            .width(spanOf(columns, IconSize, ColumnSpacing)),
        horizontalArrangement = Arrangement.spacedBy(ColumnSpacing),
    ) {
        repeat(columns) { column ->
            PatternColumn(
                angles = ColumnAngles[column % ColumnAngles.size],
                rows = rows,
                flip = flip,
            )
        }
    }
}

private fun spanOf(count: Int, cell: Dp, gap: Dp): Dp = cell * count + gap * (count - 1)

/** Alternates 0 to 1 on a hold-and-turn cadence; every icon shares the phase. */
@Composable
private fun rememberFlip(): Float {
    val transition = rememberInfiniteTransition(label = "flip")

    val flip by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = (FlipDurationMillis + FlipHoldMillis) * 2
                0f at 0 using FastOutSlowInEasing
                0f at FlipHoldMillis using FastOutSlowInEasing
                1f at FlipHoldMillis + FlipDurationMillis
                1f at FlipHoldMillis * 2 + FlipDurationMillis using FastOutSlowInEasing
                0f at (FlipHoldMillis + FlipDurationMillis) * 2
            },
        ),
        label = "flipPhase",
    )

    return flip
}

@Composable
private fun PatternColumn(angles: List<Float>, rows: Int, flip: Float) {
    val tint = ShizziTheme.colors.primary

    Column(
        modifier = Modifier.height(spanOf(rows, IconSize, IconSpacing)),
        verticalArrangement = Arrangement.spacedBy(IconSpacing),
    ) {
        repeat(rows) { row ->
            val from = angles[row % angles.size]
            val to = angles[(row + 1) % angles.size]

            TetheringGlyph(
                brush = SolidColor(tint),
                modifier = Modifier
                    .size(IconSize)
                    .graphicsLayer { rotationZ = from + (to - from) * flip },
            )
        }
    }
}

/** Enough cells to overflow [extent], so the pattern bleeds off every edge. */
private fun countToFill(extent: Dp, cell: Dp, gap: Dp): Int =
    ceil((extent + gap) / (cell + gap)).toInt() + BleedCells
