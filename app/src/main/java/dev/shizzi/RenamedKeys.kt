package dev.shizzi

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey

object RenamedKeys {

    private val RETIRED_AUTOMATION = booleanPreferencesKey("external_control")
    private val RETIRED_TOKEN = stringPreferencesKey("external_control_token")

    private val AUTOMATION = booleanPreferencesKey("automation")
    private val TOKEN = stringPreferencesKey("automation_token")

    fun isPending(preferences: Preferences): Boolean =
        preferences.contains(RETIRED_AUTOMATION) || preferences.contains(RETIRED_TOKEN)

    fun apply(preferences: Preferences): Preferences {
        val updated = mutablePreferencesOf().apply { plusAssign(preferences) }

        carryBoolean(updated)
        carryToken(updated)

        updated.remove(RETIRED_AUTOMATION)
        updated.remove(RETIRED_TOKEN)
        return updated
    }

    private fun carryBoolean(preferences: MutablePreferences) {
        val retired = preferences[RETIRED_AUTOMATION] ?: return
        if (preferences.contains(AUTOMATION)) return

        preferences[AUTOMATION] = retired
    }

    private fun carryToken(preferences: MutablePreferences) {
        val retired = preferences[RETIRED_TOKEN] ?: return
        if (preferences.contains(TOKEN)) return

        preferences[TOKEN] = retired
    }

    fun migration(): DataMigration<Preferences> = object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences) = isPending(currentData)

        override suspend fun migrate(currentData: Preferences) = apply(currentData)

        override suspend fun cleanUp() = Unit
    }
}
