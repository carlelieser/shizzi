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
 * The band the jump button sits in.
 *
 * Tall enough that the fade has room to be a fade rather than a hard edge —
 * below this the gradient reads as a grey stripe laid over the text.
 */
private val JumpBandHeight = 96.dp

/**
 * The empty state's glyph.
 *
 * Smaller than Home's 96dp status glyph: that one is the subject of its screen,
 * this one is a marker above the sentence that carries the meaning.
 */
private val EmptyIconSize = 64.dp

/**
 * How far from an end counts as already there.
 *
 * Roughly a screen of entries rather than an exact index: a reader a couple of
 * lines from an edge does not need a band offering to move them a couple of
 * lines, and a threshold that tripped on the first or last item alone would
 * flicker as that row scrolled in and out.
 *
 * Shared by both bands. A log long enough to be far from both ends at once
 * shows both, which is the honest answer: from the middle of a thousand
 * entries, both ends really are somewhere else.
 */
private const val JumpThreshold = 8

/**
 * The log as read from disk, with a way to read it again.
 *
 * File I/O, so it does not belong on the composition thread — at the 1 MB cap
 * this parses a megabyte across two files.
 *
 * Not observed for changes: entries arrive while a session runs, and a list
 * that reordered itself under a finger mid-read would be worse than one that
 * is current as of opening it. [reload] exists for the one case where the
 * screen knows the file changed because it changed it — clearing — since
 * without it the entries would sit on screen after being deleted from disk.
 */
/**
 * @param isLoaded whether the read has finished. Distinct from an empty list,
 *   which is also what the first frame holds — and the empty state is a large
 *   glyph and a filled button, too loud to flash on the way to a log that was
 *   never empty.
 */
@Immutable
data class LogEntries(
    val entries: List<LogEntry>,
    val isLoaded: Boolean,
    val reload: () -> Unit,
)

/**
 * What the log screen can do, grouped so it stays inside the parameter limit.
 *
 * [onClear] hands back what it could not empty, rather than returning nothing
 * and leaving the screen to claim a clear it may only half have achieved.
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

    // Bumping this re-runs the read. A plain counter rather than a flag, so a
    // second clear while the first is still reading is still a distinct key.
    var generation by remember { mutableIntStateOf(0) }

    LaunchedEffect(generation) {
        entries = withContext(Dispatchers.IO) { SessionLog.merged().asReversed() }
        isLoaded = true
    }

    // A reload does not clear isLoaded: the entries on screen stay valid until
    // the new read replaces them, and dropping back to the loading state would
    // blank a list that is about to be redrawn with nearly the same contents.
    return LogEntries(entries, isLoaded) { generation++ }
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
                // Absent rather than greyed out when there is nothing to act
                // on. Both items work on entries, and this screen cannot
                // produce any — a permanently dead control is furniture, and
                // the empty state already says what to do instead.
                //
                // Gated on the read having landed as well, so it does not
                // appear for a frame and then leave once an empty log resolves.
                if (log.isLoaded && entries.isNotEmpty()) {
                    val isAllSelected = selected.size == entries.size

                    OverflowMenu(isMarked = selected.isNotEmpty()) {
                        OverflowItem(
                            // The count rides on the label, as it did when this
                            // was a header button: the menu is the only place
                            // left to say what Copy will take, and "COPY" alone
                            // beside a selection the user made would be
                            // ambiguous.
                            label = copyLabel(selected.size, entries.size),
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(copyText(entries, selected)),
                                )
                                selected = emptySet()
                            },
                        )

                        // One item that flips, rather than two sitting side by
                        // side with one of them always inert: which one applies
                        // is already decided by what is selected, and the menu
                        // can simply offer that one.
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

        // Nothing at all until the read lands. The empty state is a large glyph
        // and a filled button, and flashing it on the way to a log that was
        // never empty would be worse than a blank moment.
        if (!log.isLoaded) return@Column

        if (entries.isEmpty()) {
            EmptyLog(
                isLogging = isLogging,
                onEnableLogging = actions.onEnableLogging,
                onStartSession = actions.onStartSession,
            )
            return@Column
        }

        // The list and both jump bands share a box so the overlays can sit over
        // the text rather than taking a strip of layout away from it.
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
 * The way back to either end of the log.
 *
 * The log is oldest first, so the two ends are the two things worth returning
 * to — the start of a session and its outcome — and a long scroll strands you
 * far from both. A gradient carries the label rather than a bar: the band has
 * to be legible over whatever text happens to be underneath it without drawing
 * another horizontal edge across a screen that already has one under the
 * header. Each fades from transparent toward its own edge, so the opaque end
 * is the edge the label sits against.
 *
 * Named in full rather than drawn as a chevron. A bare arrow over a scrolling
 * list is ambiguous — end of the log, or one page down — and the label costs
 * nothing on a screen whose content is already monospaced text.
 *
 * Hidden when its end is already in view. The band itself never takes touch:
 * it spans the width and is mostly transparent, and the rows under it are this
 * screen's selection targets, so swallowing taps there would cost more than
 * the affordance is worth. Only the label is interactive, and only the label
 * scrolls.
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

    // derivedStateOf so the comparison is what recomposition keys on, not the
    // scroll position: this reads a value that changes every frame of a fling
    // and answers a question whose answer changes twice.
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
        // The gradient is drawn by the container and the label is its only
        // child, so the band is paint and nothing else. Giving the band a
        // clickable or a pointer handler of its own would turn a full-width
        // mostly-transparent rectangle into a touch target sitting over the
        // rows, which are this screen's selection targets.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(JumpBandHeight)
                .background(
                    Brush.verticalGradient(if (isTop) fade else fade.asReversed()),
                ),
            contentAlignment = if (isTop) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            // Bare text, like COPY in the header: these are the only things on
            // this screen that act rather than select, and a bordered box here
            // would outrank the one in the header while doing less.
            Text(
                text = if (isTop) "SCROLL TO TOP" else "SCROLL TO BOTTOM",
                style = ShizziTheme.typography.label,
                color = colors.onSurface,
                modifier = Modifier
                    // Outside the clickable: this is clearance from the screen
                    // edge, not part of the target.
                    .padding(
                        top = if (isTop) ShizziTheme.spacing.lg else 0.dp,
                        bottom = if (isTop) 0.dp else ShizziTheme.spacing.lg,
                    )
                    .clickable {
                        // Animated rather than instant: a jump that teleports
                        // gives no sense of how much was skipped, and this is a
                        // screen people scroll back through.
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
 * The screen with nothing to show, which is two different situations.
 *
 * An empty log usually means no session has run yet, and the way out is to run
 * one. But it also reads as empty when logging is switched off — and there the
 * first message would be a lie, since starting a session would produce nothing
 * either. So the state names which of the two it is and offers the action that
 * actually fixes it.
 *
 * The logging CTA acts here rather than sending the user to Settings to find a
 * toggle: this screen already knows what is wrong and can undo it in place,
 * and a button that only opens the screen where the real button lives is a
 * detour dressed as an action.
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

        // Only the waiting state gets a second line. "Logging is disabled" is
        // already the whole fact, and the action under it says what to do about
        // it — a sentence between the two would restate both.
        if (isLogging) {
            Spacer(Modifier.height(ShizziTheme.spacing.sm))

            Text(
                text = "Logs will appear here",
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }

        // Smaller than the gap above the title: the action carries its own
        // 48dp touch target, so most of the separation is already inside it and
        // a full xl on top of that reads as a gap rather than as a grouping.
        Spacer(Modifier.height(ShizziTheme.spacing.sm))

        EmptyAction(
            label = if (isLogging) "Start a session" else "Enable logging",
            onClick = if (isLogging) onStartSession else onEnableLogging,
        )
    }
}

/**
 * The empty state's one action.
 *
 * Bare text, like CANCEL on Home and the jump bands on this screen — not a
 * filled box. The app spends its accent on the connect button, and a turquoise
 * button here would claim to be the same order of thing as starting a session
 * while sitting two screens away from it. This is a suggestion about a screen
 * that happens to be empty, not the app's main action.
 *
 * Turquoise text rather than turquoise fill keeps it findable on an otherwise
 * empty screen without competing: the only saturated pixels in the app still
 * belong to a session that is actually up.
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
 * What the copy item says it will take.
 *
 * Keyed on what actually lands on the clipboard rather than on the selection.
 * Two different states copy the whole log — nothing selected, and every row
 * selected — and both say ALL, because the label's job is to describe the
 * result rather than to report how the user arrived at it.
 *
 * A count appears only for a genuine subset, and names the unit rather than
 * leaving a bare number to be read as characters or bytes. Singular at one:
 * "1 LINES" makes an interface look unfinished, and the case comes up
 * constantly here since selection is per row.
 */
private fun copyLabel(count: Int, total: Int): String = when {
    count == 0 || count == total -> "COPY ALL"
    count == 1 -> "COPY 1 LINE"
    else -> "COPY $count LINES"
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
