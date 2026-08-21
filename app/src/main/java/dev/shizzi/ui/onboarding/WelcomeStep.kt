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
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme

/** The home screen's status glyph at the same size, which is where it reappears. */
private val WelcomeIconSize = 96.dp

/**
 * The mark, then the word. Left aligned and bottom weighted, so the eye starts
 * where every following step's content starts rather than at the centre of an
 * otherwise empty screen.
 *
 * No paragraph under it: the two steps that follow are the explanation, and a
 * summary here would be read once and then repeated.
 */
@Composable
fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(
            imageVector = Icons.Filled.WifiTethering,
            contentDescription = null,
            tint = ShizziTheme.colors.primary,
            modifier = Modifier.size(WelcomeIconSize),
        )

        Text(
            text = "Welcome",
            style = ShizziTheme.typography.display,
            color = ShizziTheme.colors.onSurface,
            modifier = Modifier.padding(top = ShizziTheme.spacing.lg),
        )
    }
}
