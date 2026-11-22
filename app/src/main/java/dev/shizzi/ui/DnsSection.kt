package dev.shizzi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import dev.shizzi.DnsProvider
import dev.shizzi.R
import dev.shizzi.dnsProviderOf
import dev.shizzi.parseDnsServers
import dev.shizzi.ui.theme.ShizziTheme

private val ENTRY_SEPARATOR = Regex("""[\s,]+""")

@Composable
fun DnsSection(servers: String, onSave: (String) -> Unit) {
    var isEditorOpen by remember { mutableStateOf(false) }

    SettingsChoice(
        label = SettingsText(title = stringResource(R.string.dns)),
        value = dnsProviderOf(servers)?.label ?: servers.ifBlank { stringResource(R.string.dns_automatic) },
        onClick = { isEditorOpen = true },
    )

    if (!isEditorOpen) return

    DnsEditor(
        initial = servers,
        onSave = onSave,
        onDismiss = { isEditorOpen = false },
    )
}

/**
 * Picking Auto or a provider saves at once; custom text saves on every edit
 * that parses, so the setting always holds the last valid choice.
 */
@Composable
private fun DnsEditor(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val initialProvider = dnsProviderOf(initial)
    var provider by remember { mutableStateOf(initialProvider) }
    var text by remember { mutableStateOf(if (initialProvider == null) initial else "") }
    val pick = { picked: DnsProvider? ->
        provider = picked
        text = ""
        onSave(picked?.servers.orEmpty())
    }
    val edit = { updated: String ->
        text = updated
        provider = null
        if (parseDnsServers(updated) is DnsParse.Valid) onSave(normalizedEntries(updated))
    }

    ThemedBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.dns),
                style = ShizziTheme.typography.heading,
                color = ShizziTheme.colors.onSurface,
            )

            DnsChoices(
                selection = DnsSelection(provider = provider, isAuto = provider == null && text.isEmpty()),
                onPick = pick,
            )

            Text(
                text = stringResource(R.string.dns_custom),
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
                modifier = Modifier.padding(top = ShizziTheme.spacing.md, bottom = ShizziTheme.spacing.md),
            )

            DnsField(text = text, invalidEntry = (parseDnsServers(text) as? DnsParse.Invalid)?.entry, onChange = edit)

            Spacer(Modifier.height(ShizziTheme.spacing.lg))
        }
    }
}

private data class DnsSelection(val provider: DnsProvider?, val isAuto: Boolean)

@Composable
private fun DnsChoices(selection: DnsSelection, onPick: (DnsProvider?) -> Unit) {
    Column(modifier = Modifier.selectableGroup().padding(top = ShizziTheme.spacing.sm)) {
        DnsOption(
            text = DnsOptionText(stringResource(R.string.dns_automatic), stringResource(R.string.dns_auto_description)),
            isSelected = selection.isAuto,
            onSelect = { onPick(null) },
        ) {
            Icon(
                imageVector = Icons.Filled.AutoMode,
                contentDescription = null,
                tint = ShizziTheme.colors.onSurface,
            )
        }

        DnsProvider.entries.forEach { candidate ->
            DnsProviderOption(
                provider = candidate,
                isSelected = candidate == selection.provider,
                onSelect = { onPick(candidate) },
            )
        }
    }
}

private fun normalizedEntries(text: String): String =
    text.split(ENTRY_SEPARATOR).filter(String::isNotEmpty).joinToString(", ")

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
