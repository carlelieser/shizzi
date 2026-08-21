package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.CompatibilityState
import dev.shizzi.isCompatible
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/**
 * The verdict, over the list that justifies it.
 *
 * Filled only when compatible: turquoise marks a state worth acting on, and
 * this is the one that means the app will run. Everything else states its case
 * in words on the app's one neutral surface, so a device that fails is told
 * what is wrong rather than shown alarm.
 */
@Composable
fun CompatibilityBadge(state: CompatibilityState) {
    val colors = ShizziTheme.colors
    val isPassing = state.isCompatible

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = badgeText(state),
            style = ShizziTheme.typography.caption,
            color = if (isPassing) colors.onPrimary else colors.onSurface,
            modifier = Modifier
                .brutalSurface(fill = if (isPassing) colors.primary else colors.surface)
                .padding(
                    horizontal = ShizziTheme.spacing.lg,
                    vertical = ShizziTheme.spacing.sm,
                ),
        )
    }
}

/**
 * Uppercase like every other caption in the app.
 *
 * A failed run reads as not compatible rather than naming the failure: the
 * verdict line has room for three words, and the reason belongs beside the
 * capability that could not answer.
 */
private fun badgeText(state: CompatibilityState): String = when {
    state is CompatibilityState.Idle -> "NOT CHECKED"
    state is CompatibilityState.Checking -> "CHECKING"
    state.isCompatible -> "COMPATIBLE"
    else -> "NOT COMPATIBLE"
}
