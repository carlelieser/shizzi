package dev.shizzi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.LogEntry
import dev.shizzi.LogLevel
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardSpring

private val GutterWidth = 44.dp

private val SelectionEdge = 3.dp

private val RuleWidth = 1.dp

private const val SelectionTint = 0.12f

@Immutable
data class LogRowState(
    val number: Int,
    val entry: LogEntry,
    val isSelected: Boolean,
)

@Composable
fun LogRow(
    row: LogRowState,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val colors = ShizziTheme.colors

    val fill by animateColorAsState(
        targetValue = when {
            row.isSelected -> colors.primary.copy(alpha = SelectionTint)
            else -> colors.background
        },
        animationSpec = standardSpring(),
        label = "logRowFill",
    )

    val edge by animateDpAsState(
        targetValue = if (row.isSelected) SelectionEdge else 0.dp,
        animationSpec = standardSpring(),
        label = "logRowEdge",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(fill)
            .clickable(onClick = onToggle)

            .drawBehind {
                val rule = GutterWidth.toPx()
                drawLine(
                    color = colors.onSurfaceMuted.copy(alpha = 0.3f),
                    start = Offset(rule, 0f),
                    end = Offset(rule, size.height),
                    strokeWidth = RuleWidth.toPx(),
                )

                val edgeWidth = edge.toPx()
                if (edgeWidth > 0f) {
                    drawRect(
                        color = colors.primary,
                        size = size.copy(width = edgeWidth),
                    )
                }
            }
            .padding(vertical = ShizziTheme.spacing.xs),
    ) {
        Text(
            text = "${row.number}",
            style = ShizziTheme.typography.log,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(GutterWidth)
                .padding(end = ShizziTheme.spacing.sm),
        )

        LogText(
            entry = row.entry,
            modifier = Modifier.padding(horizontal = ShizziTheme.spacing.sm),
        )
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
