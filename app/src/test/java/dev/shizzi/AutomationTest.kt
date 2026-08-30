package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationTest {

    private val enabledWithoutToken = Settings(isAutomationEnabled = true)

    private val enabledWithToken =
        Settings(isAutomationEnabled = true, automationToken = "secret")

    @Test
    fun `refuses every command while automation is off`() {
        val refusal = Automation.refuse(Settings(), presented = null)

        assertEquals(AutomationRefusal.Disabled, refusal)
    }

    @Test
    fun `refuses a correct token while automation is off`() {
        val settings = Settings(automationToken = "secret")

        val refusal = Automation.refuse(settings, presented = "secret")

        assertEquals(AutomationRefusal.Disabled, refusal)
    }

    @Test
    fun `accepts any caller when enabled without a token`() {
        assertNull(Automation.refuse(enabledWithoutToken, presented = null))
    }

    @Test
    fun `accepts a matching token`() {
        assertNull(Automation.refuse(enabledWithToken, presented = "secret"))
    }

    @Test
    fun `refuses a missing token when one is required`() {
        val refusal = Automation.refuse(enabledWithToken, presented = null)

        assertEquals(AutomationRefusal.BadToken, refusal)
    }

    @Test
    fun `refuses a wrong token of the same length`() {
        val refusal = Automation.refuse(enabledWithToken, presented = "secreT")

        assertEquals(AutomationRefusal.BadToken, refusal)
    }

    @Test
    fun `refuses a token that is a prefix of the expected one`() {
        val refusal = Automation.refuse(enabledWithToken, presented = "sec")

        assertEquals(AutomationRefusal.BadToken, refusal)
    }

    @Test
    fun `maps every published action to a command`() {
        assertEquals(AutomationCommand.START, Automation.commandFor(Automation.ACTION_START))
        assertEquals(AutomationCommand.STOP, Automation.commandFor(Automation.ACTION_STOP))
        assertEquals(
            AutomationCommand.TOGGLE,
            Automation.commandFor(Automation.ACTION_TOGGLE),
        )
        assertEquals(
            AutomationCommand.QUERY_STATUS,
            Automation.commandFor(Automation.ACTION_QUERY_STATUS),
        )
    }

    @Test
    fun `does not treat an unknown or absent action as a command`() {
        assertNull(Automation.commandFor("dev.shizzi.action.NOPE"))
        assertNull(Automation.commandFor(null))
    }
}
