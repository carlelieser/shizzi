package dev.shizzi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A 4dp grid. Anything off this scale is a mistake, not a decision. */
@Immutable
data class ShizziSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

val Spacing = ShizziSpacing()

/** Screen edge padding. */
val ScreenPadding = Spacing.lg

/** Header height, shared by all three screens so they line up. */
val HeaderHeight = 56.dp

/** The floor for anything tappable: a 24dp icon still needs 48dp around it. */
val MinTouchTarget = 48.dp
