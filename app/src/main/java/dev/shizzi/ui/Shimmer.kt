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

/**
 * Sweeps a highlight across the glyph while [isActive]. A pulse would read as a
 * heartbeat — a rate the user is invited to judge — where a sweep has direction
 * and no rate to read into.
 *
 * SrcATop, not SrcIn: both clip to the glyph's pixels, but SrcIn *replaces* the
 * destination, so the transparent ends of the sweep erase the icon and it
 * appears to be eaten as the band travels.
 *
 * Inactive returns the receiver untouched, leaving no animation running behind
 * an idle screen.
 *
 * Shared rather than private to one screen: the home status glyph and the
 * welcome mark are the same icon given the same treatment, and a second copy
 * would drift in band width or duration against the first.
 *
 * @param highlight what the band is made of; defaults to the page's ink. The
 *   band has to contrast with the *glyph* it crosses, not with the page, so a
 *   coloured mark needs a lighter sweep than a muted one.
 */
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
    // Defaults to the page's own ink, which reads as a glint over the muted
    // home glyph. A caller sweeping a saturated mark passes its own, since
    // onSurface over turquoise is a dark smear rather than a highlight.
    val sweep = highlight ?: ShizziTheme.colors.onSurface

    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()

            // Off one edge to off the other, so the band enters and leaves
            // rather than appearing mid-glyph. Narrower than the glyph, so it
            // reads as a glint crossing rather than the icon brightening.
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
