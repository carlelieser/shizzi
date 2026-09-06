package dev.shizzi

import androidx.datastore.preferences.core.preferencesOf
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsKeysTest {

    @Test
    fun `reads a stored design language`() {
        val stored = preferencesOf(DESIGN to DesignLanguage.NEOBRUTALISM.name)

        assertEquals(DesignLanguage.NEOBRUTALISM, toSettings(stored).design)
    }

    @Test
    fun `defaults to material expressive when no design is stored`() {
        assertEquals(DesignLanguage.MATERIAL_EXPRESSIVE, toSettings(preferencesOf()).design)
    }

    @Test
    fun `falls back to material expressive when the design is unreadable`() {
        val stored = preferencesOf(DESIGN to "wingdings")

        assertEquals(DesignLanguage.MATERIAL_EXPRESSIVE, toSettings(stored).design)
    }

    @Test
    fun `reads a stored accent`() {
        val stored = preferencesOf(ACCENT to "#3B82F6")

        assertEquals(AccentChoice.Custom(0xFF3B82F6.toInt()), toSettings(stored).accent)
    }

    @Test
    fun `reads stored custom accents in order`() {
        val stored = preferencesOf(CUSTOM_ACCENTS to "#3B82F6,#14B8A6")

        assertEquals(
            listOf(0xFF3B82F6.toInt(), 0xFF14B8A6.toInt()),
            toSettings(stored).customAccents,
        )
    }

    @Test
    fun `absent appearance keys read as defaults`() {
        val settings = toSettings(preferencesOf())

        assertEquals(AccentChoice.Default, settings.accent)
        assertEquals(emptyList<Int>(), settings.customAccents)
    }
}
