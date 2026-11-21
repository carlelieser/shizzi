package dev.shizzi.ui.theme

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelCaseTest {

    private val turkish = Locale.forLanguageTag("tr")

    @Test
    fun `uppercases neobrutalist labels with the locale's casing rules`() {
        assertEquals("GELİŞMİŞ", labelCase("Gelişmiş", DesignLanguage.NEOBRUTALISM, turkish))
    }

    @Test
    fun `keeps expressive labels as written`() {
        assertEquals("Gelişmiş", labelCase("Gelişmiş", DesignLanguage.MATERIAL_EXPRESSIVE, turkish))
    }
}
