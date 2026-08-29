package dev.shizzi

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionTest {

    @Test
    fun `every permission carries a manifest name or is granted another way`() {
        assertNull(AppPermission.BATTERY_EXEMPTION.manifestName)
        assertNotNull(AppPermission.NOTIFICATIONS.manifestName)
    }

    @Test
    fun `every permission is presentable to the user`() {
        AppPermission.entries.forEach { permission ->
            assertTrue(permission.name, permission.title.isNotBlank())
            assertTrue(permission.name, permission.rationale.isNotBlank())
        }
    }
}
