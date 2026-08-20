package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShadowOffset
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

/** A minimum, not a fixed width: clipping a label would be worse than growing. */
private val MenuMinWidth = 180.dp

/**
 * Lifts the menu clear of the header rule. Material anchors flush to the
 * button, putting the menu's top border a few pixels from the header's line —
 * two parallel edges that read as a rendering fault.
 */
private val MenuOffset = DpOffset(x = 0.dp, y = 4.dp)

/** Material's menu defaults to 4dp rounded; nothing else here has a corner. */
private val SquareShape = RectangleShape

/** What an unavailable control fades to, matching the settings screen. */
private const val DisabledAlpha = 0.4f

/**
 * The header's overflow menu.
 *
 * Material's [DropdownMenu] for its dismiss and placement behaviour — outside
 * taps, back press, staying on screen near an edge — with its surface switched
 * off, since a rounded elevated card would look imported from another app.
 *
 * Has no disabled state: a menu with nothing worth opening should not be on
 * screen, and greying it out spends the one spot a screen has for saying
 * something useful on a control that explains nothing. Callers omit it.
 */
@Composable
fun OverflowMenu(
    isMarked: Boolean = false,
    items: @Composable OverflowScope.() -> Unit,
) {
    var isOpen by remember { mutableStateOf(false) }
    val colors = ShizziTheme.colors
    val scope = remember { OverflowScope { isOpen = false } }

    Box {
        ShizziIconButton(
            icon = Icons.Filled.MoreVert,
            contentDescription = "More options",
            onClick = { isOpen = true },
            // A menu hides its contents, so state that was legible in the
            // header — a live selection — needs to show through the button.
            tint = if (isMarked) colors.primary else colors.onSurface,
        )

        // Nothing but a positioned, dismissable container; the look comes from
        // the brutalSurface inside, the same call every other bordered element
        // makes rather than a stroke reimplemented here that would drift.
        DropdownMenu(
            expanded = isOpen,
            onDismissRequest = { isOpen = false },
            offset = MenuOffset,
            containerColor = Color.Transparent,
            shape = SquareShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = null,
        ) {
            Box(
                // Room for the shadow to land in: the popup sizes itself to
                // this content, so without the padding the offset shadow falls
                // outside the window and is clipped, leaving a plain box.
                modifier = Modifier.padding(end = ShadowOffset, bottom = ShadowOffset),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = MenuMinWidth)
                        .brutalSurface(fill = colors.surface),
                ) {
                    scope.items()
                }
            }
        }
    }
}

/**
 * Receiver for a menu's contents, so an item can close the menu without every
 * call site threading a dismiss lambda through its own click handler.
 */
class OverflowScope internal constructor(internal val dismiss: () -> Unit)

/**
 * Left-aligned mono like the settings rows, not a Material menu item.
 *
 * Closes before acting: every action here finishes immediately and leaves
 * nothing to look at, so holding the menu open only costs a second tap.
 */
@Composable
fun OverflowScope.OverflowItem(
    label: String,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = ShizziTheme.typography.label,
        color = when {
            isEnabled -> ShizziTheme.colors.onSurface
            else -> ShizziTheme.colors.onSurfaceMuted.copy(alpha = DisabledAlpha)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) {
                dismiss()
                onClick()
            }
            .padding(
                horizontal = ShizziTheme.spacing.lg,
                vertical = ShizziTheme.spacing.md,
            ),
    )
}
