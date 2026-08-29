package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalControlTest {

    private val enabledWithoutToken = Settings(isExternalControlEnabled = true)

    private val enabledWithToken =
        Settings(isExternalControlEnabled = true, externalControlToken = "secret")

    @Test
    fun `refuses every command while external control is off`() {
        val refusal = ExternalControl.refuse(Settings(), presented = null)

        assertEquals(ExternalRefusal.Disabled, refusal)
    }

    @Test
    fun `refuses a correct token while external control is off`() {
        val settings = Settings(externalControlToken = "secret")

        val refusal = ExternalControl.refuse(settings, presented = "secret")

        assertEquals(ExternalRefusal.Disabled, refusal)
    }

    @Test
    fun `accepts any caller when enabled without a token`() {
        assertNull(ExternalControl.refuse(enabledWithoutToken, presented = null))
    }

    @Test
    fun `accepts a matching token`() {
        assertNull(ExternalControl.refuse(enabledWithToken, presented = "secret"))
    }

    @Test
    fun `refuses a missing token when one is required`() {
        val refusal = ExternalControl.refuse(enabledWithToken, presented = null)

        assertEquals(ExternalRefusal.BadToken, refusal)
    }

    @Test
    fun `refuses a wrong token of the same length`() {
        val refusal = ExternalControl.refuse(enabledWithToken, presented = "secreT")

        assertEquals(ExternalRefusal.BadToken, refusal)
    }

    @Test
    fun `refuses a token that is a prefix of the expected one`() {
        val refusal = ExternalControl.refuse(enabledWithToken, presented = "sec")

        assertEquals(ExternalRefusal.BadToken, refusal)
    }

    @Test
    fun `maps every published action to a command`() {
        assertEquals(ExternalCommand.START, ExternalControl.commandFor(ExternalControl.ACTION_START))
        assertEquals(ExternalCommand.STOP, ExternalControl.commandFor(ExternalControl.ACTION_STOP))
        assertEquals(
            ExternalCommand.TOGGLE,
            ExternalControl.commandFor(ExternalControl.ACTION_TOGGLE),
        )
        assertEquals(
            ExternalCommand.QUERY_STATUS,
            ExternalControl.commandFor(ExternalControl.ACTION_QUERY_STATUS),
        )
    }

    @Test
    fun `does not treat an unknown or absent action as a command`() {
        assertNull(ExternalControl.commandFor("dev.shizzi.action.NOPE"))
        assertNull(ExternalControl.commandFor(null))
    }
}
