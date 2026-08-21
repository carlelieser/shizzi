package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.ui.theme.ShizziTheme

@Composable
fun VpnChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.VpnKey,
            contentDescription = null,
            tint = ShizziTheme.colors.primary,
            modifier = Modifier.size(ShizziTheme.spacing.lg),
        )

        Text(
            text = "VPN CONNECTED",
            style = ShizziTheme.typography.caption,
            color = ShizziTheme.colors.primary,
        )
    }
}
