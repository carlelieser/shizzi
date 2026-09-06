package dev.shizzi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

// Uppercase labels are a Neobrutalist signature; Expressive sets them as written.
@Composable
@ReadOnlyComposable
fun themedLabel(text: String): String = when (ShizziTheme.design) {
    DesignLanguage.NEOBRUTALISM -> text.uppercase()
    DesignLanguage.MATERIAL_EXPRESSIVE -> text
}
