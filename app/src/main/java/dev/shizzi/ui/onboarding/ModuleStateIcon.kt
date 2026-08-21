package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RestartAlt
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
                color = ShizziTheme.colors.onSurfaceMuted,
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
    is CompatibilityState.Downloaded -> Icons.Filled.Check
    else -> Icons.Filled.FileDownload
}

/**
 * Primary only once something has landed, matching the capability rows: their
 * check mark is the one primary glyph and it means an answered pass. An offer
 * not yet acted on, and a stop, are both muted — colouring the initial download
 * primary would give the loudest mark on screen to the state that has done the
 * least.
 */
@Composable
private fun tintFor(state: CompatibilityState): Color = when (state) {
    is CompatibilityState.Downloaded -> ShizziTheme.colors.primary
    is CompatibilityState.Staged -> ShizziTheme.colors.primary
    else -> ShizziTheme.colors.onSurfaceMuted
}

private fun descriptionFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Staged -> "Restart required"
    is CompatibilityState.DownloadFailed -> "Download failed"
    is CompatibilityState.InstallFailed -> "Install failed"
    is CompatibilityState.Downloaded -> "Downloaded and verified"
    else -> "Module available"
}
