package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.audioAnalysisSettingsDataStore by preferencesDataStore(name = "audio_analysis_settings")

/** Mirrors AudioAnalysisSettings.swift's UserDefaults-backed properties. [concurrentSongs] is
 *  clamped 1-16 on write, matching the real clamp in the Swift setter (its inline doc comment
 *  says "range 1-8", but the actual code clamps 1-16 — ported the real clamp, not the stale
 *  comment). */
data class AudioAnalysisSettings(
    val analyzeNonDownloaded: Boolean = true,
    val concurrentSongs: Int = 4
)

@Singleton
class AudioAnalysisSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ANALYZE_NON_DOWNLOADED = booleanPreferencesKey("analyze_non_downloaded")
        val CONCURRENT_SONGS = intPreferencesKey("concurrent_songs")
    }

    val settings: Flow<AudioAnalysisSettings> = context.audioAnalysisSettingsDataStore.data.map { prefs ->
        AudioAnalysisSettings(
            analyzeNonDownloaded = prefs[Keys.ANALYZE_NON_DOWNLOADED] ?: true,
            concurrentSongs = prefs[Keys.CONCURRENT_SONGS] ?: 4
        )
    }

    suspend fun setAnalyzeNonDownloaded(enabled: Boolean) {
        context.audioAnalysisSettingsDataStore.edit { it[Keys.ANALYZE_NON_DOWNLOADED] = enabled }
    }

    suspend fun setConcurrentSongs(count: Int) {
        context.audioAnalysisSettingsDataStore.edit { it[Keys.CONCURRENT_SONGS] = count.coerceIn(1, 16) }
    }
}
