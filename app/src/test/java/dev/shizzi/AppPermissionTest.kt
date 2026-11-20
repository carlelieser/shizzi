package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppPermissionTest {

    @Test
    fun `every permission is presented with its own text`() {
        val permissions = AppPermission.entries

        assertEquals(permissions.size, permissions.map { it.titleRes }.toSet().size)
        assertEquals(permissions.size, permissions.map { it.rationaleRes }.toSet().size)
    }

    @Test
    fun `every permission carries a manifest name or is granted another way`() {
        assertNotNull(AppPermission.NOTIFICATIONS.manifestName)
        assertNull(AppPermission.BATTERY_EXEMPTION.manifestName)
    }
}
