package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import dev.shizzi.UiText
import dev.shizzi.resolve

@Composable
@ReadOnlyComposable
fun UiText.resolve(): String = resolve(LocalResources.current)
