package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.ShizukuState
import dev.shizzi.ui.ShizukuCard

/**
 * The settings screen's Shizuku card, unchanged.
 *
 * Shared rather than copied: this is the same question asked at a different
 * time, and a second rendering of it would be a second thing to keep correct as
 * the states change.
 */
@Composable
fun ShizukuStep(state: ShizukuState, onGrant: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ShizukuCard(state = state, onGrant = onGrant)

        // Takes the space between the card and the footer and centres in it,
        // rather than hanging at a fixed gap under the card.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ShizukuStatusIcon(state)
        }
    }
}
