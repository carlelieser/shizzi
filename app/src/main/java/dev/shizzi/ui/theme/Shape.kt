package dev.shizzi.ui.theme

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val CornerRadius = 0.dp

val BorderWidth = 2.dp

val ShadowOffset = 4.dp

fun Modifier.brutalSurface(
    fill: Color,
    isPressed: Boolean = false,
): Modifier = composed {
    val colors = ShizziTheme.colors
    val border = colors.border
    val shift = if (isPressed) ShadowOffset else 0.dp

    this
        .offset(x = shift, y = shift)
        .drawBehind {
            val stroke = BorderWidth.toPx()
            val shadow = ShadowOffset.toPx()

            if (!isPressed) {
                drawRect(
                    color = colors.shadow,
                    topLeft = Offset(shadow, shadow),
                    size = size,
                )
            }

            drawRect(color = fill, size = size)

            drawRect(
                color = border,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
        }
}

@Composable
fun InteractionSource.isPressed(): Boolean = collectIsPressedAsState().value
