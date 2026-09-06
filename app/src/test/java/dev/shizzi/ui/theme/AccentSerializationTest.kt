package dev.shizzi.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentSerializationTest {

    private val teal = 0xFF14B8A6.toInt()

    @Test
    fun `round-trips the default accent`() {
        assertEquals(AccentChoice.Default, parseAccent(AccentChoice.Default.serialize()))
    }

    @Test
    fun `round-trips the expressive accent`() {
        assertEquals(
            AccentChoice.Expressive,
            parseAccent(AccentChoice.Expressive.serialize()),
        )
    }

    @Test
    fun `round-trips a custom accent`() {
        val custom = AccentChoice.Custom(teal)

        assertEquals(custom, parseAccent(custom.serialize()))
    }

    @Test
    fun `falls back to default on malformed input`() {
        assertEquals(AccentChoice.Default, parseAccent("not-a-color"))
        assertEquals(AccentChoice.Default, parseAccent("#12"))
        assertEquals(AccentChoice.Default, parseAccent(null))
        assertEquals(AccentChoice.Default, parseAccent(""))
    }

    @Test
    fun `preserves custom accent ordering`() {
        val accents = listOf(0xFFE11D48.toInt(), teal, 0xFF3B82F6.toInt())

        assertEquals(accents, parseAccents(serializeAccents(accents)))
    }

    @Test
    fun `drops malformed entries from a custom accent list`() {
        assertEquals(listOf(teal), parseAccents("#14B8A6,garbage"))
    }

    @Test
    fun `reads an empty custom accent list`() {
        assertEquals(emptyList<Int>(), parseAccents(""))
        assertEquals(emptyList<Int>(), parseAccents(null))
    }
}
