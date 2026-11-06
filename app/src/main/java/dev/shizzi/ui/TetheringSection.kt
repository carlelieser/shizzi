package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.L10n
import dev.shizzi.R
import dev.shizzi.UsbTethering

@Composable
fun TetheringSection(
    selected: UsbTethering,
    onSelect: (UsbTethering) -> Unit,
    toasts: ToastState,
) {
    val context = LocalContext.current
    var isPickerOpen by remember { mutableStateOf(false) }

    SettingsChoice(
        label = SettingsText(title = L10n.string(R.string.usb_tethering)),
        value = usbTetheringLabel(selected),
        onClick = { isPickerOpen = true },
    )

    SettingsAction(
        label = SettingsText(title = L10n.string(R.string.open_system_settings)),
        isExternal = true,
        onClick = {
            if (!context.openTetherSettings()) toasts.show(unopenedToast(L10n.string(R.string.system_settings)))
        },
    )

    if (!isPickerOpen) return

    UsbTetheringPicker(
        selected = selected,
        onSelect = onSelect,
        onDismiss = { isPickerOpen = false },
    )
}
