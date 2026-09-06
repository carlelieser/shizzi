package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EmptyRiseDivisor = 8

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
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(standardTween()) +
                    slideInVertically(standardSpring()) { it / EmptyRiseDivisor },
            ) {
                EmptyLog(
                    isLogging = isLogging,
                    onEnableLogging = actions.onEnableLogging,
                    onStartSession = actions.onStartSession,
                )
            }
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(entries) { index, entry ->
                    LogRow(
                        row = LogRowState(
                            number = index + 1,
                            entry = entry,
                            isSelected = index in selected,
                        ),
                        modifier = Modifier.animateItem(),
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
