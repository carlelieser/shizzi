package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface
import dev.shizzi.ui.theme.isPressed

@Composable
fun SetupButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ShizziTheme.spacing.md),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .height(ShizziTheme.spacing.xxl)
                .brutalSurface(
                    fill = Color.Transparent,
                    isPressed = interaction.isPressed(),
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = ShizziTheme.spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Setup".uppercase(),
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurface,
            )
        }
    }
}
