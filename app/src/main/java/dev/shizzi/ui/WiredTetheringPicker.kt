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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shizzi.R
import dev.shizzi.WiredTethering
import dev.shizzi.ui.theme.ShizziTheme

private val CheckSize = 20.dp

@Composable
@ReadOnlyComposable
fun wiredTetheringLabel(mode: WiredTethering): String = when (mode) {
    WiredTethering.OFF -> stringResource(R.string.wired_mode_off)
    WiredTethering.AUTO -> stringResource(R.string.wired_mode_auto)
    WiredTethering.USB -> stringResource(R.string.wired_mode_usb)
    WiredTethering.ETHERNET -> stringResource(R.string.wired_mode_ethernet)
}

@Composable
@ReadOnlyComposable
private fun descriptionOf(mode: WiredTethering): String = when (mode) {
    WiredTethering.OFF -> stringResource(R.string.wired_mode_off_description)
    WiredTethering.AUTO -> stringResource(R.string.wired_mode_auto_description)
    WiredTethering.USB -> stringResource(R.string.wired_mode_usb_description)
    WiredTethering.ETHERNET -> stringResource(R.string.wired_mode_ethernet_description)
}

@Composable
fun WiredTetheringPicker(
    selected: WiredTethering,
    onSelect: (WiredTethering) -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedBottomSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.wired_tethering),
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        WiredTethering.entries.forEach { mode ->
            WiredTetheringOption(
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
private fun WiredTetheringOption(
    mode: WiredTethering,
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
                text = wiredTetheringLabel(mode),
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
