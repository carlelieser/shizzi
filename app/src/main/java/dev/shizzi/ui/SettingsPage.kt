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
 * Enough to read as unavailable, not so far the user loses their place — the
 * run's toast sits over this, and a page faded to nothing would make it look
 * like a dialog on an empty screen.
 */
private const val BusyAlpha = 0.4f

/** Grouped so the page takes state rather than four values. */
data class SettingsState(
    val shizuku: ShizukuState,
    val theme: ThemeChoice,
    val isLogging: Boolean,
    val isRunningDiagnostics: Boolean,
)

/** Likewise, so the page stays within the parameter limit. */
data class SettingsActions(
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetLogging: (Boolean) -> Unit,
    val onOpenLog: () -> Unit,
    val onRunProbes: () -> Unit,
    val onRequestPermission: () -> Unit,
)

/**
 * Everything configurable, plus the Shizuku detail the home badge cannot hold.
 * Scrolls, since four sections already exceed a short screen.
 */
@Composable
fun SettingsPage(
    state: SettingsState,
    actions: SettingsActions,
    onBack: () -> Unit,
) {
    val isBusy = state.isRunningDiagnostics

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        // Outside the dimmed region: a run lasts as long as upstream selection
        // takes to settle, and the ViewModel owns it, so leaving is free.
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
 * At the boundary rather than an `isEnabled` per row: this screen has three
 * kinds of control, and disabling them individually means every future row has
 * to remember to opt in. Consumed in the initial pass, so events never reach
 * the children at all.
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
 * Titles only: these name things a developer already knows, so a description
 * under each just restated the title at greater length.
 */
@Composable
private fun DeveloperSection(isLogging: Boolean, actions: SettingsActions) {
    SettingsToggle(
        label = SettingsText(title = "Logging"),
        isChecked = isLogging,
        onCheckedChange = actions.onSetLogging,
    )

    // Under the toggle that decides whether it records anything.
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
 * Opened from the context rather than a callback threaded from the ViewModel:
 * no state changes, so routing it upward adds a hop that forwards an intent.
 *
 * Only Author keeps a subtitle, where it names the destination rather than
 * restating what the title and the external-link glyph already carry.
 */
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
