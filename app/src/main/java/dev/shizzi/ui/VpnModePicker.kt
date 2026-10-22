package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.VpnMode
import dev.shizzi.ui.theme.ShizziTheme

private val CheckSize = 20.dp

fun vpnModeLabel(mode: VpnMode): String = when (mode) {
    VpnMode.AUTO -> str(R.string.automatic)
    VpnMode.ALWAYS -> str(R.string.always)
    VpnMode.NEVER -> str(R.string.never)
}

private fun descriptionOf(mode: VpnMode): String = when (mode) {
    VpnMode.AUTO -> str(R.string.use_an_active_vpn_automatically)
    VpnMode.ALWAYS -> str(R.string.refuse_to_connect_without_an_active_vpn)
    VpnMode.NEVER -> str(R.string.ignore_active_vpn)
}

@Composable
fun VpnModePicker(
    selected: VpnMode,
    onSelect: (VpnMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "VPN",
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        VpnMode.entries.forEach { mode ->
            VpnModeOption(
                mode = mode,
                isSelected = mode == selected,
                onSelect = {
                    onSelect(mode)
                    onDismiss()
                },
            )
        }

        Spacer(Modifier.height(ShizziTheme.spacing.lg))
    }
}

@Composable
private fun VpnModeOption(
    mode: VpnMode,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = ShizziTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vpnModeLabel(mode),
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )

            Text(
                text = descriptionOf(mode),
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        if (!isSelected) return@Row

        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = ShizziTheme.colors.primary,
            modifier = Modifier.size(CheckSize),
        )
    }
}
