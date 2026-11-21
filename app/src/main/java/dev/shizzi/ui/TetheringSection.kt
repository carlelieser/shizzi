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
import dev.shizzi.SharingSetup
import dev.shizzi.WiredTethering

data class TetheringActions(
    val onSetWiredTethering: (WiredTethering) -> Unit,
    val onSetWifiHotspot: (Boolean) -> Unit,
)

@Composable
fun TetheringSection(sharing: SharingSetup, actions: TetheringActions, toasts: ToastState) {
    var isPickerOpen by remember { mutableStateOf(false) }

    SettingsChoice(
        label = SettingsText(title = stringResource(R.string.wired_tethering)),
        value = wiredTetheringLabel(sharing.wired),
        onClick = { isPickerOpen = true },
    )

    WifiHotspotToggle(sharing = sharing, onSetWifiHotspot = actions.onSetWifiHotspot)
    OpenTetherSettingsAction(toasts)

    if (!isPickerOpen) return

    WiredTetheringPicker(
        selected = sharing.wired,
        onSelect = actions.onSetWiredTethering,
        onDismiss = { isPickerOpen = false },
    )
}

/** With wired tethering off the hotspot is all there is, so the switch holds on. */
@Composable
private fun WifiHotspotToggle(sharing: SharingSetup, onSetWifiHotspot: (Boolean) -> Unit) {
    val isLocked = sharing.wired == WiredTethering.OFF
    val subtitle = if (isLocked) stringResource(R.string.wifi_hotspot_required) else ""

    SettingsToggle(
        label = SettingsText(title = stringResource(R.string.wifi_hotspot), subtitle = subtitle),
        isChecked = sharing.isWifiHotspotOn,
        onCheckedChange = onSetWifiHotspot.takeUnless { isLocked },
    )
}

@Composable
private fun OpenTetherSettingsAction(toasts: ToastState) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val systemSettings = stringResource(R.string.system_settings)

    SettingsAction(
        label = SettingsText(title = stringResource(R.string.open_system_settings)),
        isExternal = true,
        onClick = {
            if (!context.openTetherSettings()) toasts.show(unopenedToast(resources, systemSettings))
        },
    )
}
