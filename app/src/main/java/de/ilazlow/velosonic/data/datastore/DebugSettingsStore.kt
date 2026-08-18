package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.debugSettingsDataStore by preferencesDataStore(name = "debug_settings")

/** Mirrors DebugSettingsView.swift's toggle set exactly. */
data class DebugSettings(
    val loggingEnabled: Boolean = false,
    val redactServerHost: Boolean = true,
    val redactUserDetails: Boolean = true,
    val memoryDiagnosticsEnabled: Boolean = false,
    val radiantLyricsLoggingEnabled: Boolean = false
)

@Singleton
class DebugSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        val REDACT_SERVER_HOST = booleanPreferencesKey("redact_server_host")
        val REDACT_USER_DETAILS = booleanPreferencesKey("redact_user_details")
        val MEMORY_DIAGNOSTICS_ENABLED = booleanPreferencesKey("memory_diagnostics_enabled")
        val RADIANT_LYRICS_LOGGING_ENABLED = booleanPreferencesKey("radiant_lyrics_logging_enabled")
    }

    val settings: Flow<DebugSettings> = context.debugSettingsDataStore.data.map { prefs ->
        DebugSettings(
            loggingEnabled = prefs[Keys.LOGGING_ENABLED] ?: false,
            redactServerHost = prefs[Keys.REDACT_SERVER_HOST] ?: true,
            redactUserDetails = prefs[Keys.REDACT_USER_DETAILS] ?: true,
            memoryDiagnosticsEnabled = prefs[Keys.MEMORY_DIAGNOSTICS_ENABLED] ?: false,
            radiantLyricsLoggingEnabled = prefs[Keys.RADIANT_LYRICS_LOGGING_ENABLED] ?: false
        )
    }

    suspend fun setLoggingEnabled(enabled: Boolean) { context.debugSettingsDataStore.edit { it[Keys.LOGGING_ENABLED] = enabled } }
    suspend fun setRedactServerHost(enabled: Boolean) { context.debugSettingsDataStore.edit { it[Keys.REDACT_SERVER_HOST] = enabled } }
    suspend fun setRedactUserDetails(enabled: Boolean) { context.debugSettingsDataStore.edit { it[Keys.REDACT_USER_DETAILS] = enabled } }
    suspend fun setMemoryDiagnosticsEnabled(enabled: Boolean) { context.debugSettingsDataStore.edit { it[Keys.MEMORY_DIAGNOSTICS_ENABLED] = enabled } }
    suspend fun setRadiantLyricsLoggingEnabled(enabled: Boolean) { context.debugSettingsDataStore.edit { it[Keys.RADIANT_LYRICS_LOGGING_ENABLED] = enabled } }
}
