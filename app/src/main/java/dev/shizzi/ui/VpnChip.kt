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

/**
 * Says the tethered clients are going out through a VPN.
 *
 * No surface behind it. Every bordered element in this app is something to
 * press, so wearing that treatment made a label look like a control; without
 * it, nothing about this invites a tap.
 *
 * The key glyph is the one Android itself puts in the status bar for an active
 * VPN, so it is already the thing a user reads as "VPN" without being taught.
 *
 * Drawn in the accent. The palette otherwise reserves it for a state worth
 * acting on, and a VPN being up is merely true — but this is the one thing on
 * the screen a user opens the app to confirm, and with no surface to frame it
 * the colour is what keeps it from reading as a caption.
 */
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
