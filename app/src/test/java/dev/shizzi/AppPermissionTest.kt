package dev.shizzi

import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionTest {

    @Test
    fun `every permission is presentable to the user`() {
        AppPermission.entries.forEach { permission ->
            assertTrue(permission.name, permission.title.isNotBlank())
            assertTrue(permission.name, permission.rationale.isNotBlank())
        }
    }

    @Test
    fun `every permission names the manifest permission it maps to`() {
        AppPermission.entries.forEach { permission ->
            assertTrue(permission.name, permission.manifestName.isNotBlank())
        }
    }
}
