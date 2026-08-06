package dev.shizzi.spike.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.spike.ui.theme.MinTouchTarget
import dev.shizzi.spike.ui.theme.ShizziTheme

/** Glyph size, small enough to sit inside the touch target with room around it. */
private val IconSize = 24.dp

/**
 * The app's one icon button.
 *
 * Unbordered, unlike every other interactive element: a header row of bordered
 * boxes would compete with the content for attention, and these are secondary
 * to the connect button on Home.
 */
@Composable
fun ShizziIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(MinTouchTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ShizziTheme.colors.onSurface,
            modifier = Modifier.size(IconSize),
        )
    }
}

/** Auto-mirrored so it points the correct way in a right-to-left locale. */
@Composable
fun BackButton(onBack: () -> Unit) {
    ShizziIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        onClick = onBack,
    )
}
