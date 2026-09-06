package dev.shizzi.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ripple
import androidx.compose.foundation.Indication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ShizziShapes(
    val corner: Dp,
    val border: Dp,
    val shadowOffset: Dp,
)

val BrutalShapes = ShizziShapes(corner = 0.dp, border = 2.dp, shadowOffset = 4.dp)

val ExpressiveShapes = ShizziShapes(corner = 20.dp, border = 0.dp, shadowOffset = 0.dp)

enum class SurfaceElevation { FLAT, RAISED }

private const val PressedScale = 0.97f

private val RaisedElevation = 3.dp

fun Modifier.themedSurface(
    fill: Color,
    elevation: SurfaceElevation = SurfaceElevation.RAISED,
    isPressed: Boolean = false,
): Modifier = composed {
    when (ShizziTheme.design) {
        DesignLanguage.NEOBRUTALISM -> brutalSurface(fill, elevation, isPressed)
        DesignLanguage.MATERIAL_EXPRESSIVE -> expressiveSurface(fill, elevation, isPressed)
    }
}

@Composable
private fun Modifier.brutalSurface(
    fill: Color,
    elevation: SurfaceElevation,
    isPressed: Boolean,
): Modifier {
    val colors = ShizziTheme.colors
    val shapes = ShizziTheme.shapes
    val hasShadow = elevation == SurfaceElevation.RAISED
    val shift by animateDpAsState(
        targetValue = if (isPressed && hasShadow) shapes.shadowOffset else 0.dp,
        animationSpec = fastSpring(),
        label = "surfaceShift",
    )

    return this
        .offset(x = shift, y = shift)
        .drawBehind {
            val stroke = shapes.border.toPx()
            val shadow = shapes.shadowOffset.toPx()
            val gap = shadow - shift.toPx()

            if (hasShadow && gap > 0f) {
                drawRect(
                    color = colors.shadow,
                    topLeft = Offset(gap, gap),
                    size = size,
                )
            }

            drawRect(color = fill, size = size)

            drawRect(
                color = colors.border,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
        }
}

@Composable
private fun Modifier.expressiveSurface(
    fill: Color,
    elevation: SurfaceElevation,
    isPressed: Boolean,
): Modifier {
    val shape = RoundedCornerShape(ShizziTheme.shapes.corner)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PressedScale else 1f,
        animationSpec = fastSpring(),
        label = "surfaceScale",
    )

    val elevationDp = if (elevation == SurfaceElevation.RAISED) RaisedElevation else 0.dp

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(elevation = elevationDp, shape = shape)
        .clip(shape)
        .background(color = fill)
}

@Composable
fun themedIndication(): Indication? = when (ShizziTheme.design) {
    DesignLanguage.NEOBRUTALISM -> null
    DesignLanguage.MATERIAL_EXPRESSIVE -> ripple()
}

@Composable
fun InteractionSource.isPressed(): Boolean = collectIsPressedAsState().value
