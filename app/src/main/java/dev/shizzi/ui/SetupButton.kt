package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.shizzi.ui.theme.ShizziTheme

@Composable
fun SetupButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ShizziTheme.spacing.sm),
        horizontalArrangement = Arrangement.End,
    ) {
        GhostButton(label = "Setup", onClick = onClick)
    }
}
