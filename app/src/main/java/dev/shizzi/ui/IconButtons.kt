package dev.shizzi.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.MinTouchTarget
import dev.shizzi.ui.theme.ShizziTheme

/** Glyph size, small enough to sit inside the touch target with room around it. */
private val IconSize = 24.dp

/**
 * The app's one icon button.
 *
 * Unbordered, unlike every other interactive element: a header row of bordered
 * boxes would compete with the content for attention, and these are secondary
 * to the connect button on Home.
 *
 * The tint carries the rest of that ranking, so it is left to the caller. A
 * button competing with something on its screen takes the muted tone; one that
 * is the only way off a screen keeps full strength, which is why this defaults
 * to [ShizziTheme] not being consulted for a demotion the call site has not
 * asked for.
 */
@Composable
fun ShizziIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(MinTouchTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (tint == Color.Unspecified) ShizziTheme.colors.onSurface else tint,
            modifier = Modifier.size(IconSize),
        )
    }
}

/**
 * Auto-mirrored so it points the correct way in a right-to-left locale.
 *
 * Full strength, unlike the settings button it mirrors on Home: back is the
 * only way off the screen it sits on, so it is not secondary to anything there.
 */
@Composable
fun BackButton(onBack: () -> Unit) {
    ShizziIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        onClick = onBack,
    )
}
