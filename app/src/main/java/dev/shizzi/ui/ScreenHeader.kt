package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.HeaderHeight
import dev.shizzi.ui.theme.ShizziTheme

/**
 * The rule under the header.
 *
 * 1dp rather than Dp.Hairline: a hairline is one physical pixel, which on a
 * ~3x density screen is a third of the thinnest line the rest of the app draws
 * and reads as a rendering artefact next to the 2dp borders elsewhere.
 */
private val HeaderRule = 1.dp

/**
 * The header shared by the two child screens: back button, left-aligned
 * title, and an optional action on the right edge.
 *
 * Home does not use this — it carries a status badge where the title would be,
 * and has no back button. It also goes without the rule below: Home's content
 * is centred in open space, so a line under its header would divide nothing.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit = {},
) {
    val border = ShizziTheme.colors.border

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            // Drawn before the horizontal padding so the rule runs edge to
            // edge; inside it the line would stop short of both margins and
            // read as an underline on the title rather than as structure.
            .drawBehind {
                val thickness = HeaderRule.toPx()
                drawLine(
                    color = border,
                    start = Offset(0f, size.height - thickness / 2f),
                    end = Offset(size.width, size.height - thickness / 2f),
                    strokeWidth = thickness,
                )
            }
            .padding(horizontal = ShizziTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        BackButton(onBack)

        Text(
            text = title,
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )

        action()
        Spacer(Modifier.width(ShizziTheme.spacing.xs))
    }
}
