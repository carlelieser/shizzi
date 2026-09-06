package dev.shizzi

import android.content.Context
import android.content.Intent

object AutomationResult {

    fun announce(context: Context, command: AutomationCommand, state: SessionUiState) {
        val intent = accepted(command).apply {
            putExtra(Automation.EXTRA_STATUS, state.status.name)
            putExtra(Automation.EXTRA_IS_ACTIVE, state.status == UiStatus.CONNECTED)
            putExtra(Automation.EXTRA_DETAIL, state.detail)
            putExtra(Automation.EXTRA_INTERFACE, state.interfaceName)
            putExtra(Automation.EXTRA_ERROR, state.lastError)
            putExtra(Automation.EXTRA_CLIENT_COUNT, state.clientCount)
            putExtra(Automation.EXTRA_BYTES_UP, state.traffic.up)
            putExtra(Automation.EXTRA_BYTES_DOWN, state.traffic.down)
        }
        context.sendBroadcast(intent)
    }

    fun refuse(context: Context, command: AutomationCommand?, refusal: AutomationRefusal) {
        val intent = Intent(Automation.ACTION_RESULT).apply {
            command?.let { putExtra(Automation.EXTRA_COMMAND, it.name) }
            putExtra(Automation.EXTRA_ACCEPTED, false)
            putExtra(Automation.EXTRA_REFUSAL, Automation.describe(refusal))
        }
        context.sendBroadcast(intent)
    }

    fun fail(context: Context, command: AutomationCommand, reason: String) {
        val intent = accepted(command).apply {
            putExtra(Automation.EXTRA_STATUS, UiStatus.ERROR.name)
            putExtra(Automation.EXTRA_IS_ACTIVE, false)
            putExtra(Automation.EXTRA_ERROR, reason)
        }
        context.sendBroadcast(intent)
    }

    private fun accepted(command: AutomationCommand) = Intent(Automation.ACTION_RESULT).apply {
        putExtra(Automation.EXTRA_COMMAND, command.name)
        putExtra(Automation.EXTRA_ACCEPTED, true)
    }
}
