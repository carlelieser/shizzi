package dev.shizzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.shizzi.BuildConfig
import dev.shizzi.SessionUiState
import dev.shizzi.UiStatus
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme

/** The rule between segments, sized to the caption text beside it. */
private val DividerWidth = 1.dp
private val DividerHeight = 12.dp

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
 * which carries things like the raw interface name — true, and not a status.
 */
@Composable
fun StatusRow(state: SessionUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(ScreenPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLabel(statusWord(state.status))

        // Only while a session is up: this describes the tunnel carrying
        // traffic, and leaving it under an idle screen would claim a session
        // that ended.
        if (state.status == UiStatus.CONNECTED && state.interfaceName.isNotEmpty()) {
            StatusDivider()
            TunnelSegment(state.interfaceName)
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
 * What the tunnel says it is, with its real name a tap away.
 *
 * The interface name is assigned by the framework — TestNetworkService
 * hardcodes a "testtun" prefix and appends a counter, and neither
 * createTunInterface overload accepts a name — so it cannot be made to say
 * anything friendlier at the source. It is also meaningless to anyone not
 * reading Android internals, which is why the default here is a phrase.
 *
 * It stays reachable rather than hidden: it is the string that matches
 * `dumpsys tethering` output, so it is exactly what someone filing a bug
 * needs. Tapping toggles between the two.
 */
@Composable
private fun TunnelSegment(name: String) {
    // Keyed on the interface, so a new session starts at the phrase rather
    // than inheriting the previous one's expanded state and showing a name the
    // user never asked to see.
    var isShowingName by remember(name) { mutableStateOf(false) }

    // No ripple and no press colour: this sits in a row of muted captions, and
    // an indication here would make it the loudest thing on an idle screen.
    val interaction = remember { MutableInteractionSource() }

    Text(
        text = if (isShowingName) name.uppercase() else "TUNNEL ACTIVE",
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.clickable(
            interactionSource = interaction,
            indication = null,
        ) {
            isShowingName = !isShowingName
        },
    )
}

/**
 * Separates the segments.
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
