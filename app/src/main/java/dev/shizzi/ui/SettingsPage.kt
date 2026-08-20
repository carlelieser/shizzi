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

/**
 * How far the screen dims while diagnostics run.
 *
 * Enough to read as unavailable, not so far that the user cannot see what they
 * were looking at — the toast reporting the run sits over this, and a screen
 * faded to nothing would make it look like a dialog on an empty page.
 */
private const val BusyAlpha = 0.4f

/** What the settings screen renders, so the page takes state rather than four values. */
data class SettingsState(
    val shizuku: ShizukuState,
    val theme: ThemeChoice,
    val isLogging: Boolean,
    val isRunningDiagnostics: Boolean,
)

/**
 * The callbacks the settings screen needs, grouped so it stays within the
 * parameter limit alongside the state it renders.
 */
data class SettingsActions(
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onOpenLog: () -> Unit,
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
    val isBusy = state.isRunningDiagnostics

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // Outside the dimmed region: a run takes as long as upstream selection
        // takes to settle, and stranding the user on this screen for that long
        // would be a worse bargain than letting them leave it. The run belongs
        // to the ViewModel, so navigating away does not abandon it.
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

            // The last row would otherwise sit against the navigation bar.
            Spacer(Modifier.height(ShizziTheme.spacing.xxl))
        }
    }
}

/**
 * Swallows every pointer event over this subtree while [isBusy].
 *
 * Done here rather than by threading an `isEnabled` through each row: there are
 * three kinds of control on this screen — a switch, tappable rows, and a radio
 * group — and disabling them individually means every future row has to
 * remember to opt in. Consuming at the boundary cannot be forgotten.
 *
 * Consumes in the initial pass, so the events never reach the children rather
 * than being handled and then undone.
 */
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

/**
 * Titles only.
 *
 * The three rows name things a developer already knows — a log, a switch that
 * fills it, a probe run — so a description under each restated the title in a
 * longer sentence. The About rows keep theirs, where the subtitle carries the
 * destination rather than a gloss.
 */
@Composable
private fun DeveloperSection(isLogging: Boolean, actions: SettingsActions) {
    SettingsToggle(
        label = SettingsText(title = "Logging"),
        isChecked = isLogging,
        onCheckedChange = actions.onSetLogging,
    )

    // Directly under the toggle that decides whether it records anything, so
    // the switch and what it fills sit together.
    SettingsAction(
        label = SettingsText(title = "View logs"),
        onClick = actions.onOpenLog,
    )

    SettingsAction(
        label = SettingsText(title = "Run diagnostics"),
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
