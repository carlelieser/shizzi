package dev.shizzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ShizziTheme

private val FieldMinHeight = 56.dp

private val FocusedOutline = 2.dp

data class TextFieldDecoration(
    val placeholder: String,
    val error: String? = null,
    val keyboard: KeyboardOptions = KeyboardOptions.Default,
    val trailing: (@Composable () -> Unit)? = null,
)

/** A single-line field drawn in the active design language. */
@Composable
fun ThemedTextField(value: String, onValueChange: (String) -> Unit, decoration: TextFieldDecoration) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val colors = ShizziTheme.colors
    val error = decoration.error

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = ShizziTheme.typography.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = decoration.keyboard,
            interactionSource = interaction,
            decorationBox = { input ->
                FieldFrame(
                    frame = FieldState(isFocused = isFocused, isError = error != null, isEmpty = value.isEmpty()),
                    decoration = decoration,
                    input = input,
                )
            },
        )

        if (error == null) return@Column

        Text(
            text = error,
            style = ShizziTheme.typography.caption,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = ShizziTheme.spacing.xs, start = ShizziTheme.spacing.md),
        )
    }
}

private data class FieldState(val isFocused: Boolean, val isError: Boolean, val isEmpty: Boolean)

@Composable
private fun FieldFrame(frame: FieldState, decoration: TextFieldDecoration, input: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fieldSurface(frame)
            .heightIn(min = FieldMinHeight)
            .padding(start = ShizziTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).padding(end = ShizziTheme.spacing.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (frame.isEmpty) {
                Text(
                    text = decoration.placeholder,
                    style = ShizziTheme.typography.body,
                    color = ShizziTheme.colors.onSurfaceMuted,
                )
            }
            input()
        }

        decoration.trailing?.invoke()
    }
}

@Composable
private fun Modifier.fieldSurface(frame: FieldState): Modifier {
    val colors = ShizziTheme.colors
    val accent = when {
        frame.isError -> MaterialTheme.colorScheme.error
        frame.isFocused -> colors.primary
        else -> null
    }

    return when (ShizziTheme.design) {
        DesignLanguage.NEOBRUTALISM -> brutalField(edge = accent ?: colors.border, isRaised = frame.isFocused)
        DesignLanguage.MATERIAL_EXPRESSIVE -> expressiveField(outline = accent)
    }
}

@Composable
private fun Modifier.brutalField(edge: Color, isRaised: Boolean): Modifier {
    val colors = ShizziTheme.colors
    val shapes = ShizziTheme.shapes

    return drawBehind {
        val stroke = shapes.border.toPx()
        val shadow = shapes.shadowOffset.toPx()

        if (isRaised) drawRect(color = colors.shadow, topLeft = Offset(shadow, shadow), size = size)

        drawRect(color = colors.surface, size = size)
        drawRect(
            color = edge,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
private fun Modifier.expressiveField(outline: Color?): Modifier {
    val shape = RoundedCornerShape(ShizziTheme.shapes.corner)
    val filled = clip(shape).background(ShizziTheme.colors.surfaceContainer)

    return if (outline == null) filled else filled.border(FocusedOutline, outline, shape)
}
