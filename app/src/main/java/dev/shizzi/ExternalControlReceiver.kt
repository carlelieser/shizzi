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
        val resolved = resolve(command)
        if (resolved == ExternalCommand.QUERY_STATUS) {
            ExternalResult.announce(context, command, SessionService.liveState.value)
            return
        }

        runCatching { deliver(context, resolved, command) }
            .onFailure { failure -> reportUndelivered(context, command, failure) }
    }

    private fun deliver(context: Context, resolved: ExternalCommand, reportAs: ExternalCommand) {
        when (resolved) {
            ExternalCommand.STOP -> SessionService.stop(context, reportAs)
            else -> SessionService.start(context, reportAs)
        }
    }

    private fun reportUndelivered(
        context: Context,
        command: ExternalCommand,
        failure: Throwable,
    ) {
        val reason = "${failure.javaClass.simpleName}: ${failure.message}"
        SessionLog.error("external ${command.name} could not reach the session service: $reason")
        ExternalResult.fail(context, command, reason)
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
