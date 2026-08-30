package dev.shizzi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val command = Automation.commandFor(intent.action)
        if (command == null) {
            AutomationResult.refuse(context, null, AutomationRefusal.UnknownAction)
            return
        }

        val token = intent.getStringExtra(Automation.EXTRA_TOKEN)
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

    private suspend fun dispatch(context: Context, command: AutomationCommand, token: String?) {
        val settings = (context as App).settingsStore.settings.first()

        val refusal = Automation.refuse(settings, token)
        if (refusal != null) {
            SessionLog.warn("automation ${command.name} refused: ${Automation.describe(refusal)}")
            AutomationResult.refuse(context, command, refusal)
            return
        }

        SessionLog.info("automation ${command.name} accepted")
        apply(context, command)
    }

    private fun apply(context: Context, command: AutomationCommand) {
        val resolved = resolve(command)
        if (resolved == AutomationCommand.QUERY_STATUS) {
            AutomationResult.announce(context, command, SessionService.liveState.value)
            return
        }

        runCatching { deliver(context, resolved, command) }
            .onFailure { failure -> reportUndelivered(context, command, failure) }
    }

    private fun deliver(context: Context, resolved: AutomationCommand, reportAs: AutomationCommand) {
        when (resolved) {
            AutomationCommand.STOP -> SessionService.stop(context, reportAs)
            else -> SessionService.start(context, reportAs)
        }
    }

    private fun reportUndelivered(
        context: Context,
        command: AutomationCommand,
        failure: Throwable,
    ) {
        val reason = "${failure.javaClass.simpleName}: ${failure.message}"
        SessionLog.error("automation ${command.name} could not reach the session service: $reason")
        AutomationResult.fail(context, command, reason)
    }

    private fun resolve(command: AutomationCommand): AutomationCommand = when (command) {
        AutomationCommand.TOGGLE -> toggleTarget()
        else -> command
    }

    private fun toggleTarget(): AutomationCommand = when {
        SessionService.isRunning -> AutomationCommand.STOP
        else -> AutomationCommand.START
    }
}
