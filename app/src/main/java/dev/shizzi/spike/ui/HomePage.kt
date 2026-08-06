package dev.shizzi.spike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.spike.BuildConfig
import dev.shizzi.spike.SpikeUiState
import dev.shizzi.spike.UiStatus
import dev.shizzi.spike.ui.theme.HeaderHeight
import dev.shizzi.spike.ui.theme.ScreenPadding
import dev.shizzi.spike.ui.theme.ShizziTheme

/** The rule between status and version, sized to the caption text beside it. */
private val DividerWidth = 1.dp
private val DividerHeight = 12.dp

/** What the button does, given what the session is doing. */
private fun buttonLabel(status: UiStatus): String =
    if (status == UiStatus.CONNECTED) "Stop" else "Start"

/**
 * How the button presents itself.
 *
 * Derived here rather than inside the button so the rule lives next to the
 * state it reads: only an available start is the primary action.
 */
private fun buttonState(state: SpikeUiState): ConnectButtonState = when {
    state.status == UiStatus.LOADING -> ConnectButtonState.LOADING
    state.status == UiStatus.CONNECTED -> ConnectButtonState.STOP
    state.canStart -> ConnectButtonState.START
    else -> ConnectButtonState.DISABLED
}

/**
 * The screen the app opens on.
 *
 * Three things, vertically: the status glyph, the one button, and — pinned to
 * the bottom edge rather than floating under the button — whatever detail the
 * session reports. Errors and Shizuku prompts are toasts, so nothing here
 * moves when one arrives.
 */
@Composable
fun HomePage(
    state: SpikeUiState,
    actions: HomeActions,
) {
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        HomeHeader(
            state = state,
            onOpenLog = actions.onOpenLog,
            onOpenSettings = actions.onOpenSettings,
        )

        HomeBody(state = state, onToggle = actions.onToggle, onCancel = actions.onCancel)

        SessionDetail(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * The callbacks the home screen needs, grouped so it takes two parameters
 * rather than five.
 */
data class HomeActions(
    val onToggle: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenLog: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Badge on the left, navigation on the right.
 *
 * No title: the app has one home screen and naming it would state the obvious
 * at the largest type size on the page.
 */
@Composable
private fun HomeHeader(
    state: SpikeUiState,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .padding(horizontal = ShizziTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(start = ShizziTheme.spacing.sm)) {
            ShizukuBadge(state.shizukuState)
        }

        Spacer(Modifier.weight(1f))

        ShizziIconButton(
            icon = Icons.AutoMirrored.Filled.TextSnippet,
            contentDescription = "Log",
            onClick = onOpenLog,
        )
        ShizziIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
        )
    }
}

/**
 * The centred stack: glyph, button, and the cancel affordance.
 *
 * Cancel appears only while starting. Stopping is already the fast path and
 * cancelling it would leave the session in the state this app exists to avoid.
 */
@Composable
private fun HomeBody(state: SpikeUiState, onToggle: () -> Unit, onCancel: () -> Unit) {
    val isStarting = state.status == UiStatus.LOADING

    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusIcon(state.status)

        Spacer(Modifier.height(ShizziTheme.spacing.xxxl))

        ConnectButton(
            label = buttonLabel(state.status),
            state = buttonState(state),
            onClick = onToggle,
        )

        // Reserved whether or not it is showing, so the button does not shift
        // up and down as a session starts.
        Box(modifier = Modifier.height(ShizziTheme.spacing.xxxl)) {
            if (isStarting) CancelButton(onClick = onCancel)
        }
    }
}

/**
 * The status line along the bottom edge: what the session is, then the build.
 *
 * Uppercase and mostly muted: it is a status line rather than prose. Only the
 * state itself takes full contrast, so the eye lands on the one word that
 * changes without the row competing with the button for attention.
 *
 * Always present, unlike the detail it replaced, which hid itself whenever it
 * had nothing to say and took the version down with it. The status word is
 * derived from [UiStatus] rather than from the session's own `detail` string,
 * which carries things like "via testtun51" — true, and not a status.
 */
@Composable
private fun SessionDetail(state: SpikeUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(ScreenPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLabel(statusWord(state.status))

        // Only while a session is up: the name is the tunnel carrying traffic,
        // and a stale one under an idle screen claims a session that ended.
        if (state.status == UiStatus.CONNECTED && state.interfaceName.isNotEmpty()) {
            StatusDivider()
            StatusText(state.interfaceName)
        }

        StatusDivider()
        StatusText("v${BuildConfig.VERSION_NAME}")
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text.uppercase(),
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
    )
}

/**
 * "STATUS" muted, the state itself in full contrast.
 *
 * One Text rather than two composables: the caption carries letter-spacing,
 * and splitting the pair would put the word's trailing track between them and
 * open a gap wider than the space it is meant to be.
 */
@Composable
private fun StatusLabel(status: String) {
    val colors = ShizziTheme.colors

    Text(
        text = buildAnnotatedString {
            append("STATUS ")
            withStyle(
                SpanStyle(color = colors.onSurface, fontWeight = FontWeight.W700),
            ) {
                append(status.uppercase())
            }
        },
        style = ShizziTheme.typography.caption,
        color = colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
    )
}

/**
 * Separates the two halves of the status line.
 *
 * A drawn rule rather than a "|" character: the divider should be the height
 * of the text it separates, and a pipe glyph carries the font's own spacing
 * and sits off-centre against uppercase caption text.
 */
@Composable
private fun StatusDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = ShizziTheme.spacing.sm)
            .width(DividerWidth)
            .height(DividerHeight)
            .background(ShizziTheme.colors.onSurfaceMuted.copy(alpha = 0.5f)),
    )
}

/**
 * The one word for each state.
 *
 * Deliberately not the session's `detail`: an error's text belongs to the
 * toast, and repeating it here put the same sentence on screen twice.
 */
private fun statusWord(status: UiStatus): String = when (status) {
    UiStatus.READY -> "Ready"
    UiStatus.LOADING -> "Starting"
    UiStatus.CONNECTED -> "Connected"
    UiStatus.ERROR -> "Failed"
}
