package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.AutomationCommand
import dev.shizzi.ui.theme.ShizziTheme

@Composable
fun CommandTabs(
    selected: AutomationCommand,
    onSelect: (AutomationCommand) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        AutomationCommand.entries.forEach { command ->
            GhostButton(
                label = labelFor(command),
                onClick = { onSelect(command) },
                isActive = command == selected,
                padding = 0.dp,
            )
        }
    }
}

private fun labelFor(command: AutomationCommand): String = when (command) {
    AutomationCommand.START -> str(R.string.action_start)
    AutomationCommand.STOP -> str(R.string.action_stop)
    AutomationCommand.TOGGLE -> str(R.string.toggle)
    AutomationCommand.QUERY_STATUS -> str(R.string.status)
}
