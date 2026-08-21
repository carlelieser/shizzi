package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.CompatibilityState
import dev.shizzi.ui.theme.ShizziTheme

/** Matches the capability rows' mark, so the three cards read as one column. */
private val IconSize = 24.dp

private val SpinnerSize = 18.dp

/**
 * What the module card is doing, as a glyph.
 *
 * The fix-path cards are otherwise built exactly like the capability rows above
 * them — same surface, same type — so without a mark of their own they read as a
 * third capability rather than as the thing to act on. This is the leading
 * distinction, and it tracks the state rather than decorating it: a spinner
 * while work is in flight, a warning when it stopped, the restart glyph once the
 * module is staged.
 */
@Composable
fun ModuleStateIcon(state: CompatibilityState) {
    Box(
        modifier = Modifier.size(IconSize),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is CompatibilityState.Downloading,
            is CompatibilityState.Installing,
            -> CircularProgressIndicator(
                color = ShizziTheme.colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(SpinnerSize),
            )

            else -> Icon(
                imageVector = glyphFor(state),
                contentDescription = descriptionFor(state),
                tint = tintFor(state),
                modifier = Modifier.size(IconSize),
            )
        }
    }
}

private fun glyphFor(state: CompatibilityState): ImageVector = when (state) {
    is CompatibilityState.Staged -> Icons.Filled.RestartAlt
    is CompatibilityState.DownloadFailed -> Icons.Filled.ErrorOutline
    is CompatibilityState.InstallFailed -> Icons.Filled.ErrorOutline
    is CompatibilityState.Downloaded -> Icons.Filled.SystemUpdateAlt
    else -> Icons.Filled.Download
}

/**
 * Muted for a stop, primary for anything still moving forward — the same
 * division the capability rows draw between a failure and a pass.
 */
@Composable
private fun tintFor(state: CompatibilityState): Color = when (state) {
    is CompatibilityState.DownloadFailed -> ShizziTheme.colors.onSurfaceMuted
    is CompatibilityState.InstallFailed -> ShizziTheme.colors.onSurfaceMuted
    else -> ShizziTheme.colors.primary
}

private fun descriptionFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Staged -> "Restart required"
    is CompatibilityState.DownloadFailed -> "Download failed"
    is CompatibilityState.InstallFailed -> "Install failed"
    is CompatibilityState.Downloaded -> "Ready to install"
    else -> "Module available"
}
