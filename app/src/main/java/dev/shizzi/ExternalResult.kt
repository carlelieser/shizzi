package dev.shizzi

import android.content.Context
import android.content.Intent

object ExternalResult {

    fun announce(context: Context, command: ExternalCommand, state: SessionUiState) {
        val intent = accepted(command).apply {
            putExtra(ExternalControl.EXTRA_STATUS, state.status.name)
            putExtra(ExternalControl.EXTRA_IS_ACTIVE, state.status == UiStatus.CONNECTED)
            putExtra(ExternalControl.EXTRA_DETAIL, state.detail)
            putExtra(ExternalControl.EXTRA_INTERFACE, state.interfaceName)
            putExtra(ExternalControl.EXTRA_ERROR, state.lastError)
            putExtra(ExternalControl.EXTRA_CLIENT_COUNT, state.clientCount)
            putExtra(ExternalControl.EXTRA_BYTES_UP, state.traffic.up)
            putExtra(ExternalControl.EXTRA_BYTES_DOWN, state.traffic.down)
        }
        context.sendBroadcast(intent)
    }

    fun refuse(context: Context, command: ExternalCommand?, refusal: ExternalRefusal) {
        val intent = Intent(ExternalControl.ACTION_RESULT).apply {
            command?.let { putExtra(ExternalControl.EXTRA_COMMAND, it.name) }
            putExtra(ExternalControl.EXTRA_ACCEPTED, false)
            putExtra(ExternalControl.EXTRA_REFUSAL, ExternalControl.describe(refusal))
        }
        context.sendBroadcast(intent)
    }

    private fun accepted(command: ExternalCommand) = Intent(ExternalControl.ACTION_RESULT).apply {
        putExtra(ExternalControl.EXTRA_COMMAND, command.name)
        putExtra(ExternalControl.EXTRA_ACCEPTED, true)
    }
}
