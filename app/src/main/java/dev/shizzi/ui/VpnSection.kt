package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.shizzi.VpnMode

@Composable
fun VpnSection(selected: VpnMode, onSelect: (VpnMode) -> Unit) {
    var isPickerOpen by remember { mutableStateOf(false) }

    SettingsChoice(
        label = SettingsText(title = "VPN"),
        value = vpnModeLabel(selected),
        onClick = { isPickerOpen = true },
    )

    if (!isPickerOpen) return

    VpnModePicker(
        selected = selected,
        onSelect = onSelect,
        onDismiss = { isPickerOpen = false },
    )
}
