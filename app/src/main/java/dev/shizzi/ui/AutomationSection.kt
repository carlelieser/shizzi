package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import dev.shizzi.ui.theme.standardTween

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
            title = str(R.string.automation),
            subtitle = str(R.string.allow_apps_like_tasker_to_manage_shizzi),
        ),
        isChecked = state.isEnabled,
        onCheckedChange = actions.onSetEnabled,
    )

    AnimatedVisibility(
        visible = state.isEnabled,
        enter = fadeIn(standardTween()) + expandVertically(standardTween()),
        exit = fadeOut(standardTween()) + shrinkVertically(standardTween()),
    ) {
        AutomationDetails(
            state = state,
            actions = actions,
            toasts = toasts,
        )
    }
}

@Composable
private fun AutomationDetails(
    state: AutomationState,
    actions: AutomationActions,
    toasts: ToastState,
) {
    val clipboard = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        SettingsAction(
            label = SettingsText(title = str(R.string.view_setup_instructions)),
            onClick = { isExpanded = true },
        )

        TokenCard(
            token = state.token,
            actions = TokenActions(
                onCopy = {
                    clipboard.setText(AnnotatedString(state.token))
                    toasts.show(copiedToast(str(R.string.token)))
                },
                onRegenerate = actions.onRegenerateToken,
            ),
        )
    }

    if (!isExpanded) return

    AutomationSetupDialog(
        token = state.token,
        toasts = toasts,
        onDismiss = { isExpanded = false },
    )
}
