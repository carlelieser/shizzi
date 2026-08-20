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
 * No label under it: the button already reads START or STOP, and a word between
 * the two would state the same fact a third time.
 *
 * Turquoise only when connected, so the screen has exactly one saturated
 * element at a time and it always means the tunnel is up.
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

            // Off one edge to off the other, so the band enters and leaves
            // rather than appearing mid-glyph. Narrower than the glyph, so it
            // reads as a glint crossing rather than the icon brightening.
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
 * One family throughout, so the glyph reads as one object changing rather than
 * four pictures. Ready and loading share a mark — the shimmer distinguishes
 * them, and swapping glyphs mid-animation would read as a transition.
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
 * Absent when Shizuku is ready: a badge confirming the expected is noise on
 * every launch. What to do about a problem is the toast's job.
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
