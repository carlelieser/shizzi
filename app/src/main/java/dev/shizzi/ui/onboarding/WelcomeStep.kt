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
import dev.shizzi.ui.shimmer
import dev.shizzi.ui.theme.ShizziTheme

/** Most of the screen's width, so the mark carries the page on its own. */
private val WelcomeIconSize = 280.dp

/**
 * The mark, then the word.
 *
 * The icon centres itself in the space above the title, which stays on the
 * left margin every following step's title uses.
 *
 * No paragraph under it: the two steps that follow are the explanation, and a
 * summary here would be read once and then repeated.
 */
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
                // Always sweeping: the home glyph shimmers to say a session is
                // coming up, where here there is nothing to wait for and the
                // mark is simply alive while the user reads the screen.
                .shimmer(isActive = true, highlight = ShizziTheme.colors.primaryBright)
                .padding(bottom = ShizziTheme.spacing.xl),
        )

        Text(
            text = "Welcome",
            style = StepTitleStyle,
            color = ShizziTheme.colors.onSurface,
        )
    }
}
