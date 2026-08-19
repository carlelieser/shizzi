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
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.ShizukuState
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.ThemeChoice

private const val SOURCE_URL = "https://github.com/carlelieser/shizzi"
private const val ISSUE_URL = "https://github.com/carlelieser/shizzi/issues/new"
private const val AUTHOR_URL = "https://carlelieser.dev"

/** What the settings screen renders, so the page takes state rather than four values. */
data class SettingsState(
    val shizuku: ShizukuState,
    val theme: ThemeChoice,
    val isLogging: Boolean,
)

/**
 * The callbacks the settings screen needs, grouped so it stays within the
 * parameter limit alongside the state it renders.
 */
data class SettingsActions(
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onRunProbes: () -> Unit,
    val onRequestPermission: () -> Unit,
)

/**
 * Everything configurable, plus the Shizuku detail the home badge cannot hold.
 *
 * Scrolls: four sections already exceed a short screen, and a list that
 * clipped its last row would hide the links entirely.
 */
@Composable
fun SettingsPage(
    state: SettingsState,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding),
        ) {
            SectionLabel("Shizuku")
            ShizukuCard(state = state.shizuku, onGrant = actions.onRequestPermission)

            SectionLabel("Appearance")
            ThemePicker(selected = state.theme, onSelect = actions.onSetTheme)

            SectionLabel("Diagnostics")
            DiagnosticsSection(isLogging = state.isLogging, actions = actions)

            SectionLabel("About")
            AboutSection()

            // The last row would otherwise sit against the navigation bar.
            Spacer(Modifier.height(ShizziTheme.spacing.xxl))
        }
    }
}

@Composable
private fun DiagnosticsSection(isLogging: Boolean, actions: SettingsActions) {
    SettingsToggle(
        label = SettingsText(
            title = "Logging",
            subtitle = "Records session activity to the log",
        ),
        isChecked = isLogging,
        onCheckedChange = actions.onSetLogging,
    )

    SettingsAction(
        label = SettingsText(
            title = "Run diagnostics",
            subtitle = "Runs the full probe sequence and writes a report",
        ),
        onClick = actions.onRunProbes,
    )
}

/**
 * The three outbound links.
 *
 * Opened from the context rather than through a callback threaded from the
 * ViewModel: there is no state to change and nothing to decide, so routing it
 * upward would add a hop that only forwards an intent.
 */
@Composable
private fun AboutSection() {
    val context = LocalContext.current

    SettingsAction(
        label = SettingsText(title = "Source", subtitle = "View the code on GitHub"),
        isExternal = true,
        onClick = { context.openUrl(SOURCE_URL) },
    )

    SettingsAction(
        label = SettingsText(title = "Report a bug", subtitle = "Open an issue"),
        isExternal = true,
        onClick = { context.openUrl(ISSUE_URL) },
    )

    SettingsAction(
        label = SettingsText(title = "Author", subtitle = "carlelieser.dev"),
        isExternal = true,
        onClick = { context.openUrl(AUTHOR_URL) },
    )
}
