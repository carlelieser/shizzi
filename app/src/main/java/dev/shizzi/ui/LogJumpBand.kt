package dev.shizzi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.shizzi.ui.theme.ShizziTheme
import kotlinx.coroutines.launch

private val JumpBandHeight = 96.dp

private const val JumpThreshold = 8

enum class JumpEdge { TOP, BOTTOM }

@Composable
fun JumpBand(
    edge: JumpEdge,
    listState: LazyListState,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val colors = ShizziTheme.colors
    val isTop = edge == JumpEdge.TOP

    val isShowing by remember(count, edge) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val distance = when {

                isTop -> visible.firstOrNull()?.index ?: 0
                else -> count - 1 - (visible.lastOrNull()?.index ?: 0)
            }
            distance > JumpThreshold
        }
    }

    val fade = listOf(colors.background, colors.background.copy(alpha = 0f))

    AnimatedVisibility(
        visible = isShowing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(JumpBandHeight)
                .background(
                    Brush.verticalGradient(if (isTop) fade else fade.asReversed()),
                ),
            contentAlignment = if (isTop) Alignment.TopCenter else Alignment.BottomCenter,
        ) {

            Text(
                text = if (isTop) "SCROLL TO TOP" else "SCROLL TO BOTTOM",
                style = ShizziTheme.typography.label,
                color = colors.onSurface,
                modifier = Modifier

                    .padding(
                        top = if (isTop) ShizziTheme.spacing.lg else 0.dp,
                        bottom = if (isTop) 0.dp else ShizziTheme.spacing.lg,
                    )
                    .clickable {

                        scope.launch {
                            listState.animateScrollToItem(if (isTop) 0 else count - 1)
                        }
                    }
                    .padding(ShizziTheme.spacing.sm),
            )
        }
    }
}
