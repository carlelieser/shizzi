package dev.shizzi

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey

/** Carries the retired USB tethering setting into the wired tethering and Wi-Fi hotspot settings. */
object RetiredUsbTethering {

    private val USB_TETHERING = stringPreferencesKey("usb_tethering")

    private val SETUP_BY_STORED = mapOf(
        "NEVER" to SharingSetup(WiredTethering.OFF, isWifiHotspotChosen = true),
        "AUTO" to SharingSetup(WiredTethering.USB, isWifiHotspotChosen = true),
        "ALWAYS" to SharingSetup(WiredTethering.USB, isWifiHotspotChosen = false),
    )

    fun isPending(preferences: Preferences): Boolean = preferences.contains(USB_TETHERING)

    /** An unrecognized stored value reads as Never, as it did before. */
    fun apply(preferences: Preferences): Preferences {
        val updated = mutablePreferencesOf().apply { plusAssign(preferences) }
        val setup = SETUP_BY_STORED[preferences[USB_TETHERING]] ?: SETUP_BY_STORED.getValue("NEVER")

        if (!updated.contains(WIRED_TETHERING)) updated[WIRED_TETHERING] = setup.wired.name
        if (!updated.contains(WIFI_HOTSPOT)) updated[WIFI_HOTSPOT] = setup.isWifiHotspotChosen
        updated.remove(USB_TETHERING)
        return updated
    }

    fun migration(): DataMigration<Preferences> = object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences) = isPending(currentData)

        override suspend fun migrate(currentData: Preferences) = apply(currentData)

        override suspend fun cleanUp() = Unit
    }
}
