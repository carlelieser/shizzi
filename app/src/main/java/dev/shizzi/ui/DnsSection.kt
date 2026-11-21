package dev.shizzi.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import dev.shizzi.DnsParse
import dev.shizzi.R
import dev.shizzi.parseDnsServers
import dev.shizzi.ui.theme.ShizziTheme

@Composable
fun DnsSection(servers: String, onSave: (String) -> Unit) {
    var isEditorOpen by remember { mutableStateOf(false) }

    SettingsChoice(
        label = SettingsText(title = stringResource(R.string.dns)),
        value = servers.ifBlank { stringResource(R.string.dns_automatic) },
        onClick = { isEditorOpen = true },
    )

    if (!isEditorOpen) return

    DnsEditor(
        initial = servers,
        onSave = onSave,
        onDismiss = { isEditorOpen = false },
    )
}

/** Saves every edit that parses, so the setting always holds the last valid list. */
@Composable
private fun DnsEditor(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val parsed = parseDnsServers(text)
    val edit = { updated: String ->
        text = updated
        val result = parseDnsServers(updated)
        if (result is DnsParse.Valid) onSave(result.servers.joinToString(", ") { it.hostAddress.orEmpty() })
    }

    ThemedBottomSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.dns),
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        Text(
            text = stringResource(R.string.dns_servers_description),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(top = ShizziTheme.spacing.sm, bottom = ShizziTheme.spacing.md),
        )

        DnsField(text = text, invalidEntry = (parsed as? DnsParse.Invalid)?.entry, onChange = edit)

        Spacer(Modifier.height(ShizziTheme.spacing.lg))
    }
}

@Composable
private fun DnsField(text: String, invalidEntry: String?, onChange: (String) -> Unit) {
    ThemedTextField(
        value = text,
        onValueChange = onChange,
        decoration = TextFieldDecoration(
            placeholder = stringResource(R.string.dns_servers_hint),
            error = invalidEntry?.let { stringResource(R.string.dns_server_invalid, it) },
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri),
            trailing = if (text.isEmpty()) null else {
                {
                    ShizziIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear),
                        onClick = { onChange("") },
                        tint = ShizziTheme.colors.onSurfaceMuted,
                    )
                }
            },
        ),
    )
}
