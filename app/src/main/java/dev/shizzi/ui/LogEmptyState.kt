package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.MinTouchTarget
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme

private val EmptyIconSize = 64.dp

@Composable
fun EmptyLog(
    isLogging: Boolean,
    onEnableLogging: () -> Unit,
    onStartSession: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.TextSnippet,
            contentDescription = null,
            tint = ShizziTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(EmptyIconSize),
        )

        Spacer(Modifier.height(ShizziTheme.spacing.xl))

        Text(
            text = if (isLogging) "No logs yet" else "Logging is disabled",
            style = ShizziTheme.typography.subheading,
            color = ShizziTheme.colors.onSurface,
        )

        if (isLogging) {
            Spacer(Modifier.height(ShizziTheme.spacing.sm))

            Text(
                text = "Logs will appear here",
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(ShizziTheme.spacing.sm))

        EmptyAction(
            label = if (isLogging) "Start a session" else "Enable logging",
            onClick = if (isLogging) onStartSession else onEnableLogging,
        )
    }
}

@Composable
private fun EmptyAction(label: String, onClick: () -> Unit) {
    Box(

        modifier = Modifier
            .height(MinTouchTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.primary,
        )
    }
}
