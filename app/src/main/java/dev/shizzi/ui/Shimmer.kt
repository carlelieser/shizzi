package dev.shizzi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import dev.shizzi.ui.theme.ShizziTheme

fun Modifier.shimmer(
    isActive: Boolean,
    highlight: Color? = null,
): Modifier = composed {
    if (!isActive) return@composed this

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
        ),
        label = "shimmerSweep",
    )

    val sweep = highlight ?: ShizziTheme.colors.onSurface

    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()

            val band = size.width * 0.6f
            val head = progress * (size.width + band * 2f) - band

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.5f to sweep,
                        1f to Color.Transparent,
                    ),
                    start = Offset(head, 0f),
                    end = Offset(head + band, 0f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}
