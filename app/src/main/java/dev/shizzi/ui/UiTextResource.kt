package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.UiText
import dev.shizzi.resolve

@Composable
@ReadOnlyComposable
fun UiText.resolve(): String {
    // Subscribes to configuration changes so locale switches recompose, as stringResource does.
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}
