package dev.shizzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.LogEntry
import dev.shizzi.LogLevel
import dev.shizzi.SessionLog
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gutter width, fixed rather than measured.
 *
 * Wide enough for four digits at the log style's size, which outlasts the
 * entries a 1 MB cap can hold. Measuring it per frame would make the rule
 * between gutter and text shift as the list scrolls past line 100.
 */
private val GutterWidth = 44.dp

/** Turquoise edge marking a selected row, and the width of the gutter rule. */
private val SelectionEdge = 3.dp
private val RuleWidth = 1.dp

/** How much turquoise a selected row's background carries. */
private const val SelectionTint = 0.12f

/**
 * Reads the log off the main thread, once per visit to the screen.
 *
 * File I/O, so it does not belong on the composition thread — at the 1 MB cap
 * this parses a megabyte across two files. Not observed for changes: entries
 * arrive while a session runs, and a list that reordered itself under a finger
 * mid-read would be worse than one that is current as of opening it.
 */
@Composable
fun rememberLogEntries(): List<LogEntry> {
    var entries by remember { mutableStateOf(emptyList<LogEntry>()) }

    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { SessionLog.merged().asReversed() }
    }

    return entries
}

/**
 * The session log.
 *
 * Reads both files through [SessionLog.merged] — the shell process writes the
 * events worth reading and the app cannot write to that directory, so the
 * history is two files interleaved by timestamp.
 *
 * Oldest first here, unlike `merged()`, which returns newest first: a log read
 * top to bottom is a story, and reversing it puts the cause after the effect.
 */
@Composable
fun LogPage(entries: List<LogEntry>, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(
            title = "Log",
            onBack = onBack,
            action = {
                CopyButton(
                    count = selected.size,
                    isEnabled = entries.isNotEmpty(),
                    onClick = {
                        clipboard.setText(AnnotatedString(copyText(entries, selected)))
                        selected = emptySet()
                    },
                )
            },
        )

        if (entries.isEmpty()) {
            EmptyLog()
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(entries) { index, entry ->
                LogRow(
                    number = index + 1,
                    entry = entry,
                    isSelected = index in selected,
                    onToggle = {
                        selected = if (index in selected) selected - index else selected + index
                    },
                )
            }
        }
    }
}

/**
 * One entry, its number in the gutter and its text wrapped beside it.
 *
 * The row is the selection unit: character-level drag selection is a large
 * amount of machinery and awkward to land on with a finger, and a log is read
 * and quoted by the line anyway.
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
            // Drawn rather than composed: the gutter rule runs the full height
            // of a wrapped row, which a Divider between two columns would not.
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
 * The entry itself: time, level, message.
 *
 * The level is bold rather than coloured. The palette carries one accent and
 * spends it on actionable state, so a red line here would introduce a second
 * signal that means something quieter — and the level word already says which
 * it is.
 */
@Composable
private fun LogText(entry: LogEntry, modifier: Modifier = Modifier) {
    val colors = ShizziTheme.colors
    val style = ShizziTheme.typography.log

    Text(
        text = buildString {
            // Date and level are dropped from the display but kept in a copy:
            // on screen the date is the same for every visible row, and the
            // level is about to be rendered in its own weight.
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
 * Copies everything, or the selection.
 *
 * Reads COPY ALL until rows are picked, then the count, so the button says
 * what pressing it will take rather than leaving the user to infer it from
 * whether anything looks highlighted.
 */
@Composable
private fun CopyButton(count: Int, isEnabled: Boolean, onClick: () -> Unit) {
    Text(
        text = if (count == 0) "COPY ALL" else "COPY $count",
        style = ShizziTheme.typography.label,
        color = when {
            !isEnabled -> ShizziTheme.colors.onSurfaceMuted.copy(alpha = 0.4f)
            count > 0 -> ShizziTheme.colors.primary
            else -> ShizziTheme.colors.onSurface
        },
        modifier = Modifier
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(ShizziTheme.spacing.sm),
    )
}

@Composable
private fun EmptyLog() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Nothing logged yet",
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(ScreenPadding),
        )
    }
}

/**
 * What lands on the clipboard: full timestamps, no line numbers.
 *
 * The numbers are a reading aid for a screen where entries wrap, not part of
 * the record — pasting them into an issue would be pasting this screen's
 * layout rather than the log.
 */
private fun copyText(entries: List<LogEntry>, selected: Set<Int>): String = entries
    .filterIndexed { index, _ -> selected.isEmpty() || index in selected }
    .joinToString("\n") { entry ->
        "${entry.timestamp} ${entry.level.name.padEnd(5)} ${entry.message}"
    }
