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

@Composable
fun ShizukuStep(state: ShizukuState, onGrant: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ShizukuCard(state = state, onGrant = onGrant)

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ShizukuStatusIcon(state)
        }
    }
}
