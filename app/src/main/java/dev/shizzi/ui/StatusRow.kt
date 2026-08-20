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
 * The bottom-edge status line: what the session is, then the build.
 *
 * Only the state takes full contrast, so the eye lands on the one word that
 * changes without the row competing with the button. Always present, unlike the
 * detail it replaced, which hid whenever it had nothing to say and took the
 * version with it.
 */
@Composable
fun StatusRow(state: SessionUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(ScreenPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLabel(statusWord(state.status))

        // Only while a session is up, or it claims one that ended.
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
 * One Text rather than two composables: the caption carries letter-spacing, so
 * splitting them puts the trailing track between the words and opens a gap
 * wider than the space it should be.
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
 * A phrase by default, the real name a tap away.
 *
 * The framework assigns the name — TestNetworkService hardcodes a "testtun"
 * prefix and a counter, and no createTunInterface overload accepts one — so it
 * cannot be made friendlier at the source, and it means nothing to anyone not
 * reading Android internals. It stays reachable because it is the string that
 * matches `dumpsys tethering`, which is what someone filing a bug needs.
 */
@Composable
private fun TunnelSegment(name: String) {
    // Keyed on the interface, so a new session starts at the phrase rather than
    // inheriting the last one's expanded state.
    var isShowingName by remember(name) { mutableStateOf(false) }

    // No ripple or press colour: in a row of muted captions, an indication here
    // would be the loudest thing on an idle screen.
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
 * Drawn rather than a "|" character, which carries the font's own spacing and
 * sits off-centre against uppercase caption text.
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
 * Not the session's `detail`, which carries things like the raw interface name
 * — true, and not a status. An error's text belongs to the toast.
 */
private fun statusWord(status: UiStatus): String = when (status) {
    UiStatus.READY -> "Ready"
    UiStatus.LOADING -> "Starting"
    UiStatus.CONNECTED -> "Connected"
    UiStatus.ERROR -> "Failed"
}
