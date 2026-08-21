package dev.shizzi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.ShizukuState
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.ThemeChoice

private const val SOURCE_URL = "https://github.com/carlelieser/shizzi"
private const val ISSUE_URL = "https://github.com/carlelieser/shizzi/issues/new"
private const val AUTHOR_URL = "https://carlelieser.dev"

private const val BusyAlpha = 0.4f

data class SettingsState(
    val shizuku: ShizukuState,
    val theme: ThemeChoice,
    val isLogging: Boolean,
    val isRunningDiagnostics: Boolean,
)

data class SettingsActions(
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onOpenLog: () -> Unit,
    val onRunProbes: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onRestartOnboarding: () -> Unit,
)

@Composable
fun SettingsPage(
    state: SettingsState,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    val isBusy = state.isRunningDiagnostics

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        ScreenHeader(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState(), enabled = !isBusy)
                .alpha(if (isBusy) BusyAlpha else 1f)
                .inert(isBusy)
                .padding(horizontal = ScreenPadding),
        ) {
            SectionLabel("Shizuku")
            ShizukuCard(state = state.shizuku, onGrant = actions.onRequestPermission)

            SectionLabel("Appearance")
            ThemePicker(selected = state.theme, onSelect = actions.onSetTheme)

            SectionLabel("Developer")
            DeveloperSection(isLogging = state.isLogging, actions = actions)

            SectionLabel("About")
            AboutSection()

            Spacer(Modifier.height(ShizziTheme.spacing.xxl))
        }
    }
}

private fun Modifier.inert(isBusy: Boolean): Modifier = when {
    !isBusy -> this
    else -> this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .forEach { it.consume() }
            }
        }
    }
}

@Composable
private fun DeveloperSection(isLogging: Boolean, actions: SettingsActions) {
    SettingsToggle(
        label = SettingsText(title = "Logging"),
        isChecked = isLogging,
        onCheckedChange = actions.onSetLogging,
    )

    SettingsAction(
        label = SettingsText(title = "View logs"),
        onClick = actions.onOpenLog,
    )

    SettingsAction(
        label = SettingsText(title = "Run diagnostics"),
        onClick = actions.onRunProbes,
    )

    SettingsAction(
        label = SettingsText(title = "Restart onboarding"),
        onClick = actions.onRestartOnboarding,
    )
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    SettingsAction(
        label = SettingsText(title = "GitHub"),
        isExternal = true,
        onClick = { context.openUrl(SOURCE_URL) },
    )

    SettingsAction(
        label = SettingsText(title = "Report a bug"),
        isExternal = true,
        onClick = { context.openUrl(ISSUE_URL) },
    )

    SettingsAction(
        label = SettingsText(title = "Author", subtitle = "carlelieser.dev"),
        isExternal = true,
        onClick = { context.openUrl(AUTHOR_URL) },
    )
}
