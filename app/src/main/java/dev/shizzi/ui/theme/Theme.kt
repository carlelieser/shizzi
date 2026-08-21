package dev.shizzi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

private val LocalShizziColors: ProvidableCompositionLocal<ShizziColors> =
    staticCompositionLocalOf { LightColors }

private val LocalShizziTypography: ProvidableCompositionLocal<ShizziTypography> =
    staticCompositionLocalOf { Typography }

private val LocalShizziSpacing: ProvidableCompositionLocal<ShizziSpacing> =
    staticCompositionLocalOf { Spacing }

object ShizziTheme {
    val colors: ShizziColors
        @Composable @ReadOnlyComposable get() = LocalShizziColors.current

    val typography: ShizziTypography
        @Composable @ReadOnlyComposable get() = LocalShizziTypography.current

    val spacing: ShizziSpacing
        @Composable @ReadOnlyComposable get() = LocalShizziSpacing.current
}

@Composable
fun ShizziTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val colors = if (isDark) DarkColors else LightColors

    CompositionLocalProvider(
        LocalShizziColors provides colors,
        LocalShizziTypography provides Typography,
        LocalShizziSpacing provides Spacing,
        LocalContentColor provides colors.onSurface,
    ) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(colors, isDark),
            content = content,
        )
    }
}

private fun materialSchemeFrom(colors: ShizziColors, isDark: Boolean) = when {
    isDark -> darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        background = colors.background,
        surface = colors.surface,
        onSurface = colors.onSurface,
        onSurfaceVariant = colors.onSurfaceMuted,
        outline = colors.border,
    )

    else -> lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        background = colors.background,
        surface = colors.surface,
        onSurface = colors.onSurface,
        onSurfaceVariant = colors.onSurfaceMuted,
        outline = colors.border,
    )
}
