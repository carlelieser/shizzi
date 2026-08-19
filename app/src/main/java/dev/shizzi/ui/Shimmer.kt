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

/** One sweep, shared by every shimmer so they read as the same signal. */
private const val SweepDurationMs = 1400

/**
 * Sweeps a highlight across whatever this modifies, left to right, while
 * [isActive].
 *
 * The alternative for "working on it" is a pulse, which reads as a heartbeat —
 * a thing with a rate, which invites the user to judge whether it is going
 * well. A sweep has a direction and no rate to read into.
 *
 * The highlight is composited with SrcATop: clipped to the content's own
 * pixels, so the band never paints the gaps between elements, but leaving the
 * content intact wherever the gradient is transparent. SrcIn does the clipping
 * too and looks correct in a still frame, but it *replaces* the destination —
 * the transparent ends of the sweep erase the content, so only the lit band is
 * visible and the content appears to be eaten as the band travels.
 *
 * Inactive returns the receiver untouched, leaving no animation running behind
 * an idle screen.
 *
 * @param band how much of the width the lit region spans. Narrower than the
 *   content, so it reads as a glint crossing it rather than the whole thing
 *   changing brightness at once.
 */
fun Modifier.shimmer(isActive: Boolean, band: Float = 0.6f): Modifier = composed {
    if (!isActive) return@composed this

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SweepDurationMs, easing = LinearEasing),
        ),
        label = "shimmerSweep",
    )
    val highlight = ShizziTheme.colors.onSurface

    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()

            // Travels from fully off one edge to fully off the other, so the
            // band enters and leaves rather than appearing mid-content.
            val width = size.width * band
            val head = progress * (size.width + width * 2f) - width

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.5f to highlight,
                        1f to Color.Transparent,
                    ),
                    start = Offset(head, 0f),
                    end = Offset(head + width, 0f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}
