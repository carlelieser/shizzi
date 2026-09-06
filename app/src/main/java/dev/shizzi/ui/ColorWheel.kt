package dev.shizzi.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val FullTurn = 360f

private val HueStops = listOf(
    Color.hsv(0f, 1f, 1f),
    Color.hsv(60f, 1f, 1f),
    Color.hsv(120f, 1f, 1f),
    Color.hsv(180f, 1f, 1f),
    Color.hsv(240f, 1f, 1f),
    Color.hsv(300f, 1f, 1f),
    Color.hsv(360f, 1f, 1f),
)

private val SelectorRadius = 8.dp

private val SelectorStroke = 2.dp

data class HueSaturation(val hue: Float, val saturation: Float)

fun hueSaturationAt(offset: Offset, center: Offset, radius: Float): HueSaturation {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

    return HueSaturation(
        hue = (degrees + FullTurn) % FullTurn,
        saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f),
    )
}

fun positionOf(selection: HueSaturation, center: Offset, radius: Float): Offset {
    val radians = Math.toRadians(selection.hue.toDouble())
    val distance = selection.saturation.coerceIn(0f, 1f) * radius

    return Offset(
        x = center.x + (cos(radians).toFloat() * distance),
        y = center.y + (sin(radians).toFloat() * distance),
    )
}

@Composable
fun ColorWheel(
    selection: HueSaturation,
    value: Float,
    onSelect: (HueSaturation) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { onSelect(hueSaturationAt(it, center(), radius())) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onSelect(hueSaturationAt(change.position, center(), radius()))
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        drawCircle(brush = Brush.sweepGradient(HueStops, center), radius = radius)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
        )

        drawCircle(color = Color.Black.copy(alpha = 1f - value), radius = radius)

        drawCircle(
            color = Color.White,
            radius = SelectorRadius.toPx(),
            center = positionOf(selection, center, radius),
            style = Stroke(width = SelectorStroke.toPx()),
        )
    }
}

private fun PointerInputScope.center() = Offset(size.width / 2f, size.height / 2f)

private fun PointerInputScope.radius() = minOf(size.width, size.height) / 2f
