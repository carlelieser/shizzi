package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.ShizukuState
import dev.shizzi.ui.theme.ShizziTheme

private val StatusIconSize = 120.dp

@Composable
fun ShizukuStatusIcon(state: ShizukuState) {
    val colors = ShizziTheme.colors

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is ShizukuState.Ready -> Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "Shizuku is ready",
                tint = colors.primary,
                modifier = Modifier.size(StatusIconSize),
            )

            else -> Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = "Shizuku is not usable",
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(StatusIconSize),
            )
        }
    }
}
