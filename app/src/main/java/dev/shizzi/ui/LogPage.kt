package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.LogEntry
import dev.shizzi.LogLevel
import dev.shizzi.SessionLog
import dev.shizzi.ui.theme.MinTouchTarget
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GutterWidth = 44.dp

private val SelectionEdge = 3.dp
private val RuleWidth = 1.dp

private const val SelectionTint = 0.12f

private val JumpBandHeight = 96.dp

private val EmptyIconSize = 64.dp

private const val JumpThreshold = 8

@Immutable
data class LogEntries(
    val entries: List<LogEntry>,
    val isLoaded: Boolean,
    val reload: () -> Unit,
)

@Immutable
data class LogActions(
    val onClear: (onCleared: (String?) -> Unit) -> Unit,
    val onEnableLogging: () -> Unit,
    val onStartSession: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun rememberLogEntries(): LogEntries {
    var entries by remember { mutableStateOf(emptyList<LogEntry>()) }
    var isLoaded by remember { mutableStateOf(false) }

    var generation by remember { mutableIntStateOf(0) }

    LaunchedEffect(generation) {
        entries = withContext(Dispatchers.IO) { SessionLog.merged().asReversed() }
        isLoaded = true
    }

    return LogEntries(entries, isLoaded) { generation++ }
}

@Composable
fun LogPage(
    log: LogEntries,
    toasts: ToastState,
    isLogging: Boolean,
    actions: LogActions,
) {
    val entries = log.entries
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var isConfirmingClear by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    ClearLogToast(
        isConfirming = isConfirmingClear,
        toasts = toasts,
        onConfirm = {
            isConfirmingClear = false
            actions.onClear { problem ->
                selected = emptySet()
                log.reload()
                toasts.show(clearedToast(problem))
            }
        },
        onCancel = { isConfirmingClear = false },
    )

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(
            title = "Log",
            onBack = actions.onBack,
            action = {

                if (log.isLoaded && entries.isNotEmpty()) {
                    val isAllSelected = selected.size == entries.size

                    OverflowMenu(isMarked = selected.isNotEmpty()) {
                        OverflowItem(

                            label = copyLabel(selected.size, entries.size),
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(copyText(entries, selected)),
                                )
                                selected = emptySet()
                            },
                        )

                        OverflowItem(
                            label = when {
                                isAllSelected -> "DESELECT ALL"
                                else -> "SELECT ALL"
                            },
                            onClick = {
                                selected = when {
                                    isAllSelected -> emptySet()
                                    else -> entries.indices.toSet()
                                }
                            },
                        )

                        OverflowItem(
                            label = "CLEAR",
                            onClick = { isConfirmingClear = true },
                        )
                    }
                }
            },
        )

        if (!log.isLoaded) return@Column

        if (entries.isEmpty()) {
            EmptyLog(
                isLogging = isLogging,
                onEnableLogging = actions.onEnableLogging,
                onStartSession = actions.onStartSession,
            )
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(entries) { index, entry ->
                    LogRow(
                        number = index + 1,
                        entry = entry,
                        isSelected = index in selected,
                        onToggle = {
                            selected = if (index in selected) {
                                selected - index
                            } else {
                                selected + index
                            }
                        },
                    )
                }
            }

            JumpBand(
                edge = JumpEdge.TOP,
                listState = listState,
                count = entries.size,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            JumpBand(
                edge = JumpEdge.BOTTOM,
                listState = listState,
                count = entries.size,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private enum class JumpEdge { TOP, BOTTOM }

@Composable
private fun JumpBand(
    edge: JumpEdge,
    listState: LazyListState,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val colors = ShizziTheme.colors
    val isTop = edge == JumpEdge.TOP

    val isShowing by remember(count, edge) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val distance = when {

                isTop -> visible.firstOrNull()?.index ?: 0
                else -> count - 1 - (visible.lastOrNull()?.index ?: 0)
            }
            distance > JumpThreshold
        }
    }

    val fade = listOf(colors.background, colors.background.copy(alpha = 0f))

    AnimatedVisibility(
        visible = isShowing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(JumpBandHeight)
                .background(
                    Brush.verticalGradient(if (isTop) fade else fade.asReversed()),
                ),
            contentAlignment = if (isTop) Alignment.TopCenter else Alignment.BottomCenter,
        ) {

            Text(
                text = if (isTop) "SCROLL TO TOP" else "SCROLL TO BOTTOM",
                style = ShizziTheme.typography.label,
                color = colors.onSurface,
                modifier = Modifier

                    .padding(
                        top = if (isTop) ShizziTheme.spacing.lg else 0.dp,
                        bottom = if (isTop) 0.dp else ShizziTheme.spacing.lg,
                    )
                    .clickable {

                        scope.launch {
                            listState.animateScrollToItem(if (isTop) 0 else count - 1)
                        }
                    }
                    .padding(ShizziTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun LogRow(
    number: Int,
    entry: LogEntry,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = ShizziTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) colors.primary.copy(alpha = SelectionTint) else colors.background,
            )
            .clickable(onClick = onToggle)

            .drawBehind {
                val rule = GutterWidth.toPx()
                drawLine(
                    color = colors.onSurfaceMuted.copy(alpha = 0.3f),
                    start = Offset(rule, 0f),
                    end = Offset(rule, size.height),
                    strokeWidth = RuleWidth.toPx(),
                )

                if (isSelected) {
                    drawRect(
                        color = colors.primary,
                        size = size.copy(width = SelectionEdge.toPx()),
                    )
                }
            }
            .padding(vertical = ShizziTheme.spacing.xs),
    ) {
        Text(
            text = "$number",
            style = ShizziTheme.typography.log,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(GutterWidth)
                .padding(end = ShizziTheme.spacing.sm),
        )

        LogText(entry = entry, modifier = Modifier.padding(horizontal = ShizziTheme.spacing.sm))
    }
}

@Composable
private fun LogText(entry: LogEntry, modifier: Modifier = Modifier) {
    val colors = ShizziTheme.colors
    val style = ShizziTheme.typography.log

    Text(
        text = buildString {

            append(entry.timestamp.substringAfter(' ').ifEmpty { entry.timestamp })
            if (isNotEmpty()) append("  ")
            append(entry.message)
        },
        style = style.copy(
            fontWeight = if (entry.level == LogLevel.INFO) FontWeight.W400 else FontWeight.W700,
        ),
        color = if (entry.level == LogLevel.INFO) colors.onSurfaceMuted else colors.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun EmptyLog(
    isLogging: Boolean,
    onEnableLogging: () -> Unit,
    onStartSession: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.TextSnippet,
            contentDescription = null,
            tint = ShizziTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(EmptyIconSize),
        )

        Spacer(Modifier.height(ShizziTheme.spacing.xl))

        Text(
            text = if (isLogging) "No logs yet" else "Logging is disabled",
            style = ShizziTheme.typography.subheading,
            color = ShizziTheme.colors.onSurface,
        )

        if (isLogging) {
            Spacer(Modifier.height(ShizziTheme.spacing.sm))

            Text(
                text = "Logs will appear here",
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(ShizziTheme.spacing.sm))

        EmptyAction(
            label = if (isLogging) "Start a session" else "Enable logging",
            onClick = if (isLogging) onStartSession else onEnableLogging,
        )
    }
}

@Composable
private fun EmptyAction(label: String, onClick: () -> Unit) {
    Box(

        modifier = Modifier
            .height(MinTouchTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.primary,
        )
    }
}

private fun copyLabel(count: Int, total: Int): String = when {
    count == 0 || count == total -> "COPY ALL"
    count == 1 -> "COPY 1 LINE"
    else -> "COPY $count LINES"
}

private fun copyText(entries: List<LogEntry>, selected: Set<Int>): String = entries
    .filterIndexed { index, _ -> selected.isEmpty() || index in selected }
    .joinToString("\n") { entry ->
        "${entry.timestamp} ${entry.level.name.padEnd(5)} ${entry.message}"
    }
