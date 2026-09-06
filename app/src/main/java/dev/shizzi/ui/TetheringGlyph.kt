package dev.shizzi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Paints the tethering icon with a [Brush] rather than a flat tint,
 * which `Icon` does not support. The gradient is masked to the glyph's
 * own coverage with `SrcIn`.
 */
@Composable
fun TetheringGlyph(brush: Brush, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcIn)
            },
    ) {
        Icon(
            imageVector = Icons.Filled.WifiTethering,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.matchParentSize(),
        )
    }
}
