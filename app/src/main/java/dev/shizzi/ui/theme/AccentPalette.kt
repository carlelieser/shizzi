package dev.shizzi.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeTonalSpot

private const val DefaultSeed = 0xFF748B7A.toInt()

// The Default swatch shows the app's own teal, which does not move with the
// active accent.
val DefaultAccentColor = Color(DefaultSeed)

private const val StandardContrast = 0.0

private val Roles = MaterialDynamicColors()

private const val BlackArgb = 0xFF000000.toInt()

private const val WhiteArgb = 0xFFFFFFFF.toInt()

fun accentPalette(
    accent: AccentChoice,
    isDark: Boolean,
    design: DesignLanguage,
    context: Context,
): AccentPalette {
    // Neobrutalism outlines every surface with a hard black/white edge instead
    // of the accent's outline tone, which is what gives the style its contrast.
    // Material 3 keeps the softer outline roles the accent generates.
    val edge = hardEdge(isDark).takeIf { design == DesignLanguage.NEOBRUTALISM }

    val dynamic = wallpaperScheme(accent, isDark, context)
    if (dynamic != null) return AccentPalette.Material(dynamic, isDark, edge)

    return AccentPalette.Generated(schemeFor(accent, isDark), isDark, edge)
}

private fun wallpaperScheme(
    accent: AccentChoice,
    isDark: Boolean,
    context: Context,
): ColorScheme? {
    if (accent != AccentChoice.Default && accent != AccentChoice.Expressive) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null

    return when {
        isDark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
}

fun schemeFor(accent: AccentChoice, isDark: Boolean): DynamicScheme = when (accent) {
    AccentChoice.Expressive ->
        SchemeExpressive(Hct.fromInt(DefaultSeed), isDark, StandardContrast)

    is AccentChoice.Custom ->
        SchemeTonalSpot(Hct.fromInt(accent.argb), isDark, StandardContrast)

    AccentChoice.Default ->
        SchemeTonalSpot(Hct.fromInt(DefaultSeed), isDark, StandardContrast)
}

sealed interface AccentPalette {

    val colors: ShizziColors

    val scheme: ColorScheme

    data class Fixed(val isDark: Boolean) : AccentPalette {
        override val colors = if (isDark) DarkColors else LightColors
        override val scheme = materialSchemeOf(colors, isDark)
    }

    data class Generated(
        val source: DynamicScheme,
        val isDark: Boolean,
        val edge: Color? = null,
    ) : AccentPalette {
        override val colors = shizziColorsFrom(source, isDark, edge)
        override val scheme = materialSchemeOf(colors, isDark)
    }

    data class Material(
        val source: ColorScheme,
        val isDark: Boolean,
        val edge: Color? = null,
    ) : AccentPalette {
        override val colors = shizziColorsFrom(source, isDark, edge)
        override val scheme = source
    }
}

fun DynamicScheme.roleArgb(pick: MaterialDynamicColors.() -> DynamicColor): Int =
    Roles.pick().getArgb(this)

fun brutalEdgeArgb(isDark: Boolean): Int = if (isDark) WhiteArgb else BlackArgb

private fun DynamicScheme.role(pick: MaterialDynamicColors.() -> DynamicColor): Color =
    Color(roleArgb(pick))

private fun shizziColorsFrom(scheme: DynamicScheme, isDark: Boolean, edge: Color?) = ShizziColors(
    primary = scheme.role { primary() },
    onPrimary = scheme.role { onPrimary() },
    primaryBright = scheme.role { primaryContainer() },
    background = scheme.role { background() },
    surface = scheme.role { surfaceContainerLow() },
    onSurface = scheme.role { onSurface() },
    onSurfaceMuted = scheme.role { onSurfaceVariant() },
    border = edge ?: scheme.role { outline() },
    shadow = edge ?: scheme.role { outline() },
    isDark = isDark,
    primaryContainer = scheme.role { primaryContainer() },
    onPrimaryContainer = scheme.role { onPrimaryContainer() },
    secondary = scheme.role { secondary() },
    tertiary = scheme.role { tertiary() },
    surfaceVariant = scheme.role { surfaceVariant() },
    surfaceContainer = scheme.role { surfaceContainerHigh() },
)

private fun shizziColorsFrom(scheme: ColorScheme, isDark: Boolean, edge: Color?) = ShizziColors(
    primary = scheme.primary,
    onPrimary = scheme.onPrimary,
    primaryBright = scheme.primaryContainer,
    background = scheme.background,
    surface = scheme.surfaceContainerLow,
    onSurface = scheme.onSurface,
    onSurfaceMuted = scheme.onSurfaceVariant,
    border = edge ?: scheme.outline,
    shadow = edge ?: scheme.outlineVariant,
    isDark = isDark,
    primaryContainer = scheme.primaryContainer,
    onPrimaryContainer = scheme.onPrimaryContainer,
    secondary = scheme.secondary,
    tertiary = scheme.tertiary,
    surfaceVariant = scheme.surfaceVariant,
    surfaceContainer = scheme.surfaceContainerHigh,
)

private fun hardEdge(isDark: Boolean) = Color(brutalEdgeArgb(isDark))

private fun materialSchemeOf(colors: ShizziColors, isDark: Boolean): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()

    return base.copy(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primaryContainer,
        onPrimaryContainer = colors.onPrimaryContainer,
        secondary = colors.secondary,
        tertiary = colors.tertiary,
        background = colors.background,
        surface = colors.surface,
        onSurface = colors.onSurface,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.onSurfaceMuted,
        surfaceContainer = colors.surfaceContainer,
        outline = colors.border,
    )
}
