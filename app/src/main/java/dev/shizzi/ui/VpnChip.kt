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
fun VpnChip(isBypassed: Boolean = false) {
    val tint = when {
        isBypassed -> ShizziTheme.colors.onSurfaceMuted
        else -> ShizziTheme.colors.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.VpnKey,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ShizziTheme.spacing.lg),
        )

        Text(
            text = if (isBypassed) "VPN IGNORED" else "VPN CONNECTED",
            style = ShizziTheme.typography.caption,
            color = tint,
        )
    }
}
