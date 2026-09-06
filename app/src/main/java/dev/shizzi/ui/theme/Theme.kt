package dev.shizzi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

@Immutable
data class Appearance(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val design: DesignLanguage = DesignLanguage.MATERIAL_EXPRESSIVE,
    val accent: AccentChoice = AccentChoice.Default,
)

private val LocalShizziColors: ProvidableCompositionLocal<ShizziColors> =
    staticCompositionLocalOf { LightColors }

private val LocalShizziTypography: ProvidableCompositionLocal<ShizziTypography> =
    staticCompositionLocalOf { ExpressiveTypography }

private val LocalShizziSpacing: ProvidableCompositionLocal<ShizziSpacing> =
    staticCompositionLocalOf { Spacing }

private val LocalShizziShapes: ProvidableCompositionLocal<ShizziShapes> =
    staticCompositionLocalOf { ExpressiveShapes }

private val LocalShizziDesign: ProvidableCompositionLocal<DesignLanguage> =
    staticCompositionLocalOf { DesignLanguage.MATERIAL_EXPRESSIVE }

object ShizziTheme {
    val colors: ShizziColors
        @Composable @ReadOnlyComposable get() = LocalShizziColors.current

    val typography: ShizziTypography
        @Composable @ReadOnlyComposable get() = LocalShizziTypography.current

    val spacing: ShizziSpacing
        @Composable @ReadOnlyComposable get() = LocalShizziSpacing.current

    val shapes: ShizziShapes
        @Composable @ReadOnlyComposable get() = LocalShizziShapes.current

    val design: DesignLanguage
        @Composable @ReadOnlyComposable get() = LocalShizziDesign.current
}

@Composable
fun ShizziTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val isDark = when (appearance.theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    val context = LocalContext.current
    val palette = remember(appearance.accent, isDark, context) {
        accentPalette(appearance.accent, isDark, context)
    }

    val isExpressive = appearance.design == DesignLanguage.MATERIAL_EXPRESSIVE

    CompositionLocalProvider(
        LocalShizziColors provides palette.colors,
        LocalShizziTypography provides if (isExpressive) ExpressiveTypography else Typography,
        LocalShizziSpacing provides Spacing,
        LocalShizziShapes provides if (isExpressive) ExpressiveShapes else BrutalShapes,
        LocalShizziDesign provides appearance.design,
        LocalContentColor provides palette.colors.onSurface,
    ) {
        MaterialTheme(
            colorScheme = palette.scheme,
            content = content,
        )
    }
}
