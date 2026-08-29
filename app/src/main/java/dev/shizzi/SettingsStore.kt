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

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val isLogging: Boolean = true,

    val hasCompletedOnboarding: Boolean = false,

    val isExternalControlEnabled: Boolean = false,
    val externalControlToken: String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map(::toSettings)

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[THEME] = choice.name }
    }

    suspend fun setLogging(enabled: Boolean) {
        context.dataStore.edit { it[LOGGING] = enabled }
    }

    suspend fun setOnboardingComplete(hasCompleted: Boolean) {
        context.dataStore.edit { it[ONBOARDED] = hasCompleted }
    }

    suspend fun setExternalControlEnabled(isEnabled: Boolean) {
        context.dataStore.edit { it[EXTERNAL_CONTROL] = isEnabled }
    }

    suspend fun setExternalControlToken(token: String) {
        context.dataStore.edit { it[EXTERNAL_TOKEN] = token }
    }

    private fun toSettings(preferences: Preferences) = Settings(
        theme = runCatching { ThemeChoice.valueOf(preferences[THEME].orEmpty()) }
            .getOrDefault(ThemeChoice.SYSTEM),
        isLogging = preferences[LOGGING] ?: true,
        hasCompletedOnboarding = preferences[ONBOARDED] ?: false,
        isExternalControlEnabled = preferences[EXTERNAL_CONTROL] ?: false,
        externalControlToken = preferences[EXTERNAL_TOKEN].orEmpty(),
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LOGGING = booleanPreferencesKey("logging")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val EXTERNAL_CONTROL = booleanPreferencesKey("external_control")
        val EXTERNAL_TOKEN = stringPreferencesKey("external_control_token")
    }
}
