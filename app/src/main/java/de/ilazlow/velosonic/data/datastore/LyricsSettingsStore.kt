package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lyricsSettingsDataStore by preferencesDataStore(name = "lyrics_settings")

/** Mirrors StreamingSettings.lyricsSource's three modes exactly. Radiant Lyrics sits outside
 *  this enum on iOS too — it's tried first, unconditionally of this pick, gated by its own
 *  [LyricsSettings.radiantLyricsEnabled] toggle instead. */
enum class LyricsSource { AUTO, NAVIDROME, LRCLIB }

data class LyricsSettings(
    val source: LyricsSource = LyricsSource.AUTO,
    val radiantLyricsEnabled: Boolean = true,
    val radiantLyricsRomanization: Boolean = false,
    val radiantLyricsSparklesEnabled: Boolean = true,
    // Off by default — enabling this sends the request to a server-side AI model, kept as an
    // explicit opt-in rather than bundled into the general enable toggle (mirrors iOS's
    // deliberately separate Settings section for the same disclosure reason).
    val radiantLyricsAiSynthesizeEnabled: Boolean = false,
    // Off by default, unlike the general library sync — a per-track lyrics lookup across a large
    // library means real background network+battery cost (see LyricsSyncWorker's doc comment on
    // why it's even batched/rate-limited in the first place), so this is an explicit opt-in
    // rather than something that just silently starts happening for everyone. See
    // [VeloSonicApp.onCreate] for where this actually gates [LyricsSyncScheduler].
    val lyricsAutoSyncEnabled: Boolean = false
)

@Singleton
class LyricsSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SOURCE = stringPreferencesKey("lyrics_source")
        val RADIANT_ENABLED = booleanPreferencesKey("radiant_lyrics_enabled")
        val RADIANT_ROMANIZATION = booleanPreferencesKey("radiant_lyrics_romanization")
        val RADIANT_SPARKLES = booleanPreferencesKey("radiant_lyrics_sparkles_enabled")
        val RADIANT_AI_SYNTHESIZE = booleanPreferencesKey("radiant_lyrics_ai_synthesize_enabled")
        val AUTO_SYNC = booleanPreferencesKey("lyrics_auto_sync_enabled")
    }

    val settings: Flow<LyricsSettings> = context.lyricsSettingsDataStore.data.map { prefs ->
        LyricsSettings(
            source = prefs[Keys.SOURCE]?.let { raw -> runCatching { LyricsSource.valueOf(raw) }.getOrNull() }
                ?: LyricsSource.AUTO,
            radiantLyricsEnabled = prefs[Keys.RADIANT_ENABLED] ?: true,
            radiantLyricsRomanization = prefs[Keys.RADIANT_ROMANIZATION] ?: false,
            radiantLyricsSparklesEnabled = prefs[Keys.RADIANT_SPARKLES] ?: true,
            radiantLyricsAiSynthesizeEnabled = prefs[Keys.RADIANT_AI_SYNTHESIZE] ?: false,
            lyricsAutoSyncEnabled = prefs[Keys.AUTO_SYNC] ?: false
        )
    }

    suspend fun setSource(source: LyricsSource) {
        context.lyricsSettingsDataStore.edit { it[Keys.SOURCE] = source.name }
    }

    suspend fun setRadiantLyricsEnabled(enabled: Boolean) {
        context.lyricsSettingsDataStore.edit { it[Keys.RADIANT_ENABLED] = enabled }
    }

    suspend fun setRadiantLyricsRomanization(enabled: Boolean) {
        context.lyricsSettingsDataStore.edit { it[Keys.RADIANT_ROMANIZATION] = enabled }
    }

    suspend fun setRadiantLyricsSparklesEnabled(enabled: Boolean) {
        context.lyricsSettingsDataStore.edit { it[Keys.RADIANT_SPARKLES] = enabled }
    }

    suspend fun setRadiantLyricsAiSynthesizeEnabled(enabled: Boolean) {
        context.lyricsSettingsDataStore.edit { it[Keys.RADIANT_AI_SYNTHESIZE] = enabled }
    }

    suspend fun setLyricsAutoSyncEnabled(enabled: Boolean) {
        context.lyricsSettingsDataStore.edit { it[Keys.AUTO_SYNC] = enabled }
    }
}
