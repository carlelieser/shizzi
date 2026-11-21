package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shizzi.AutomationCommand
import dev.shizzi.R
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

@Composable
@ReadOnlyComposable
private fun labelFor(command: AutomationCommand): String = when (command) {
    AutomationCommand.START -> stringResource(R.string.command_start)
    AutomationCommand.STOP -> stringResource(R.string.command_stop)
    AutomationCommand.TOGGLE -> stringResource(R.string.command_toggle)
    AutomationCommand.QUERY_STATUS -> stringResource(R.string.command_status)
}
