package dev.shizzi.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/**
 * Says the tunnel is carrying traffic out through a VPN.
 *
 * Neutral rather than turquoise: the palette reserves the accent for a state
 * worth acting on, and a VPN being up is merely true. The connected status
 * glyph above is already the one saturated thing on screen, and a second would
 * give the eye two things claiming to be the most important.
 *
 * "VIA VPN" rather than "PROTECTED", which overclaims — the app cannot vouch
 * for what the VPN does — and rather than "VPN ACTIVE", which reads equally as
 * "your VPN is on", a thing the system status bar already says. What this adds
 * is that the *tethered clients* are going out through it.
 */
@Composable
fun VpnChip() {
    Row(
        modifier = Modifier
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .padding(
                horizontal = ShizziTheme.spacing.md,
                vertical = ShizziTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "VIA VPN",
            style = ShizziTheme.typography.caption,
            color = ShizziTheme.colors.onSurfaceMuted,
        )
    }
}
