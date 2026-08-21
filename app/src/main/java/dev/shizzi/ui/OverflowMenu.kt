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

private val MenuMinWidth = 180.dp

private val MenuOffset = DpOffset(x = 0.dp, y = 4.dp)

private val SquareShape = RectangleShape

private const val DisabledAlpha = 0.4f

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

            tint = if (isMarked) colors.primary else colors.onSurface,
        )

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

class OverflowScope internal constructor(internal val dismiss: () -> Unit)

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
