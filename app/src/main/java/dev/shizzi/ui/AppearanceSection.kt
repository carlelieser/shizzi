package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.shizzi.R
import dev.shizzi.str
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ThemeChoice

data class AppearanceState(val theme: ThemeChoice, val design: DesignLanguage, val accent: AccentChoice)
data class AppearanceActions(
    val onSetTheme: (ThemeChoice) -> Unit,
    val onSetDesign: (DesignLanguage) -> Unit,
    val onSetAccent: (AccentChoice) -> Unit,
)

@Composable
fun AppearanceSection(state: AppearanceState, actions: AppearanceActions) {
    var isAccentOpen by remember { mutableStateOf(false) }
    var isDesignOpen by remember { mutableStateOf(false) }

    ThemePicker(selected = state.theme, onSelect = actions.onSetTheme)
    SettingsChoice(
        label = SettingsText(title = str(R.string.design)),
        value = designLabel(state.design),
        onClick = { isDesignOpen = true },
    )
    SettingsChoice(
        label = SettingsText(title = str(R.string.accent_color)),
        value = accentLabel(state.accent),
        onClick = { isAccentOpen = true },
    )
    if (isDesignOpen) DesignPicker(
        selected = state.design,
        onSelect = actions.onSetDesign,
        onDismiss = { isDesignOpen = false },
    )
    if (isAccentOpen) AccentPicker(
        state = AccentPickerState(selected = state.accent),
        actions = AccentPickerActions(onSelect = actions.onSetAccent, onDismiss = { isAccentOpen = false }),
    )
}
