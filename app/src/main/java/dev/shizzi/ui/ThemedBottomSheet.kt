package dev.shizzi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme

private val SheetCorner = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isBrutal = ShizziTheme.design == DesignLanguage.NEOBRUTALISM

    if (isBrutal) {
        BrutalSheet(sheetState = sheetState, onDismiss = onDismiss, content = content)
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .navigationBarsPadding(),
            content = content,
        )
    }
}

// A sheet is anchored to the bottom edge, so it takes a rounded top and a rule
// along the edge it meets rather than themedSurface's floating card treatment.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrutalSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ShizziTheme.colors
    val shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)
    val borderWidth = ShizziTheme.shapes.border

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        shape = shape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(width = borderWidth, color = colors.border, shape = shape)
                .padding(ScreenPadding)
                .navigationBarsPadding(),
            content = content,
        )
    }
}
