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
import dev.shizzi.ui.theme.themedIndication

private val DividerWidth = 1.dp
private val DividerHeight = 12.dp

private const val RevealTapCount = 3
private const val RevealWindowMillis = 1200L

@Composable
fun StatusRow(
    state: SessionUiState,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(ScreenPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLabel(statusWord(state.status))

        if (state.status == UiStatus.CONNECTED && state.interfaceName.isNotEmpty()) {
            StatusDivider()
            TunnelSegment(state.interfaceName)
        }

        StatusDivider()
        VersionSegment(onReveal = onVersionClick)
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

@Composable
private fun VersionSegment(onReveal: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val taps = remember { TapCounter(RevealTapCount, RevealWindowMillis) }

    Box(
        modifier = Modifier.clickable(
            interactionSource = interaction,
            indication = themedIndication(),
        ) {
            if (taps.record(System.currentTimeMillis())) onReveal()
        },
    ) {
        StatusText("v${BuildConfig.VERSION_NAME}")
    }
}

/** Counts taps toward [target], resetting once [windowMillis] lapses between them. */
private class TapCounter(private val target: Int, private val windowMillis: Long) {
    private var count = 0
    private var lastTapAt = 0L

    fun record(now: Long): Boolean {
        count = if (now - lastTapAt > windowMillis) 1 else count + 1
        lastTapAt = now

        if (count < target) return false

        count = 0
        return true
    }
}

@Composable
private fun TunnelSegment(name: String) {

    var isShowingName by remember(name) { mutableStateOf(false) }

    val interaction = remember { MutableInteractionSource() }

    Text(
        text = if (isShowingName) name.uppercase() else "TUNNEL ACTIVE",
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.clickable(
            interactionSource = interaction,
            indication = themedIndication(),
        ) {
            isShowingName = !isShowingName
        },
    )
}

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

private fun statusWord(status: UiStatus): String = when (status) {
    UiStatus.READY -> "Ready"
    UiStatus.LOADING -> "Starting"
    UiStatus.CONNECTED -> "Connected"
    UiStatus.ERROR -> "Failed"
}
