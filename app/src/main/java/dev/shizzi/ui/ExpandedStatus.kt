package dev.shizzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme

/** Fades over the row's side padding, where nothing is drawn, so no segment shows through. */
private val EdgeFade = ScreenPadding

/** A full-width copy of the status laid over the row and its padding, so the row itself never reflows. */
@Composable
fun ExpandedStatus(text: AnnotatedString, onCollapse: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .fadedEdges(EdgeFade)
            .background(ShizziTheme.colors.background)
            .clickable(interactionSource = interaction, indication = null, onClick = onCollapse),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ShizziTheme.typography.caption,
            color = ShizziTheme.colors.onSurfaceMuted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = EdgeFade).basicMarquee(),
        )
    }
}

/** Fades the left and right [width] of everything drawn, so the overlay has no hard edge. */
private fun Modifier.fadedEdges(width: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        val mask = Brush.horizontalGradient(
            0f to Color.Transparent,
            edge to Color.Black,
            1f - edge to Color.Black,
            1f to Color.Transparent,
        )
        drawRect(mask, blendMode = BlendMode.DstIn)
    }
