package de.ilazlow.velosonic.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.storageSettingsDataStore by preferencesDataStore(name = "storage_settings")

private const val DEFAULT_CACHE_LIMIT_MB = 500

/** Mirrors StreamingSettings' storage/download-related subset (playback-related settings live
 *  in [PlaybackSettingsStore] instead). [cacheLimitMb] is a soft cap (100-10000MB) on the
 *  evictable stream cache, distinct from permanent downloads which have no size limit. */
data class StorageSettings(
    val cacheLimitMb: Int = DEFAULT_CACHE_LIMIT_MB,
    val preCacheSongsEnabled: Boolean = false,
    val songsToCache: Int = 3,
    val downloadFormat: StreamFormat = StreamFormat.AAC,
    val downloadBitrate: StreamBitrate = StreamBitrate.KBPS_192,
    val customDownloadFormat: String = "",
    val customDownloadBitrate: String = "",
    val downloadNetworkPolicy: DownloadNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY
)

@Singleton
class StorageSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        val PRE_CACHE_SONGS_ENABLED = booleanPreferencesKey("pre_cache_songs_enabled")
        val SONGS_TO_CACHE = intPreferencesKey("songs_to_cache")
        val DOWNLOAD_FORMAT = stringPreferencesKey("download_format")
        val DOWNLOAD_BITRATE = stringPreferencesKey("download_bitrate")
        val CUSTOM_DOWNLOAD_FORMAT = stringPreferencesKey("custom_download_format")
        val CUSTOM_DOWNLOAD_BITRATE = stringPreferencesKey("custom_download_bitrate")
        val DOWNLOAD_NETWORK_POLICY = stringPreferencesKey("download_network_policy")
    }

    val settings: Flow<StorageSettings> = context.storageSettingsDataStore.data.map { prefs ->
        StorageSettings(
            cacheLimitMb = prefs[Keys.CACHE_LIMIT_MB] ?: DEFAULT_CACHE_LIMIT_MB,
            preCacheSongsEnabled = prefs[Keys.PRE_CACHE_SONGS_ENABLED] ?: false,
            songsToCache = prefs[Keys.SONGS_TO_CACHE] ?: 3,
            downloadFormat = prefs[Keys.DOWNLOAD_FORMAT]?.let { raw -> runCatching { StreamFormat.valueOf(raw) }.getOrNull() } ?: StreamFormat.AAC,
            downloadBitrate = prefs[Keys.DOWNLOAD_BITRATE]?.let { raw -> runCatching { StreamBitrate.valueOf(raw) }.getOrNull() } ?: StreamBitrate.KBPS_192,
            customDownloadFormat = prefs[Keys.CUSTOM_DOWNLOAD_FORMAT] ?: "",
            customDownloadBitrate = prefs[Keys.CUSTOM_DOWNLOAD_BITRATE] ?: "",
            downloadNetworkPolicy = prefs[Keys.DOWNLOAD_NETWORK_POLICY]?.let { raw -> runCatching { DownloadNetworkPolicy.valueOf(raw) }.getOrNull() } ?: DownloadNetworkPolicy.WIFI_ONLY
        )
    }

    suspend fun setCacheLimitMb(mb: Int) {
        context.storageSettingsDataStore.edit { it[Keys.CACHE_LIMIT_MB] = mb.coerceIn(100, 10000) }
    }

    suspend fun setPreCacheSongsEnabled(enabled: Boolean) {
        context.storageSettingsDataStore.edit { it[Keys.PRE_CACHE_SONGS_ENABLED] = enabled }
    }

    suspend fun setSongsToCache(count: Int) {
        context.storageSettingsDataStore.edit { it[Keys.SONGS_TO_CACHE] = count.coerceIn(1, 20) }
    }

    suspend fun setDownloadFormat(format: StreamFormat) {
        context.storageSettingsDataStore.edit { it[Keys.DOWNLOAD_FORMAT] = format.name }
    }

    suspend fun setDownloadBitrate(bitrate: StreamBitrate) {
        context.storageSettingsDataStore.edit { it[Keys.DOWNLOAD_BITRATE] = bitrate.name }
    }

    suspend fun setCustomDownloadFormat(value: String) {
        context.storageSettingsDataStore.edit { it[Keys.CUSTOM_DOWNLOAD_FORMAT] = value }
    }

    suspend fun setCustomDownloadBitrate(value: String) {
        context.storageSettingsDataStore.edit { it[Keys.CUSTOM_DOWNLOAD_BITRATE] = value }
    }

    suspend fun setDownloadNetworkPolicy(policy: DownloadNetworkPolicy) {
        context.storageSettingsDataStore.edit { it[Keys.DOWNLOAD_NETWORK_POLICY] = policy.name }
    }
}
