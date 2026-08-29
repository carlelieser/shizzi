package dev.shizzi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ExternalControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val command = ExternalControl.commandFor(intent.action)
        if (command == null) {
            ExternalResult.refuse(context, null, ExternalRefusal.UnknownAction)
            return
        }

        val token = intent.getStringExtra(ExternalControl.EXTRA_TOKEN)
        val pending = goAsync()
        val application = context.applicationContext

        CoroutineScope(Dispatchers.Default).launch {
            try {
                dispatch(application, command, token)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun dispatch(context: Context, command: ExternalCommand, token: String?) {
        val settings = (context as App).settingsStore.settings.first()

        val refusal = ExternalControl.refuse(settings, token)
        if (refusal != null) {
            SessionLog.warn("external ${command.name} refused: ${ExternalControl.describe(refusal)}")
            ExternalResult.refuse(context, command, refusal)
            return
        }

        SessionLog.info("external ${command.name} accepted")
        apply(context, command)
    }

    private fun apply(context: Context, command: ExternalCommand) {
        when (resolve(command)) {
            ExternalCommand.START -> SessionService.start(context, command)
            ExternalCommand.STOP -> SessionService.stop(context, command)
            else -> ExternalResult.announce(context, command, SessionService.liveState.value)
        }
    }

    private fun resolve(command: ExternalCommand): ExternalCommand = when (command) {
        ExternalCommand.TOGGLE -> toggleTarget()
        else -> command
    }

    private fun toggleTarget(): ExternalCommand = when {
        SessionService.isRunning -> ExternalCommand.STOP
        else -> ExternalCommand.START
    }
}
