package dev.shizzi.ui

import dev.shizzi.R
import dev.shizzi.str

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.MinTouchTarget
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.fastSpring
import dev.shizzi.ui.theme.themedIndication

private val IconSize = 24.dp
private val CompactIconSize = 18.dp
private val CompactTouchTarget = 32.dp
private val IconRestCorner = 24.dp
private val IconActiveCorner = 12.dp
private val CompactRestCorner = 16.dp
private val CompactActiveCorner = 8.dp

@Composable
fun ShizziIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    containerColor: Color = Color.Transparent,
    selected: Boolean = false,
) {
    ExpressiveIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        tint = tint,
        containerColor = containerColor,
        selected = selected,
        touchTarget = MinTouchTarget,
        iconSize = IconSize,
        restCorner = IconRestCorner,
        activeCorner = IconActiveCorner,
    )
}

@Composable
fun ShizziCompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    ExpressiveIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = ShizziTheme.colors.onSurfaceMuted,
        selected = selected,
        touchTarget = CompactTouchTarget,
        iconSize = CompactIconSize,
        restCorner = CompactRestCorner,
        activeCorner = CompactActiveCorner,
    )
}

@Composable
private fun ExpressiveIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    containerColor: Color = Color.Transparent,
    selected: Boolean = false,
    touchTarget: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    restCorner: androidx.compose.ui.unit.Dp,
    activeCorner: androidx.compose.ui.unit.Dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val isActive = selected || isPressed
    val corner by animateDpAsState(
        targetValue = if (isActive) activeCorner else restCorner,
        animationSpec = fastSpring(),
        label = "iconButtonCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = fastSpring(),
        label = "iconButtonScale",
    )
    val fill = when {
        selected -> ShizziTheme.colors.primaryContainer
        else -> containerColor
    }

    Box(
        modifier = modifier
            .size(touchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = themedIndication(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (tint == Color.Unspecified) ShizziTheme.colors.onSurface else tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    ShizziIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = str(R.string.back),
        onClick = onBack,
    )
}
