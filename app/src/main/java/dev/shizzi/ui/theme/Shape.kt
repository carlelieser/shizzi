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
val ShadowOffset = 6.dp

/**
 * Draws the app's one surface treatment: a hard offset shadow, a thick border,
 * and a flat fill.
 *
 * Material's elevation shadow is blurred and colour-filtered, which is the
 * opposite of what this design needs, so the shadow is drawn as a plain solid
 * rectangle displaced from the element. Routing every bordered element through
 * a single modifier is what keeps the treatment identical everywhere instead
 * of drifting per component.
 *
 * The border is always the theme's structural colour — black in light, white in
 * dark — because its job is to separate the surface from the page. Tinting it
 * to match a turquoise fill draws it in the colour it sits on and it disappears,
 * which is what happened when the accent border was applied to the filled
 * button.
 *
 * @param isPressed shifts the element into its shadow. The shadow is
 *   suppressed and the content translated by the same offset, so the surface
 *   appears to depress to meet the page.
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

            // Suppressed while pressed: the element has moved into the space
            // the shadow occupied, so drawing both would double the mass.
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
 * Tracks press state for [brutalSurface].
 *
 * Kept next to the modifier so a caller wiring up a pressable surface does not
 * have to remember which interaction to collect.
 */
@Composable
fun InteractionSource.isPressed(): Boolean = collectIsPressedAsState().value
