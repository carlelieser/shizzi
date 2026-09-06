package dev.shizzi

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.parseAccent
import dev.shizzi.ui.theme.parseAccents
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsKeysTest {

    private val design = stringPreferencesKey("design")
    private val accent = stringPreferencesKey("accent")
    private val customAccents = stringPreferencesKey("custom_accents")

    @Test
    fun `reads a stored design language`() {
        val stored = preferencesOf(design to DesignLanguage.MATERIAL_EXPRESSIVE.name)

        assertEquals(
            DesignLanguage.MATERIAL_EXPRESSIVE,
            DesignLanguage.valueOf(stored[design].orEmpty()),
        )
    }

    @Test
    fun `falls back to neobrutalism when the design is unreadable`() {
        val stored = preferencesOf(design to "wingdings")

        val resolved = runCatching { DesignLanguage.valueOf(stored[design].orEmpty()) }
            .getOrDefault(DesignLanguage.NEOBRUTALISM)

        assertEquals(DesignLanguage.NEOBRUTALISM, resolved)
    }

    @Test
    fun `reads a stored accent`() {
        val stored = preferencesOf(accent to "#3B82F6")

        assertEquals(AccentChoice.Custom(0xFF3B82F6.toInt()), parseAccent(stored[accent]))
    }

    @Test
    fun `reads stored custom accents in order`() {
        val stored = preferencesOf(customAccents to "#3B82F6,#14B8A6")

        assertEquals(
            listOf(0xFF3B82F6.toInt(), 0xFF14B8A6.toInt()),
            parseAccents(stored[customAccents]),
        )
    }

    @Test
    fun `absent appearance keys read as defaults`() {
        val stored = preferencesOf()

        assertEquals(AccentChoice.Default, parseAccent(stored[accent]))
        assertEquals(emptyList<Int>(), parseAccents(stored[customAccents]))
    }
}
