package dev.shizzi

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.shizzi.ui.theme.AccentChoice
import dev.shizzi.ui.theme.DesignLanguage
import dev.shizzi.ui.theme.ThemeChoice
import dev.shizzi.ui.theme.parseAccent
import dev.shizzi.ui.theme.parseAccents
import dev.shizzi.ui.theme.serialize
import dev.shizzi.ui.theme.serializeAccents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val design: DesignLanguage = DesignLanguage.MATERIAL_EXPRESSIVE,
    val accent: AccentChoice = AccentChoice.Default,
    val customAccents: List<Int> = emptyList(),
    val isLogging: Boolean = true,

    val hasCompletedOnboarding: Boolean = false,

    val isAutomationEnabled: Boolean = false,
    val automationToken: String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(RenamedKeys.migration()) },
)

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map(::toSettings)

    suspend fun backfillTokenIfEnabled() {
        context.dataStore.edit { preferences ->
            if (preferences[AUTOMATION] != true) return@edit

            preferences.mintTokenIfAbsent()
        }
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.dataStore.edit { it[THEME] = choice.name }
    }

    suspend fun setDesign(design: DesignLanguage) {
        context.dataStore.edit { it[DESIGN] = design.name }
    }

    suspend fun setAccent(accent: AccentChoice) {
        context.dataStore.edit { it[ACCENT] = accent.serialize() }
    }

    suspend fun addCustomAccent(argb: Int) {
        context.dataStore.edit { preferences ->
            val existing = parseAccents(preferences[CUSTOM_ACCENTS])
            if (argb in existing) return@edit

            preferences[CUSTOM_ACCENTS] = serializeAccents(existing + argb)
        }
    }

    suspend fun setLogging(enabled: Boolean) {
        context.dataStore.edit { it[LOGGING] = enabled }
    }

    suspend fun setOnboardingComplete(hasCompleted: Boolean) {
        context.dataStore.edit { it[ONBOARDED] = hasCompleted }
    }

    suspend fun setAutomationEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOMATION] = isEnabled
            if (isEnabled) preferences.mintTokenIfAbsent()
        }
    }

    suspend fun setAutomationToken(token: String) {
        context.dataStore.edit { it[AUTOMATION_TOKEN] = token }
    }

    private fun MutablePreferences.mintTokenIfAbsent() {
        if (!this[AUTOMATION_TOKEN].isNullOrEmpty()) return

        this[AUTOMATION_TOKEN] = AutomationToken.generate()
    }

}

internal val THEME = stringPreferencesKey("theme")
internal val DESIGN = stringPreferencesKey("design")
internal val ACCENT = stringPreferencesKey("accent")
internal val CUSTOM_ACCENTS = stringPreferencesKey("custom_accents")
internal val LOGGING = booleanPreferencesKey("logging")
internal val ONBOARDED = booleanPreferencesKey("onboarded")
internal val AUTOMATION = booleanPreferencesKey("automation")
internal val AUTOMATION_TOKEN = stringPreferencesKey("automation_token")

internal fun toSettings(preferences: Preferences) = Settings(
    theme = runCatching { ThemeChoice.valueOf(preferences[THEME].orEmpty()) }
        .getOrDefault(ThemeChoice.SYSTEM),
    design = runCatching { DesignLanguage.valueOf(preferences[DESIGN].orEmpty()) }
        .getOrDefault(DesignLanguage.MATERIAL_EXPRESSIVE),
    accent = parseAccent(preferences[ACCENT]),
    customAccents = parseAccents(preferences[CUSTOM_ACCENTS]),
    isLogging = preferences[LOGGING] ?: true,
    hasCompletedOnboarding = preferences[ONBOARDED] ?: false,
    isAutomationEnabled = preferences[AUTOMATION] ?: false,
    automationToken = preferences[AUTOMATION_TOKEN].orEmpty(),
)
