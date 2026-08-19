package dev.shizzi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WifiTetheringError
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ShizukuState
import dev.shizzi.UiStatus
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

private val StatusIconSize = 96.dp

/**
 * The screen's one large element: what the session is doing, as a glyph.
 *
 * No label under it. The button below already reads START or STOP, and a word
 * between the two would be a third statement of the same fact.
 *
 * Turquoise only when connected. The other three states are neutral, so the
 * screen has exactly one saturated element at a time and that element always
 * means the tunnel is up.
 */
@Composable
fun StatusIcon(status: UiStatus) {
    val isConnected = status == UiStatus.CONNECTED
    val target = when {
        isConnected -> ShizziTheme.colors.primary
        else -> ShizziTheme.colors.onSurfaceMuted
    }
    val tint by animateColorAsState(target, label = "statusTint")

    Icon(
        imageVector = glyphFor(status),
        contentDescription = descriptionFor(status),
        tint = tint,
        modifier = Modifier
            .size(StatusIconSize)
            .shimmer(isActive = status == UiStatus.LOADING),
    )
}

/**
 * Sweeps a highlight across the glyph, left to right, while [isActive].
 *
 * The alternative for "working on it" is a pulse, which reads as a heartbeat —
 * a thing with a rate, which invites the user to judge whether it is going
 * well. A sweep has a direction and no rate to read into.
 *
 * The highlight is composited with SrcATop: clipped to the glyph's own pixels,
 * so the band never paints the empty box around it, but leaving the glyph
 * intact wherever the gradient is transparent. SrcIn does the clipping too and
 * looks correct in a still frame, but it *replaces* the destination — the
 * transparent ends of the sweep erase the icon, so only the lit band is
 * visible and the glyph appears to be eaten as the band travels.
 *
 * Inactive returns the receiver untouched, leaving no animation running behind
 * an idle screen.
 */
private fun Modifier.shimmer(isActive: Boolean): Modifier = composed {
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
    val highlight = ShizziTheme.colors.onSurface

    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()

            // Travels from fully off one edge to fully off the other, so the
            // band enters and leaves rather than appearing mid-glyph. The band
            // is narrower than the glyph so it reads as a glint crossing it
            // rather than the whole icon changing brightness at once.
            val band = size.width * 0.6f
            val head = progress * (size.width + band * 2f) - band

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.5f to highlight,
                        1f to Color.Transparent,
                    ),
                    start = Offset(head, 0f),
                    end = Offset(head + band, 0f),
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}

/**
 * One family across every state, so the glyph reads as the same object
 * changing rather than four unrelated pictures.
 *
 * Ready and loading share the plain mark: what distinguishes them is the
 * shimmer and the button, not a different icon, and swapping the glyph mid-
 * animation would make the shimmer look like a transition to something else.
 */
private fun glyphFor(status: UiStatus): ImageVector = when (status) {
    UiStatus.READY -> Icons.Filled.WifiTethering
    UiStatus.LOADING -> Icons.Filled.WifiTethering
    UiStatus.CONNECTED -> Icons.Filled.WifiTethering
    UiStatus.ERROR -> Icons.Filled.WifiTetheringError
}

/** The label the icon does not draw, for anyone using a screen reader. */
private fun descriptionFor(status: UiStatus): String = when (status) {
    UiStatus.READY -> "Not connected"
    UiStatus.LOADING -> "Connecting"
    UiStatus.CONNECTED -> "Connected"
    UiStatus.ERROR -> "Failed"
}

/**
 * States Shizuku availability in the header, and nothing more.
 *
 * Absent when Shizuku is ready, because a badge confirming that the thing which
 * is supposed to work does work is noise on every launch. What to do about a
 * problem is the toast's job; this only says one exists.
 */
@Composable
fun ShizukuBadge(state: ShizukuState) {
    val text = badgeText(state) ?: return

    Text(
        text = text,
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .padding(
                horizontal = ShizziTheme.spacing.sm,
                vertical = ShizziTheme.spacing.xs,
            ),
    )
}

private fun badgeText(state: ShizukuState): String? = when (state) {
    is ShizukuState.Ready -> null
    is ShizukuState.NotInstalled -> "NO SHIZUKU"
    is ShizukuState.NotRunning -> "SHIZUKU OFF"
    is ShizukuState.PermissionRequired -> "PERMISSION NEEDED"
}
