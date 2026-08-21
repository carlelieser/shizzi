package dev.shizzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

enum class Screen { HOME, LOG, SETTINGS }

private val ScreenSaver = Saver<MutableState<Screen>, String>(
    save = { it.value.name },
    restore = { name ->
        mutableStateOf(runCatching { Screen.valueOf(name) }.getOrDefault(Screen.HOME))
    },
)

@Composable
fun rememberNavigator(): MutableState<Screen> =
    rememberSaveable(saver = ScreenSaver) { mutableStateOf(Screen.HOME) }

@Composable
fun HandleBack(current: Screen, onBack: () -> Unit) {
    BackHandler(enabled = current != Screen.HOME, onBack = onBack)
}
