package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

data class AutomationState(
    val isEnabled: Boolean,
    val token: String,
)

data class AutomationActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onRegenerateToken: () -> Unit,
)

@Composable
fun AutomationSection(
    state: AutomationState,
    actions: AutomationActions,
    toasts: ToastState,
) {
    SettingsToggle(
        label = SettingsText(
            title = "Automation",
            subtitle = "Allows tasker apps to manage Shizzi",
        ),
        isChecked = state.isEnabled,
        onCheckedChange = actions.onSetEnabled,
    )

    if (!state.isEnabled) return

    val clipboard = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }

    TokenCard(
        token = state.token,
        actions = TokenActions(
            onCopy = {
                clipboard.setText(AnnotatedString(state.token))
                toasts.show(copiedToast("Token"))
            },
            onRegenerate = actions.onRegenerateToken,
        ),
    )

    SetupButton(onClick = { isExpanded = true })

    if (!isExpanded) return

    AutomationSetupDialog(
        token = state.token,
        toasts = toasts,
        onDismiss = { isExpanded = false },
    )
}
