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

/**
 * Fixed rather than measured: four digits at the log style's size outlasts what
 * a 1 MB cap holds, and measuring per frame would shift the rule between gutter
 * and text as the list scrolls past line 100.
 */
private val GutterWidth = 44.dp

/** Turquoise edge marking a selected row, and the width of the gutter rule. */
private val SelectionEdge = 3.dp
private val RuleWidth = 1.dp

/** How much turquoise a selected row's background carries. */
private const val SelectionTint = 0.12f

/** Tall enough for the fade to be a fade; below this it reads as a grey stripe. */
private val JumpBandHeight = 96.dp

/**
 * Smaller than Home's 96dp status glyph, which is the subject of its screen;
 * this one only marks the sentence that carries the meaning.
 */
private val EmptyIconSize = 64.dp

/**
 * How far from an end counts as already there — roughly a screen, since a
 * reader two lines from an edge needs no offer to move two lines, and a
 * threshold tripping on the last item alone would flicker as it scrolled.
 *
 * Shared by both bands, so a long log far from both ends shows both. From the
 * middle of a thousand entries, both ends really are somewhere else.
 */
private const val JumpThreshold = 8

/**
 * The log as read from disk, with a way to read it again. File I/O — at the cap
 * this parses a megabyte across two files — so it stays off the composition
 * thread.
 *
 * Not observed for changes: entries arrive while a session runs, and a list
 * reordering itself under a finger would be worse than one current as of
 * opening it. [reload] covers the one case where the screen knows the file
 * changed because it changed it.
 *
 * @param isLoaded whether the read has finished, distinct from an empty list —
 *   which is also what the first frame holds.
 */
@Immutable
data class LogEntries(
    val entries: List<LogEntry>,
    val isLoaded: Boolean,
    val reload: () -> Unit,
)

/**
 * Grouped so [LogPage] stays inside the parameter limit. [onClear] hands back
 * what it could not empty, so the screen cannot claim a half-achieved clear.
 */
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

    // A counter rather than a flag, so a second clear while the first is still
    // reading is still a distinct key.
    var generation by remember { mutableIntStateOf(0) }

    LaunchedEffect(generation) {
        entries = withContext(Dispatchers.IO) { SessionLog.merged().asReversed() }
        isLoaded = true
    }

    // A reload does not clear isLoaded: the entries on screen stay valid until
    // the new read replaces them, and blanking would flash a list about to be
    // redrawn with nearly the same contents.
    return LogEntries(entries, isLoaded) { generation++ }
}

/**
 * The session log, both files interleaved by timestamp.
 *
 * Oldest first, unlike `merged()`: a log read top to bottom is a story, and
 * reversing it puts the cause after the effect.
 */
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
                // Absent rather than greyed out: both items work on entries,
                // this screen cannot produce any, and the empty state already
                // says what to do. Gated on the read landing too, so it does
                // not appear for a frame before an empty log resolves.
                if (log.isLoaded && entries.isNotEmpty()) {
                    val isAllSelected = selected.size == entries.size

                    OverflowMenu(isMarked = selected.isNotEmpty()) {
                        OverflowItem(
                            // The menu is the only place left to say what Copy
                            // will take; "COPY" alone beside a selection the
                            // user made would be ambiguous.
                            label = copyLabel(selected.size, entries.size),
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(copyText(entries, selected)),
                                )
                                selected = emptySet()
                            },
                        )

                        // One item that flips rather than two with one always
                        // inert — the selection already decides which applies.
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

        // Nothing until the read lands: a glyph-and-button empty state flashing
        // on the way to a log that was never empty is worse than a blank moment.
        if (!log.isLoaded) return@Column

        if (entries.isEmpty()) {
            EmptyLog(
                isLogging = isLogging,
                onEnableLogging = actions.onEnableLogging,
                onStartSession = actions.onStartSession,
            )
            return@Column
        }

        // One box, so the bands overlay the text rather than taking a strip of
        // layout from it.
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

/** Which end of the list a band offers to travel to. */
private enum class JumpEdge { TOP, BOTTOM }

/**
 * The way back to either end of the log — a session's start and its outcome,
 * with a long scroll stranding the reader far from both.
 *
 * A gradient rather than a bar, so the label stays legible over whatever text
 * is underneath without drawing a second horizontal edge under the header's.
 * Each fades from transparent toward its own edge.
 *
 * Named in full rather than drawn as a chevron: a bare arrow over a scrolling
 * list could mean the end of the log or one page down.
 *
 * The band never takes touch — it is a full-width mostly-transparent rectangle
 * over the rows that are this screen's selection targets. Only the label is
 * interactive.
 */
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

    // derivedStateOf so recomposition keys on the comparison, not the scroll
    // position: this reads a value that changes every frame of a fling to
    // answer a question whose answer changes twice.
    val isShowing by remember(count, edge) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val distance = when {
                // Distance from the nearest visible row to this band's end.
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
        // The container paints and the label is its only child, so the band is
        // paint and nothing else — a clickable here would put a full-width
        // touch target over the rows.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(JumpBandHeight)
                .background(
                    Brush.verticalGradient(if (isTop) fade else fade.asReversed()),
                ),
            contentAlignment = if (isTop) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            // Bare text: a bordered box here would outrank the header's while
            // doing less.
            Text(
                text = if (isTop) "SCROLL TO TOP" else "SCROLL TO BOTTOM",
                style = ShizziTheme.typography.label,
                color = colors.onSurface,
                modifier = Modifier
                    // Outside the clickable: clearance from the screen edge,
                    // not part of the target.
                    .padding(
                        top = if (isTop) ShizziTheme.spacing.lg else 0.dp,
                        bottom = if (isTop) 0.dp else ShizziTheme.spacing.lg,
                    )
                    .clickable {
                        // Animated: a teleport gives no sense of how much was
                        // skipped, on a screen people scroll back through.
                        scope.launch {
                            listState.animateScrollToItem(if (isTop) 0 else count - 1)
                        }
                    }
                    .padding(ShizziTheme.spacing.sm),
            )
        }
    }
}

/**
 * The row is the selection unit: character-level drag selection is a lot of
 * machinery and awkward under a finger, and a log is quoted by the line anyway.
 */
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
            // Drawn, not composed: the rule runs the full height of a wrapped
            // row, which a Divider between two columns would not.
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

/**
 * The level is bold rather than coloured: the palette spends its one accent on
 * actionable state, and the level word already says which it is.
 */
@Composable
private fun LogText(entry: LogEntry, modifier: Modifier = Modifier) {
    val colors = ShizziTheme.colors
    val style = ShizziTheme.typography.log

    Text(
        text = buildString {
            // Dropped here but kept in a copy: on screen the date is the same
            // for every visible row, and the level gets its own weight.
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

/**
 * Two different situations. Usually no session has run yet, and the way out is
 * to run one — but the screen reads the same with logging switched off, where
 * that message would be a lie, since a session would produce nothing either.
 *
 * The logging CTA toggles in place rather than opening Settings: a button that
 * only leads to the real button is a detour dressed as an action.
 */
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

        // Only the waiting state gets a second line: "Logging is disabled" is
        // the whole fact, and the action below says what to do about it.
        if (isLogging) {
            Spacer(Modifier.height(ShizziTheme.spacing.sm))

            Text(
                text = "Logs will appear here",
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }

        // Smaller than the gap above the title: the action's own 48dp target
        // already holds most of the separation.
        Spacer(Modifier.height(ShizziTheme.spacing.sm))

        EmptyAction(
            label = if (isLogging) "Start a session" else "Enable logging",
            onClick = if (isLogging) onStartSession else onEnableLogging,
        )
    }
}

/**
 * Bare text, like CANCEL on Home — not a filled box. The accent belongs to the
 * connect button, and a filled one here would claim to be the same order of
 * thing while two screens away from it. Turquoise text keeps it findable
 * without competing.
 */
@Composable
private fun EmptyAction(label: String, onClick: () -> Unit) {
    Box(
        // Sized to the touch minimum rather than to the text, which is shorter
        // than a finger.
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

/**
 * Keyed on what lands on the clipboard, not on the selection: nothing selected
 * and everything selected both copy the whole log, so both say ALL.
 *
 * A count appears only for a genuine subset, and names its unit so a bare
 * number is not read as characters. Singular at one — the case comes up
 * constantly with per-row selection.
 */
private fun copyLabel(count: Int, total: Int): String = when {
    count == 0 || count == total -> "COPY ALL"
    count == 1 -> "COPY 1 LINE"
    else -> "COPY $count LINES"
}

/**
 * Full timestamps, no line numbers: the numbers are a reading aid for wrapped
 * entries, so pasting them would paste this screen's layout, not the log.
 */
private fun copyText(entries: List<LogEntry>, selected: Set<Int>): String = entries
    .filterIndexed { index, _ -> selected.isEmpty() || index in selected }
    .joinToString("\n") { entry ->
        "${entry.timestamp} ${entry.level.name.padEnd(5)} ${entry.message}"
    }
