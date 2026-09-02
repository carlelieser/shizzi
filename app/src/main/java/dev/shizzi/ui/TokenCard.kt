package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import dev.shizzi.ui.theme.ShadowOffset
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.brutalSurface

data class TokenActions(
    val onCopy: () -> Unit,
    val onRegenerate: () -> Unit,
)

@Composable
fun TokenCard(token: String, actions: TokenActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = SettingsRowPadding,
                bottom = SettingsRowPadding + ShadowOffset,
            )
            .brutalSurface(fill = ShizziTheme.colors.surface)
            .padding(
                start = ShizziTheme.spacing.lg,
                end = ShizziTheme.spacing.sm,
                top = ShizziTheme.spacing.sm,
                bottom = ShizziTheme.spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        TokenHeader(actions)

        Text(
            text = token,
            style = ShizziTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
            color = ShizziTheme.colors.onSurface,
        )
    }
}

@Composable
private fun TokenHeader(actions: TokenActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Token",
            style = ShizziTheme.typography.subheading,
            color = ShizziTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )

        ShizziCompactIconButton(
            icon = Icons.Filled.ContentCopy,
            contentDescription = "Copy token",
            onClick = actions.onCopy,
        )

        ShizziCompactIconButton(
            icon = Icons.Filled.Refresh,
            contentDescription = "Regenerate token",
            onClick = actions.onRegenerate,
        )
    }
}
