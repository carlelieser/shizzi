package dev.shizzi.spike

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.shizzi.spike.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the user can configure, in one readable shape. */
data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val isDebugLogging: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * Persists the two settings the app has.
 *
 * Debug logging previously lived in ViewModel memory and reset on every
 * launch, which made it a setting the user could not rely on. Both values now
 * outlive the process.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map(::toSettings)

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[THEME] = choice.name }
    }

    suspend fun setDebugLogging(enabled: Boolean) {
        context.dataStore.edit { it[DEBUG_LOGGING] = enabled }
    }

    /**
     * An unrecognised stored theme falls back rather than throwing.
     *
     * A downgrade, or a value written by a future build, should not crash the
     * app on launch over a preference.
     */
    private fun toSettings(preferences: Preferences) = Settings(
        theme = runCatching { ThemeChoice.valueOf(preferences[THEME].orEmpty()) }
            .getOrDefault(ThemeChoice.SYSTEM),
        isDebugLogging = preferences[DEBUG_LOGGING] ?: false,
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
    }
}
