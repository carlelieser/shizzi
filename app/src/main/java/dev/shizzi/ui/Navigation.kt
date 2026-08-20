package dev.shizzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * The three screens, which form a stack rather than a graph.
 *
 * Home is the root, Settings its child, and Log a child of Settings — the only
 * way into the log is the row under Developer. A navigation library would add a
 * dependency and a graph declaration to express exactly this, so the app keeps
 * its own.
 */
enum class Screen { HOME, LOG, SETTINGS }

/**
 * Saved by name rather than by Parcelable.
 *
 * The alternative is the kotlin-parcelize plugin, which is a build dependency
 * to serialise what is already an enum.
 */
private val ScreenSaver = Saver<MutableState<Screen>, String>(
    save = { it.value.name },
    restore = { name ->
        mutableStateOf(runCatching { Screen.valueOf(name) }.getOrDefault(Screen.HOME))
    },
)

/**
 * Remembers the current screen across configuration change and process death.
 *
 * Saveable rather than plain state: a rotation while reading the log should
 * not return the user to Home.
 */
@Composable
fun rememberNavigator(): MutableState<Screen> =
    rememberSaveable(saver = ScreenSaver) { mutableStateOf(Screen.HOME) }

/**
 * Sends the system back gesture to Home, for the two child screens.
 *
 * Disabled on Home so back leaves the app rather than being swallowed.
 */
@Composable
fun HandleBack(current: Screen, onBack: () -> Unit) {
    BackHandler(enabled = current != Screen.HOME, onBack = onBack)
}
