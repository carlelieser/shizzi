package dev.shizzi.spike.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.spike.ShizukuState
import dev.shizzi.spike.UiStatus
import dev.shizzi.spike.ui.theme.ShizziTheme
import dev.shizzi.spike.ui.theme.brutalSurface

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
        modifier = Modifier.size(StatusIconSize),
    )
}

private fun glyphFor(status: UiStatus): ImageVector = when (status) {
    UiStatus.READY -> Icons.Filled.CloudQueue
    UiStatus.LOADING -> Icons.Filled.CloudQueue
    UiStatus.CONNECTED -> Icons.Filled.WifiTethering
    UiStatus.ERROR -> Icons.Filled.ErrorOutline
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
    is ShizukuState.UnsupportedPlatform -> "UNSUPPORTED"
}
