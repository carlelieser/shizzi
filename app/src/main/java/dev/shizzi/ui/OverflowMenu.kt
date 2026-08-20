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

/**
 * Narrow enough to sit under the button, wide enough for the longest label.
 *
 * A minimum rather than a fixed width: a menu that clipped its own text would
 * be worse than one that grows, and these labels are short verbs.
 */
private val MenuMinWidth = 180.dp

/**
 * Lifts the menu clear of the header rule.
 *
 * Material anchors a dropdown flush against its button, which would put the
 * menu's top border directly on the line under the header — two parallel
 * edges a couple of pixels apart, reading as a rendering fault.
 */
private val MenuOffset = DpOffset(x = 0.dp, y = 4.dp)

/**
 * Square, like every other surface here.
 *
 * Material's menu default is a 4dp rounded rectangle; nothing else in this app
 * has a rounded corner.
 */
private val SquareShape = RectangleShape

/** What an unavailable control fades to, matching the settings screen. */
private const val DisabledAlpha = 0.4f

/**
 * The header's overflow menu.
 *
 * A menu rather than the actions themselves: Log had one action in its header
 * and room for exactly one more, and a screen that grows a third would have
 * had to become this anyway. Holding the first item behind a tap costs a press
 * and buys somewhere for the rest to go.
 *
 * Built on Material's [DropdownMenu] for its dismiss and placement behaviour —
 * outside taps, back press, and staying on screen near an edge are the parts
 * worth not reimplementing — but with its surface replaced. Material draws a
 * rounded, elevated, tonally shaded card; every other surface in this app is a
 * square bordered box with a hard offset shadow, and a menu that disagreed
 * would look like it came from a different application.
 *
 * Has no disabled state. A menu with nothing worth opening should not be on
 * screen at all — greying it out leaves a control that explains nothing about
 * why it cannot be used, in the one spot a screen has for saying something
 * useful. Callers omit it instead.
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
            // Turquoise when something inside is waiting on the user. A menu
            // hides its contents by design, so state that used to be legible in
            // the header — a live selection, say — needs a way to show through
            // the closed button or it is simply lost.
            tint = if (isMarked) colors.primary else colors.onSurface,
        )

        // Material's own surface is switched off entirely — transparent, square,
        // unelevated, unbordered — so the popup is nothing but a positioned,
        // dismissable container. The look comes from a brutalSurface inside it,
        // the same call every other bordered element in the app makes, rather
        // than from a stroke reimplemented here that would drift from it.
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
                // Room for the shadow to land in. The popup sizes itself to this
                // content, so without the padding the offset shadow would be
                // drawn outside the window and clipped away — which is exactly
                // what left the menu looking like a plain bordered box.
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
 * One line of the menu.
 *
 * Left-aligned mono, like the settings rows rather than like a Material menu
 * item — this app's lists are text against a left margin, and centring a
 * single column of short labels would leave them floating.
 *
 * Closes the menu before acting. Every action here finishes immediately and
 * leaves nothing to look at behind the menu, so holding it open would just
 * mean a second tap to dismiss it.
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
