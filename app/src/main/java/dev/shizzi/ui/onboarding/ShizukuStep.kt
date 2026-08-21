package dev.shizzi.ui.onboarding

import androidx.compose.runtime.Composable
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
    ShizukuCard(state = state, onGrant = onGrant)
}
