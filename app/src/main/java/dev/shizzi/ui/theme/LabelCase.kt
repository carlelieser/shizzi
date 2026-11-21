package dev.shizzi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

// Uppercase labels are a Neobrutalist signature; Expressive sets them as written.
fun labelCase(text: String, design: DesignLanguage, locale: Locale): String = when (design) {
    DesignLanguage.NEOBRUTALISM -> text.uppercase(locale)
    DesignLanguage.MATERIAL_EXPRESSIVE -> text
}

@Composable
@ReadOnlyComposable
fun themedLabel(text: String): String = labelCase(text, ShizziTheme.design, currentLocale())

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
