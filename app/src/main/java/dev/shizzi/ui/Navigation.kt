package dev.shizzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * A stack, not a graph: Home is the root, Settings its child, Log a child of
 * Settings. A navigation library would be a dependency and a graph declaration
 * to express exactly this.
 */
enum class Screen { HOME, LOG, SETTINGS }

/** By name rather than Parcelable, which means a plugin to serialise an enum. */
private val ScreenSaver = Saver<MutableState<Screen>, String>(
    save = { it.value.name },
    restore = { name ->
        mutableStateOf(runCatching { Screen.valueOf(name) }.getOrDefault(Screen.HOME))
    },
)

/** Saveable so a rotation while reading the log does not return to Home. */
@Composable
fun rememberNavigator(): MutableState<Screen> =
    rememberSaveable(saver = ScreenSaver) { mutableStateOf(Screen.HOME) }

/** Disabled on Home, so back leaves the app rather than being swallowed. */
@Composable
fun HandleBack(current: Screen, onBack: () -> Unit) {
    BackHandler(enabled = current != Screen.HOME, onBack = onBack)
}
