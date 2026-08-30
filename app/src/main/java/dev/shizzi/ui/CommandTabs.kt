package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.AutomationCommand
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface
import dev.shizzi.ui.theme.isPressed

private val TabHeight = 36.dp

@Composable
fun CommandTabs(
    selected: AutomationCommand,
    onSelect: (AutomationCommand) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        AutomationCommand.entries.forEach { command ->
            CommandTab(
                command = command,
                isSelected = command == selected,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CommandTab(
    command: AutomationCommand,
    isSelected: Boolean,
    onSelect: (AutomationCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = ShizziTheme.colors

    Box(
        modifier = modifier
            .height(TabHeight)
            .brutalSurface(
                fill = if (isSelected) colors.primary else colors.surface,
                isPressed = interaction.isPressed(),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onSelect(command) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = labelFor(command),
            style = ShizziTheme.typography.caption,
            color = if (isSelected) colors.onPrimary else colors.onSurfaceMuted,
        )
    }
}

private fun labelFor(command: AutomationCommand): String = when (command) {
    AutomationCommand.START -> "START"
    AutomationCommand.STOP -> "STOP"
    AutomationCommand.TOGGLE -> "TOGGLE"
    AutomationCommand.QUERY_STATUS -> "STATUS"
}
