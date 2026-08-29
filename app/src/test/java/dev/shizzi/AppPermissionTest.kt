package dev.shizzi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionTest {

    @Test
    fun `battery exemption is only required once external control is on`() {
        val off = Settings(isExternalControlEnabled = false)

        assertFalse(AppPermission.BATTERY_EXEMPTION.isRequiredFor(off))
    }

    @Test
    fun `battery exemption is required while external control is on`() {
        val on = Settings(isExternalControlEnabled = true)

        assertTrue(AppPermission.BATTERY_EXEMPTION.isRequiredFor(on))
    }

    @Test
    fun `notifications are required regardless of external control`() {
        assertTrue(AppPermission.NOTIFICATIONS.isRequiredFor(Settings()))
        assertTrue(
            AppPermission.NOTIFICATIONS.isRequiredFor(
                Settings(isExternalControlEnabled = true),
            ),
        )
    }

    @Test
    fun `every permission is presentable to the user`() {
        AppPermission.entries.forEach { permission ->
            assertTrue(permission.name, permission.title.isNotBlank())
            assertTrue(permission.name, permission.rationale.isNotBlank())
        }
    }
}
