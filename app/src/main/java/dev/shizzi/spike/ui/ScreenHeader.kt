package dev.shizzi.spike.ui

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
import dev.shizzi.spike.ui.theme.HeaderHeight
import dev.shizzi.spike.ui.theme.ShizziTheme

/**
 * The header shared by the two child screens: back button, left-aligned
 * title, and an optional action on the right edge.
 *
 * Home does not use this — it carries a status badge where the title would be,
 * and has no back button.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
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
