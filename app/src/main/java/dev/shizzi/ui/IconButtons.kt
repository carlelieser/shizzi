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

private val IconSize = 24.dp

private val CompactIconSize = 18.dp

private val CompactTouchTarget = 32.dp

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

@Composable
fun ShizziCompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(CompactTouchTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ShizziTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(CompactIconSize),
        )
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    ShizziIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        onClick = onBack,
    )
}
