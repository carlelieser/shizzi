package dev.shizzi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.shizzi.BuildConfig
import dev.shizzi.R
import dev.shizzi.SessionUiState
import dev.shizzi.UiStatus
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.currentLocale
import dev.shizzi.ui.theme.standardTween
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
    val status = statusWord(state)
    var isStatusExpanded by remember(status) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.padding(ScreenPadding)) {
            StatusSegments(state, onExpandStatus = { isStatusExpanded = true }, onVersionClick)
        }

        AnimatedVisibility(
            visible = isStatusExpanded,
            enter = fadeIn(standardTween()),
            exit = fadeOut(standardTween()),
            modifier = Modifier.matchParentSize(),
        ) {
            ExpandedStatus(statusText(status), onCollapse = { isStatusExpanded = false })
        }
    }
}

@Composable
private fun StatusSegments(state: SessionUiState, onExpandStatus: () -> Unit, onVersionClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLabel(statusWord(state), Modifier.weight(1f, fill = false), onExpand = onExpandStatus)

        AnimatedVisibility(
            visible = hasTunnel(state),
            enter = fadeIn(standardTween()) + expandHorizontally(standardTween()),
            exit = fadeOut(standardTween()) + shrinkHorizontally(standardTween()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDivider()
                TunnelSegment(state.interfaceName)
            }
        }

        StatusDivider()
        VersionSegment(onReveal = onVersionClick)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text.uppercase(currentLocale()),
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        softWrap = false,
    )
}

private fun hasTunnel(state: SessionUiState): Boolean =
    state.status == UiStatus.CONNECTED && state.interfaceName.isNotEmpty()

@Composable
private fun statusText(status: String): AnnotatedString {
    val emphasis = SpanStyle(color = ShizziTheme.colors.onSurface, fontWeight = FontWeight.W700)

    return buildAnnotatedString {
        append(stringResource(R.string.status).uppercase(currentLocale()) + " ")
        withStyle(emphasis) { append(status.uppercase(currentLocale())) }
    }
}

/** The only segment that gives way: a long status truncates, and a tap shows it in full over the row. */
@Composable
private fun StatusLabel(status: String, modifier: Modifier, onExpand: () -> Unit) {
    val fadeSpec = standardTween<Float>()
    var isTruncated by remember(status) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
        label = "statusWord",
        modifier = modifier.clickable(
            enabled = isTruncated,
            interactionSource = interaction,
            indication = themedIndication(),
            onClick = onExpand,
        ),
    ) { word ->
        Text(
            text = statusText(word),
            style = ShizziTheme.typography.caption,
            color = ShizziTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout -> if (word == status) isTruncated = layout.isTruncated() },
        )
    }
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
        text = if (isShowingName) name.uppercase() else stringResource(R.string.tunnel_active),
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        softWrap = false,
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

@Composable
@ReadOnlyComposable
private fun loadingWord(state: SessionUiState): String = when {
    state.isWaitingForShizuku -> stringResource(R.string.status_waiting_for_shizuku)
    state.isReconnecting -> stringResource(R.string.status_reconnecting)
    else -> stringResource(R.string.status_starting)
}

@Composable
@ReadOnlyComposable
private fun statusWord(state: SessionUiState): String = when (state.status) {
    UiStatus.CONNECTED -> stringResource(
        if (state.isWaitingForWired) R.string.waiting_for_wired else R.string.status_connected,
    )
    UiStatus.READY -> stringResource(R.string.ready)
    UiStatus.LOADING -> loadingWord(state)
    UiStatus.ERROR -> stringResource(R.string.status_failed)
}

private fun TextLayoutResult.isTruncated(): Boolean =
    multiParagraph.intrinsics.maxIntrinsicWidth > size.width
