package dev.shizzi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.shizzi.ui.theme.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the user can configure, in one readable shape. */
data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val isLogging: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * Persists the two settings the app has. Logging used to live in ViewModel
 * memory and reset on every launch, which made it unreliable as a setting.
 */
class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map(::toSettings)

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[THEME] = choice.name }
    }

    suspend fun setLogging(enabled: Boolean) {
        context.dataStore.edit { it[LOGGING] = enabled }
    }

    /**
     * An unrecognised theme falls back rather than throwing: a downgrade, or a
     * value from a future build, should not crash the app over a preference.
     */
    private fun toSettings(preferences: Preferences) = Settings(
        theme = runCatching { ThemeChoice.valueOf(preferences[THEME].orEmpty()) }
            .getOrDefault(ThemeChoice.SYSTEM),
        isLogging = preferences[LOGGING] ?: true,
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LOGGING = booleanPreferencesKey("logging")
    }
}
