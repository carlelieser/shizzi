package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import dev.shizzi.Automation
import dev.shizzi.AutomationCommand
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

private data class SetupField(val label: String, val value: String)

@Composable
fun AutomationSetupDialog(token: String, toasts: ToastState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .brutalSurface(fill = ShizziTheme.colors.surface)
                .verticalScroll(rememberScrollState())
                .padding(ShizziTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
        ) {
            SetupContent(token = token, toasts = toasts)
        }
    }
}

@Composable
private fun ColumnScope.SetupContent(token: String, toasts: ToastState) {
    var command by remember { mutableStateOf(AutomationCommand.START) }

    Text(
        text = "Setup",
        style = ShizziTheme.typography.heading,
        color = ShizziTheme.colors.onSurface,
    )

    Text(
        text = "Send a broadcast intent with these values.",
        style = ShizziTheme.typography.body,
        color = ShizziTheme.colors.onSurfaceMuted,
    )

    CommandTabs(selected = command, onSelect = { command = it })

    setupFields(command, token).forEach { field ->
        CopyableField(field = field, toasts = toasts)
    }
}

private fun setupFields(command: AutomationCommand, token: String) = listOf(
    SetupField("Action", Automation.actionFor(command)),
    SetupField("Package", "dev.shizzi"),
    SetupField("Class", "dev.shizzi.AutomationReceiver"),
    SetupField("Target", "Broadcast receiver"),
    SetupField("Extra name", Automation.EXTRA_TOKEN),
    SetupField("Extra value", token),
)

@Composable
private fun CopyableField(field: SetupField, toasts: ToastState) {
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
        ) {
            Text(
                text = field.label.uppercase(),
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
            )

            Text(
                text = field.value,
                style = ShizziTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                color = ShizziTheme.colors.onSurface,
            )
        }

        ShizziCompactIconButton(
            icon = Icons.Filled.ContentCopy,
            contentDescription = "Copy ${field.label.lowercase()}",
            onClick = {
                clipboard.setText(AnnotatedString(field.value))
                toasts.show(copiedToast(field.label))
            },
        )
    }
}
