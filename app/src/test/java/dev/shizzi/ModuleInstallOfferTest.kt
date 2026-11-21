package dev.shizzi

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleInstallOfferTest {

    private val missingPreference = listOf(
        CapabilityResult(Capability.TEST_NETWORK, isPresent = true, detail = ""),
        CapabilityResult(Capability.PREFER_TEST_NETWORKS, isPresent = false, detail = ""),
    )

    @Test
    fun `offers the module to an arm64 device on Android 12`() {
        val device = DeviceProfile(Build.VERSION_CODES.S, listOf("arm64-v8a", "armeabi-v7a"))

        assertTrue(missingPreference.isFixableByModuleInstall(device))
    }

    @Test
    fun `does not offer the arm64-only module to a 32-bit device`() {
        val device = DeviceProfile(Build.VERSION_CODES.S, listOf("armeabi-v7a", "armeabi"))

        assertFalse(missingPreference.isFixableByModuleInstall(device))
    }

    @Test
    fun `does not offer the module outside the installable releases`() {
        val device = DeviceProfile(Build.VERSION_CODES.TIRAMISU, listOf("arm64-v8a"))

        assertFalse(missingPreference.isFixableByModuleInstall(device))
    }
}
