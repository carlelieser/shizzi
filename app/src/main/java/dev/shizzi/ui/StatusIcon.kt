package dev.shizzi.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WifiTetheringError
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.shizzi.ShizukuState
import dev.shizzi.UiStatus
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.emphasizedSpring
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween
import dev.shizzi.ui.theme.themedSurface

private val StatusIconSize = 280.dp

private const val InactiveAlpha = 0.10f

private const val ArrivalOvershoot = 1.08f

@Composable
fun StatusIcon(status: UiStatus) {
    val isConnected = status == UiStatus.CONNECTED
    val target = when {
        isConnected -> ShizziTheme.colors.primary
        else -> ShizziTheme.colors.onSurfaceMuted.copy(alpha = InactiveAlpha)
    }
    val tint by animateColorAsState(
        targetValue = target,
        animationSpec = standardSpring(),
        label = "statusTint",
    )

    val fadeSpec = standardTween<Float>()

    AnimatedContent(
        targetState = glyphFor(status),
        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
        label = "statusGlyph",
        modifier = Modifier.arrivalPulse(isConnected),
    ) { glyph ->
        Icon(
            imageVector = glyph,
            contentDescription = descriptionFor(status),
            tint = tint,
            modifier = Modifier
                .size(StatusIconSize)
                .shimmer(isActive = status == UiStatus.LOADING),
        )
    }
}

/** Swells once when the session connects, so arrival registers as an event. */
@Composable
private fun Modifier.arrivalPulse(isConnected: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    val settleSpec = emphasizedSpring<Float>()

    LaunchedEffect(isConnected) {
        if (!isConnected) return@LaunchedEffect

        scale.snapTo(ArrivalOvershoot)
        scale.animateTo(targetValue = 1f, animationSpec = settleSpec)
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

private fun glyphFor(status: UiStatus): ImageVector = when (status) {
    UiStatus.READY -> Icons.Filled.WifiTethering
    UiStatus.LOADING -> Icons.Filled.WifiTethering
    UiStatus.CONNECTED -> Icons.Filled.WifiTethering
    UiStatus.ERROR -> Icons.Filled.WifiTetheringError
}

private fun descriptionFor(status: UiStatus): String = when (status) {
    UiStatus.READY -> "Not connected"
    UiStatus.LOADING -> "Connecting"
    UiStatus.CONNECTED -> "Connected"
    UiStatus.ERROR -> "Failed"
}

@Composable
fun ShizukuBadge(state: ShizukuState) {
    val text = badgeText(state) ?: return

    Text(
        text = text,
        style = ShizziTheme.typography.caption,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier
            .themedSurface(fill = ShizziTheme.colors.surface)
            .padding(
                horizontal = ShizziTheme.spacing.sm,
                vertical = ShizziTheme.spacing.xs,
            ),
    )
}

private fun badgeText(state: ShizukuState): String? = when (state) {
    is ShizukuState.Ready -> null
    is ShizukuState.NotInstalled -> "NO SHIZUKU"
    is ShizukuState.NotRunning -> "SHIZUKU OFF"
    is ShizukuState.PermissionRequired -> "PERMISSION NEEDED"
}
