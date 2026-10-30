package dev.shizzi.ui

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
import dev.shizzi.UsbTethering
import dev.shizzi.ui.theme.ShizziTheme

private val CheckSize = 20.dp

fun usbTetheringLabel(mode: UsbTethering): String = when (mode) {
    UsbTethering.AUTO -> "Auto"
    UsbTethering.ALWAYS -> "Always"
    UsbTethering.NEVER -> "Never"
}

private fun descriptionOf(mode: UsbTethering): String = when (mode) {
    UsbTethering.AUTO -> "Also share over USB when a cable is connected"
    UsbTethering.ALWAYS -> "Share over USB only; requires a cable"
    UsbTethering.NEVER -> "Share through the hotspot only"
}

@Composable
fun UsbTetheringPicker(
    selected: UsbTethering,
    onSelect: (UsbTethering) -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedBottomSheet(onDismiss = onDismiss) {
        Text(
            text = "USB tethering",
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        UsbTethering.entries.forEach { mode ->
            UsbTetheringOption(
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
private fun UsbTetheringOption(
    mode: UsbTethering,
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
                text = usbTetheringLabel(mode),
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
