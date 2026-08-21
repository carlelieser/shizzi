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

private val HeaderRule = 1.dp

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
