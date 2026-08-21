package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.CompatibilityState
import dev.shizzi.isCompatible
import dev.shizzi.ui.theme.ShizziTheme

/** Matches the Shizuku step's mark, so the two verdicts carry equal weight. */
private val VerdictIconSize = 120.dp

/** Sized under the mark it replaces, so the band does not jump when it lands. */
private val SpinnerSize = 64.dp

private val SpinnerStroke = 3.dp

/**
 * The verdict, as one mark under the capabilities that justify it.
 *
 * The same three marks the Shizuku step uses, so a verdict reads the same way
 * on both. Unlike that step, the spinner has a state behind it: the check is a
 * round trip to the shell rather than a synchronous read.
 */
@Composable
fun CompatibilityVerdict(state: CompatibilityState) {
    val colors = ShizziTheme.colors

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            state is CompatibilityState.Checking || state is CompatibilityState.Idle ->
                CircularProgressIndicator(
                    color = colors.onSurfaceMuted,
                    strokeWidth = SpinnerStroke,
                    modifier = Modifier.size(SpinnerSize),
                )

            state.isCompatible -> Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "This device is compatible",
                tint = colors.primary,
                modifier = Modifier.size(VerdictIconSize),
            )

            else -> Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = "This device is not compatible",
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(VerdictIconSize),
            )
        }
    }
}
