package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.shizzi.R
import dev.shizzi.UsbTethering

@Composable
fun TetheringSection(
    selected: UsbTethering,
    onSelect: (UsbTethering) -> Unit,
    toasts: ToastState,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val systemSettings = stringResource(R.string.system_settings)
    var isPickerOpen by remember { mutableStateOf(false) }

    SettingsChoice(
        label = SettingsText(title = stringResource(R.string.usb_tethering)),
        value = usbTetheringLabel(selected),
        onClick = { isPickerOpen = true },
    )

    SettingsAction(
        label = SettingsText(title = stringResource(R.string.open_system_settings)),
        isExternal = true,
        onClick = {
            if (!context.openTetherSettings()) toasts.show(unopenedToast(resources, systemSettings))
        },
    )

    if (!isPickerOpen) return

    UsbTetheringPicker(
        selected = selected,
        onSelect = onSelect,
        onDismiss = { isPickerOpen = false },
    )
}
