package dev.shizzi.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ShizukuState
import dev.shizzi.UiStatus
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/** Matches the welcome mark, so the same glyph is the same size across the app. */
private val StatusIconSize = 280.dp

/**
 * Held back from full strength while the tunnel is down, so the mark reads as
 * dormant rather than as a second piece of chrome competing with the button.
 */
private const val InactiveAlpha = 0.10f

/**
 * No label under it: the button already reads START or STOP, and a word between
 * the two would state the same fact a third time.
 *
 * Turquoise only when connected, so the screen has exactly one saturated
 * element at a time and it always means the tunnel is up. Everything else is
 * both muted and faded — the colour alone left the glyph as heavy on screen as
 * the connected one.
 */
@Composable
fun StatusIcon(status: UiStatus) {
    val isConnected = status == UiStatus.CONNECTED
    val target = when {
        isConnected -> ShizziTheme.colors.primary
        else -> ShizziTheme.colors.onSurfaceMuted.copy(alpha = InactiveAlpha)
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
