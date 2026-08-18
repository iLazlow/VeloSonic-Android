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

private val Context.offlineModeDataStore by preferencesDataStore(name = "offline_mode_settings")

/**
 * Mirrors iOS's `OfflineModeSettings.isEnabled` — the toggle at the very top of the Settings
 * list. UI-only for now: real network/sync gating (stopping audio analysis, applying to
 * NetworkMonitor, flushing the scrobble queue when turned off, etc.) is deliberately not wired
 * yet, matching the staged "structure first, function later" rebuild plan.
 */
@Singleton
class OfflineModeStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("offline_mode_enabled")
    }

    val isEnabled: Flow<Boolean> = context.offlineModeDataStore.data.map { it[Keys.ENABLED] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        context.offlineModeDataStore.edit { it[Keys.ENABLED] = enabled }
    }
}
