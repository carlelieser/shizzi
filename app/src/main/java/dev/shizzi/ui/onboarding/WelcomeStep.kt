package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.shimmer
import dev.shizzi.ui.theme.ShizziTheme

private val WelcomeIconSize = 280.dp

@Composable
fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.WifiTethering,
            contentDescription = null,
            tint = ShizziTheme.colors.primary,
            modifier = Modifier
                .size(WelcomeIconSize)

                .shimmer(isActive = true, highlight = ShizziTheme.colors.primaryBright)
                .padding(bottom = ShizziTheme.spacing.xl),
        )

        Text(
            text = "Welcome",
            style = StepTitleStyle,
            color = ShizziTheme.colors.onSurface,
        )

        Text(
            text = "Rootless wifi-tethering with Shizuku.",
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = ShizziTheme.spacing.sm),
        )
    }
}
