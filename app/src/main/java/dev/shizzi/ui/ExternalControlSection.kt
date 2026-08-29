package dev.shizzi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.shizzi.ui.theme.ShizziTheme

data class ExternalControlState(
    val isEnabled: Boolean,
    val token: String,
)

data class ExternalControlActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onRegenerateToken: () -> Unit,
    val onClearToken: () -> Unit,
)

@Composable
fun ExternalControlSection(
    state: ExternalControlState,
    actions: ExternalControlActions,
) {
    SettingsToggle(
        label = SettingsText(
            title = "Allow external control",
            subtitle = "Let automation apps start and stop tethering",
        ),
        isChecked = state.isEnabled,
        onCheckedChange = actions.onSetEnabled,
    )

    if (!state.isEnabled) return

    TokenRow(token = state.token, actions = actions)
}

@Composable
private fun TokenRow(token: String, actions: ExternalControlActions) {
    val clipboard = LocalClipboardManager.current

    if (token.isEmpty()) {
        SettingsAction(
            label = SettingsText(
                title = "Require a token",
                subtitle = "Any app on this device can trigger Shizzi until you set one",
            ),
            onClick = actions.onRegenerateToken,
        )
        return
    }

    SettingsAction(
        label = SettingsText(title = "Copy token", subtitle = token),
        onClick = { clipboard.setText(AnnotatedString(token)) },
    )

    SettingsAction(
        label = SettingsText(title = "Regenerate token"),
        onClick = actions.onRegenerateToken,
    )

    SettingsAction(
        label = SettingsText(title = "Remove token"),
        onClick = actions.onClearToken,
    )
}

@Composable
fun ExternalControlHint() {
    Text(
        text = "Send dev.shizzi.action.TOGGLE to dev.shizzi/.ExternalControlReceiver.",
        style = ShizziTheme.typography.body,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.fillMaxWidth().padding(top = ShizziTheme.spacing.sm),
    )
}
