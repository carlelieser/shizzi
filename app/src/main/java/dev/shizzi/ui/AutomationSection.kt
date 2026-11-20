package dev.shizzi.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import dev.shizzi.R
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
            title = stringResource(R.string.automation),
            subtitle = stringResource(R.string.automation_description),
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
    val resources = LocalContext.current.resources
    val tokenLabel = stringResource(R.string.token)
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        SettingsAction(
            label = SettingsText(title = stringResource(R.string.view_setup_instructions)),
            onClick = { isExpanded = true },
        )

        TokenCard(
            token = state.token,
            actions = TokenActions(
                onCopy = {
                    clipboard.setText(AnnotatedString(state.token))
                    toasts.show(copiedToast(resources, tokenLabel))
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
