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

/** Every corner in the app. Sharp, without exception. */
val CornerRadius = 0.dp

/** Border thickness, thick enough to read as structure rather than as a hairline. */
val BorderWidth = 2.dp

/** Shadow displacement, down and to the right. Zero blur. */
val ShadowOffset = 4.dp

/**
 * The app's one surface treatment: hard offset shadow, thick border, flat fill.
 * Material's elevation shadow is blurred and colour-filtered, so the shadow is
 * a plain displaced rectangle instead. One modifier for every bordered element,
 * so the treatment cannot drift per component.
 *
 * The border always takes the theme's structural colour, since its job is to
 * separate surface from page — tinting it to match a turquoise fill drew it in
 * the colour it sits on and it vanished.
 *
 * @param isPressed shifts the element into its shadow, which is suppressed, so
 *   the surface appears to depress to meet the page.
 */
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

            // The element has moved into the space the shadow occupied, so
            // drawing both would double the mass.
            if (!isPressed) {
                drawRect(
                    color = colors.shadow,
                    topLeft = Offset(shadow, shadow),
                    size = size,
                )
            }

            drawRect(color = fill, size = size)

            // Inset by half the stroke so the border sits inside the bounds
            // rather than straddling them, which would clip against a sibling.
            drawRect(
                color = border,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke),
            )
        }
}

/**
 * Kept beside [brutalSurface] so a caller wiring up a pressable surface does
 * not have to remember which interaction to collect.
 */
@Composable
fun InteractionSource.isPressed(): Boolean = collectIsPressedAsState().value
