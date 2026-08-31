package dev.shizzi

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenamedKeysTest {

    private val retiredAutomation = booleanPreferencesKey("external_control")
    private val retiredToken = stringPreferencesKey("external_control_token")

    private val automation = booleanPreferencesKey("automation")
    private val token = stringPreferencesKey("automation_token")

    @Test
    fun `carries a retired setting onto the new key`() {
        val before = preferencesOf(retiredAutomation to true, retiredToken to "secret")

        val after = RenamedKeys.apply(before)

        assertEquals(true, after[automation])
        assertEquals("secret", after[token])
    }

    @Test
    fun `drops the retired keys once carried`() {
        val before = preferencesOf(retiredAutomation to true, retiredToken to "secret")

        val after = RenamedKeys.apply(before)

        assertNull(after[retiredAutomation])
        assertNull(after[retiredToken])
    }

    @Test
    fun `does not overwrite a value already under the new key`() {
        val before = preferencesOf(
            retiredToken to "old",
            token to "current",
        )

        val after = RenamedKeys.apply(before)

        assertEquals("current", after[token])
    }

    @Test
    fun `leaves unrelated settings untouched`() {
        val theme = stringPreferencesKey("theme")
        val before = preferencesOf(retiredAutomation to true, theme to "DARK")

        val after = RenamedKeys.apply(before)

        assertEquals("DARK", after[theme])
    }

    @Test
    fun `runs only while a retired key is present`() {
        assertTrue(RenamedKeys.isPending(preferencesOf(retiredAutomation to false)))
        assertTrue(RenamedKeys.isPending(preferencesOf(retiredToken to "secret")))
    }

    @Test
    fun `does not run once migrated`() {
        val migrated = preferencesOf(automation to true, token to "secret")

        assertFalse(RenamedKeys.isPending(migrated))
        assertFalse(RenamedKeys.isPending(mutablePreferencesOf()))
    }

    @Test
    fun `carries a disabled setting rather than dropping it`() {
        val before = preferencesOf(retiredAutomation to false)

        val after = RenamedKeys.apply(before)

        assertEquals(false, after[automation])
    }
}
