package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.playback.ContinuousMixMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackSettingsDataStore by preferencesDataStore(name = "playback_settings")

data class PlaybackSettings(
    val continuousPlaybackEnabled: Boolean = true,
    val continuousMixMode: ContinuousMixMode = ContinuousMixMode.SIMILAR,
    /** Mirrors iOS's `continuousMixLimit` — how many tracks a single continuous-mix fetch queues. */
    val continuousMixLimit: Int = 10,
    val gaplessEnabled: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeSeconds: Int = 5,
    val replayGainEnabled: Boolean = false,
    val scrobblingEnabled: Boolean = true,
    val scrobbleThreshold: Double = 0.2,
    /** Mirrors iOS's `StreamingSettings.sonicSimilarSongs` — a user-facing opt-out for the
     *  server-side AudioMuse-AI sonic-similarity plugin (Navidrome >= 0.62), on top of the
     *  existing [de.ilazlow.velosonic.data.db.ServerConfigEntity]-derived capability check.
     *  Gates both continuous-mix queue extension and the player's Similar Songs/Artists section. */
    val sonicSimilarSongsEnabled: Boolean = true,
    val syncPlayQueueEnabled: Boolean = false,
    val streamingModeEnabled: Boolean = false,
    val preferLosslessOnWifiEnabled: Boolean = false,
    val keepScreenAwakeEnabled: Boolean = false,
    val sfwModeEnabled: Boolean = false,
    val wifiFormat: StreamFormat = StreamFormat.RAW,
    val wifiBitrate: StreamBitrate = StreamBitrate.ORIGINAL,
    val onlyTranscodeLosslessWifi: Boolean = false,
    val customWifiFormat: String = "",
    val customWifiBitrate: String = "",
    val cellularFormat: StreamFormat = StreamFormat.AAC,
    val cellularBitrate: StreamBitrate = StreamBitrate.KBPS_192,
    val onlyTranscodeLosslessCellular: Boolean = false,
    val customCellularFormat: String = "",
    val customCellularBitrate: String = ""
)

/**
 * Playback-tuning knobs surfaced in the Settings screen's Playback section. Mirrors
 * StreamingSettings.swift's playback-related subset (the storage/download-related subset lives
 * in [StorageSettingsStore] instead).
 */
@Singleton
class PlaybackSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val CONTINUOUS_PLAYBACK = booleanPreferencesKey("continuous_playback_enabled")
        val CONTINUOUS_MIX_MODE = stringPreferencesKey("continuous_mix_mode")
        val CONTINUOUS_MIX_LIMIT = intPreferencesKey("continuous_mix_limit")
        val GAPLESS_ENABLED = booleanPreferencesKey("gapless_enabled")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val REPLAY_GAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")
        val SCROBBLING_ENABLED = booleanPreferencesKey("scrobbling_enabled")
        val SCROBBLE_THRESHOLD = doublePreferencesKey("scrobble_threshold")
        val SONIC_SIMILAR_SONGS_ENABLED = booleanPreferencesKey("sonic_similar_songs_enabled")
        val SYNC_PLAY_QUEUE_ENABLED = booleanPreferencesKey("sync_play_queue_enabled")
        val STREAMING_MODE_ENABLED = booleanPreferencesKey("streaming_mode_enabled")
        val PREFER_LOSSLESS_ON_WIFI_ENABLED = booleanPreferencesKey("prefer_lossless_on_wifi_enabled")
        val KEEP_SCREEN_AWAKE_ENABLED = booleanPreferencesKey("keep_screen_awake_enabled")
        val SFW_MODE_ENABLED = booleanPreferencesKey("sfw_mode_enabled")
        val WIFI_FORMAT = stringPreferencesKey("wifi_format")
        val WIFI_BITRATE = stringPreferencesKey("wifi_bitrate")
        val ONLY_TRANSCODE_LOSSLESS_WIFI = booleanPreferencesKey("only_transcode_lossless_wifi")
        val CUSTOM_WIFI_FORMAT = stringPreferencesKey("custom_wifi_format")
        val CUSTOM_WIFI_BITRATE = stringPreferencesKey("custom_wifi_bitrate")
        val CELLULAR_FORMAT = stringPreferencesKey("cellular_format")
        val CELLULAR_BITRATE = stringPreferencesKey("cellular_bitrate")
        val ONLY_TRANSCODE_LOSSLESS_CELLULAR = booleanPreferencesKey("only_transcode_lossless_cellular")
        val CUSTOM_CELLULAR_FORMAT = stringPreferencesKey("custom_cellular_format")
        val CUSTOM_CELLULAR_BITRATE = stringPreferencesKey("custom_cellular_bitrate")
    }

    val settings: Flow<PlaybackSettings> = context.playbackSettingsDataStore.data.map { prefs ->
        PlaybackSettings(
            continuousPlaybackEnabled = prefs[Keys.CONTINUOUS_PLAYBACK] ?: true,
            continuousMixMode = prefs[Keys.CONTINUOUS_MIX_MODE]?.let { raw ->
                runCatching { ContinuousMixMode.valueOf(raw) }.getOrNull()
            } ?: ContinuousMixMode.SIMILAR,
            continuousMixLimit = prefs[Keys.CONTINUOUS_MIX_LIMIT] ?: 10,
            gaplessEnabled = prefs[Keys.GAPLESS_ENABLED] ?: false,
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
            crossfadeSeconds = prefs[Keys.CROSSFADE_SECONDS] ?: 5,
            replayGainEnabled = prefs[Keys.REPLAY_GAIN_ENABLED] ?: false,
            scrobblingEnabled = prefs[Keys.SCROBBLING_ENABLED] ?: true,
            scrobbleThreshold = prefs[Keys.SCROBBLE_THRESHOLD] ?: 0.2,
            sonicSimilarSongsEnabled = prefs[Keys.SONIC_SIMILAR_SONGS_ENABLED] ?: true,
            syncPlayQueueEnabled = prefs[Keys.SYNC_PLAY_QUEUE_ENABLED] ?: false,
            streamingModeEnabled = prefs[Keys.STREAMING_MODE_ENABLED] ?: false,
            preferLosslessOnWifiEnabled = prefs[Keys.PREFER_LOSSLESS_ON_WIFI_ENABLED] ?: false,
            keepScreenAwakeEnabled = prefs[Keys.KEEP_SCREEN_AWAKE_ENABLED] ?: false,
            sfwModeEnabled = prefs[Keys.SFW_MODE_ENABLED] ?: false,
            wifiFormat = prefs[Keys.WIFI_FORMAT]?.let { raw -> runCatching { StreamFormat.valueOf(raw) }.getOrNull() } ?: StreamFormat.RAW,
            wifiBitrate = prefs[Keys.WIFI_BITRATE]?.let { raw -> runCatching { StreamBitrate.valueOf(raw) }.getOrNull() } ?: StreamBitrate.ORIGINAL,
            onlyTranscodeLosslessWifi = prefs[Keys.ONLY_TRANSCODE_LOSSLESS_WIFI] ?: false,
            customWifiFormat = prefs[Keys.CUSTOM_WIFI_FORMAT] ?: "",
            customWifiBitrate = prefs[Keys.CUSTOM_WIFI_BITRATE] ?: "",
            cellularFormat = prefs[Keys.CELLULAR_FORMAT]?.let { raw -> runCatching { StreamFormat.valueOf(raw) }.getOrNull() } ?: StreamFormat.AAC,
            cellularBitrate = prefs[Keys.CELLULAR_BITRATE]?.let { raw -> runCatching { StreamBitrate.valueOf(raw) }.getOrNull() } ?: StreamBitrate.KBPS_192,
            onlyTranscodeLosslessCellular = prefs[Keys.ONLY_TRANSCODE_LOSSLESS_CELLULAR] ?: false,
            customCellularFormat = prefs[Keys.CUSTOM_CELLULAR_FORMAT] ?: "",
            customCellularBitrate = prefs[Keys.CUSTOM_CELLULAR_BITRATE] ?: ""
        )
    }

    suspend fun setContinuousPlaybackEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.CONTINUOUS_PLAYBACK] = enabled }
    }

    suspend fun setContinuousMixMode(mode: ContinuousMixMode) {
        context.playbackSettingsDataStore.edit { it[Keys.CONTINUOUS_MIX_MODE] = mode.name }
    }

    suspend fun setContinuousMixLimit(limit: Int) {
        context.playbackSettingsDataStore.edit { it[Keys.CONTINUOUS_MIX_LIMIT] = limit }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.GAPLESS_ENABLED] = enabled }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.CROSSFADE_ENABLED] = enabled }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.playbackSettingsDataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds }
    }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.REPLAY_GAIN_ENABLED] = enabled }
    }

    suspend fun setScrobblingEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.SCROBBLING_ENABLED] = enabled }
    }

    suspend fun setScrobbleThreshold(threshold: Double) {
        context.playbackSettingsDataStore.edit { it[Keys.SCROBBLE_THRESHOLD] = threshold }
    }

    suspend fun setSonicSimilarSongsEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.SONIC_SIMILAR_SONGS_ENABLED] = enabled }
    }

    suspend fun setSyncPlayQueueEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.SYNC_PLAY_QUEUE_ENABLED] = enabled }
    }

    suspend fun setStreamingModeEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.STREAMING_MODE_ENABLED] = enabled }
    }

    suspend fun setPreferLosslessOnWifiEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.PREFER_LOSSLESS_ON_WIFI_ENABLED] = enabled }
    }

    suspend fun setKeepScreenAwakeEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.KEEP_SCREEN_AWAKE_ENABLED] = enabled }
    }

    suspend fun setSfwModeEnabled(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.SFW_MODE_ENABLED] = enabled }
    }

    suspend fun setWifiFormat(format: StreamFormat) {
        context.playbackSettingsDataStore.edit { it[Keys.WIFI_FORMAT] = format.name }
    }

    suspend fun setWifiBitrate(bitrate: StreamBitrate) {
        context.playbackSettingsDataStore.edit { it[Keys.WIFI_BITRATE] = bitrate.name }
    }

    suspend fun setOnlyTranscodeLosslessWifi(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.ONLY_TRANSCODE_LOSSLESS_WIFI] = enabled }
    }

    suspend fun setCustomWifiFormat(value: String) {
        context.playbackSettingsDataStore.edit { it[Keys.CUSTOM_WIFI_FORMAT] = value }
    }

    suspend fun setCustomWifiBitrate(value: String) {
        context.playbackSettingsDataStore.edit { it[Keys.CUSTOM_WIFI_BITRATE] = value }
    }

    suspend fun setCellularFormat(format: StreamFormat) {
        context.playbackSettingsDataStore.edit { it[Keys.CELLULAR_FORMAT] = format.name }
    }

    suspend fun setCellularBitrate(bitrate: StreamBitrate) {
        context.playbackSettingsDataStore.edit { it[Keys.CELLULAR_BITRATE] = bitrate.name }
    }

    suspend fun setOnlyTranscodeLosslessCellular(enabled: Boolean) {
        context.playbackSettingsDataStore.edit { it[Keys.ONLY_TRANSCODE_LOSSLESS_CELLULAR] = enabled }
    }

    suspend fun setCustomCellularFormat(value: String) {
        context.playbackSettingsDataStore.edit { it[Keys.CUSTOM_CELLULAR_FORMAT] = value }
    }

    suspend fun setCustomCellularBitrate(value: String) {
        context.playbackSettingsDataStore.edit { it[Keys.CUSTOM_CELLULAR_BITRATE] = value }
    }
}
