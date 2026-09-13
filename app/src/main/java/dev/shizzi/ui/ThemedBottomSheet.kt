package dev.shizzi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrutalSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ShizziTheme.colors
    val borderWidth = ShizziTheme.shapes.border

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .drawBehind {
                    val stroke = borderWidth.toPx()

                    drawRect(
                        color = colors.border,
                        size = Size(size.width, stroke),
                    )
                }
                .navigationBarsPadding()
                .padding(ScreenPadding),
            content = content,
        )
    }
}
