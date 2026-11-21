package dev.shizzi

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RetiredUsbTetheringTest {

    private val retired = stringPreferencesKey("usb_tethering")

    private fun migrated(stored: String) = toSettings(RetiredUsbTethering.apply(preferencesOf(retired to stored))).sharing

    @Test
    fun `a retired usb setting keeps its behavior`() {
        assertEquals(SharingSetup(WiredTethering.OFF, isWifiHotspotChosen = true), migrated("NEVER"))
        assertEquals(SharingSetup(WiredTethering.USB, isWifiHotspotChosen = true), migrated("AUTO"))
        assertEquals(SharingSetup(WiredTethering.USB, isWifiHotspotChosen = false), migrated("ALWAYS"))
    }

    @Test
    fun `an unreadable retired value keeps reading as never`() {
        assertEquals(SharingSetup(WiredTethering.OFF, isWifiHotspotChosen = true), migrated("carrier pigeon"))
    }

    @Test
    fun `removes the retired key`() {
        val updated = RetiredUsbTethering.apply(preferencesOf(retired to "AUTO"))

        assertFalse(RetiredUsbTethering.isPending(updated))
    }

    @Test
    fun `does not overwrite a wired choice already made`() {
        val stored = preferencesOf(retired to "ALWAYS", WIRED_TETHERING to WiredTethering.ETHERNET.name)

        assertEquals(WiredTethering.ETHERNET, toSettings(RetiredUsbTethering.apply(stored)).sharing.wired)
    }

    @Test
    fun `nothing to carry without a retired setting`() {
        assertFalse(RetiredUsbTethering.isPending(preferencesOf()))
    }
}
