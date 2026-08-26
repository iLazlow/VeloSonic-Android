package de.ilazlow.velosonic.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.ServerStats
import de.ilazlow.velosonic.data.ServerStatsRepository
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.data.sync.SyncState
import de.ilazlow.velosonic.data.datastore.AppearanceSettings
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import de.ilazlow.velosonic.data.datastore.DEFAULT_ANIMATED_ARTWORK_API_URL
import de.ilazlow.velosonic.data.datastore.BuiltInEqPresets
import de.ilazlow.velosonic.data.datastore.EqPreset
import de.ilazlow.velosonic.data.datastore.EqSettings
import de.ilazlow.velosonic.data.datastore.EqSettingsStore
import de.ilazlow.velosonic.data.datastore.LyricsSettings
import de.ilazlow.velosonic.data.datastore.LyricsSettingsStore
import de.ilazlow.velosonic.data.datastore.LyricsSource
import de.ilazlow.velosonic.data.datastore.OfflineModeStore
import de.ilazlow.velosonic.data.datastore.PlaybackSettings
import de.ilazlow.velosonic.data.datastore.ServerOrderStore
import de.ilazlow.velosonic.data.datastore.SharingSettings
import de.ilazlow.velosonic.data.datastore.SharingSettingsStore
import de.ilazlow.velosonic.data.datastore.ShareLinkType
import de.ilazlow.velosonic.data.datastore.PlaybackSettingsStore
import de.ilazlow.velosonic.data.datastore.StreamBitrate
import de.ilazlow.velosonic.data.datastore.StreamFormat
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.sync.SyncMode
import de.ilazlow.velosonic.data.sync.SyncNowWorker
import de.ilazlow.velosonic.playback.ContinuousMixMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Settings screen — every setter here just forwards to a store/repository method that
 * already existed before this screen did (PlaybackSettingsStore's knobs, ServerRepository's
 * server CRUD); this ViewModel's only job is exposing them as StateFlows a Composable can read
 * and giving each one a UI action to call.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playbackSettingsStore: PlaybackSettingsStore,
    private val appearanceSettingsStore: AppearanceSettingsStore,
    private val lyricsSettingsStore: LyricsSettingsStore,
    private val eqSettingsStore: EqSettingsStore,
    private val offlineModeStore: OfflineModeStore,
    private val sharingSettingsStore: SharingSettingsStore,
    private val serverRepository: ServerRepository,
    private val serverStatsRepository: ServerStatsRepository,
    private val serverOrderStore: ServerOrderStore,
    private val syncEngine: SyncEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val syncState: StateFlow<SyncState> = syncEngine.state
    val playbackSettings: StateFlow<PlaybackSettings> = playbackSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackSettings())

    val offlineModeEnabled: StateFlow<Boolean> = offlineModeStore.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sharingSettings: StateFlow<SharingSettings> = sharingSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharingSettings())

    val appearanceSettings: StateFlow<AppearanceSettings> = appearanceSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceSettings())

    val lyricsSettings: StateFlow<LyricsSettings> = lyricsSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsSettings())

    val eqSettings: StateFlow<EqSettings> = eqSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqSettings())

    val servers: StateFlow<List<ServerConfigEntity>> = combine(
        serverRepository.observeServers(), serverOrderStore.order
    ) { configs, order -> serverOrderStore.applyOrder(configs, order) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setServerOrder(hosts: List<String>) = viewModelScope.launch {
        serverOrderStore.setOrder(hosts)
    }

    fun setContinuousPlaybackEnabled(enabled: Boolean) = viewModelScope.launch {
        playbackSettingsStore.setContinuousPlaybackEnabled(enabled)
    }

    fun setContinuousMixMode(mode: ContinuousMixMode) = viewModelScope.launch {
        playbackSettingsStore.setContinuousMixMode(mode)
    }

    fun setSonicSimilarSongsEnabled(enabled: Boolean) = viewModelScope.launch {
        playbackSettingsStore.setSonicSimilarSongsEnabled(enabled)
    }

    fun setCrossfadeEnabled(enabled: Boolean) = viewModelScope.launch {
        playbackSettingsStore.setCrossfadeEnabled(enabled)
    }

    fun setCrossfadeSeconds(seconds: Int) = viewModelScope.launch {
        playbackSettingsStore.setCrossfadeSeconds(seconds)
    }

    fun setReplayGainEnabled(enabled: Boolean) = viewModelScope.launch {
        playbackSettingsStore.setReplayGainEnabled(enabled)
    }

    fun setScrobblingEnabled(enabled: Boolean) = viewModelScope.launch {
        playbackSettingsStore.setScrobblingEnabled(enabled)
    }

    fun setScrobbleThreshold(threshold: Double) = viewModelScope.launch {
        playbackSettingsStore.setScrobbleThreshold(threshold)
    }

    fun setContinuousMixLimit(limit: Int) = viewModelScope.launch { playbackSettingsStore.setContinuousMixLimit(limit) }

    fun setGaplessEnabled(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setGaplessEnabled(enabled) }

    fun setSyncPlayQueueEnabled(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setSyncPlayQueueEnabled(enabled) }

    fun setStreamingModeEnabled(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setStreamingModeEnabled(enabled) }

    fun setPreferLosslessOnWifiEnabled(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setPreferLosslessOnWifiEnabled(enabled) }

    fun setKeepScreenAwakeEnabled(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setKeepScreenAwakeEnabled(enabled) }

    fun setSfwModeEnabled(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setSfwModeEnabled(enabled) }

    fun setWifiFormat(format: StreamFormat) = viewModelScope.launch { playbackSettingsStore.setWifiFormat(format) }
    fun setWifiBitrate(bitrate: StreamBitrate) = viewModelScope.launch { playbackSettingsStore.setWifiBitrate(bitrate) }
    fun setOnlyTranscodeLosslessWifi(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setOnlyTranscodeLosslessWifi(enabled) }
    fun setCustomWifiFormat(value: String) = viewModelScope.launch { playbackSettingsStore.setCustomWifiFormat(value) }
    fun setCustomWifiBitrate(value: String) = viewModelScope.launch { playbackSettingsStore.setCustomWifiBitrate(value) }

    fun setCellularFormat(format: StreamFormat) = viewModelScope.launch { playbackSettingsStore.setCellularFormat(format) }
    fun setCellularBitrate(bitrate: StreamBitrate) = viewModelScope.launch { playbackSettingsStore.setCellularBitrate(bitrate) }
    fun setOnlyTranscodeLosslessCellular(enabled: Boolean) = viewModelScope.launch { playbackSettingsStore.setOnlyTranscodeLosslessCellular(enabled) }
    fun setCustomCellularFormat(value: String) = viewModelScope.launch { playbackSettingsStore.setCustomCellularFormat(value) }
    fun setCustomCellularBitrate(value: String) = viewModelScope.launch { playbackSettingsStore.setCustomCellularBitrate(value) }

    fun setOfflineModeEnabled(enabled: Boolean) = viewModelScope.launch { offlineModeStore.setEnabled(enabled) }

    fun setShareLinkType(type: ShareLinkType) = viewModelScope.launch { sharingSettingsStore.setLinkType(type) }

    fun setDynamicColorEnabled(enabled: Boolean) = viewModelScope.launch {
        appearanceSettingsStore.setDynamicColorEnabled(enabled)
    }

    fun setAnimatedArtworksEnabled(enabled: Boolean) = viewModelScope.launch {
        appearanceSettingsStore.setAnimatedArtworksEnabled(enabled)
    }

    fun setAnimatedAlbumGridEnabled(enabled: Boolean) = viewModelScope.launch {
        appearanceSettingsStore.setAnimatedAlbumGridEnabled(enabled)
    }

    fun setApplyMissingAnimatedArtworks(enabled: Boolean) = viewModelScope.launch {
        appearanceSettingsStore.setApplyMissingAnimatedArtworks(enabled)
    }

    fun setAnimatedArtworkApiUrl(url: String) = viewModelScope.launch {
        appearanceSettingsStore.setAnimatedArtworkApiUrl(url)
    }

    fun resetAnimatedArtworkApiUrl() = viewModelScope.launch {
        appearanceSettingsStore.setAnimatedArtworkApiUrl(DEFAULT_ANIMATED_ARTWORK_API_URL)
    }

    fun setServerVisible(config: ServerConfigEntity, visible: Boolean) = viewModelScope.launch {
        serverRepository.setServerVisible(config, visible)
    }

    fun removeServer(config: ServerConfigEntity) = viewModelScope.launch {
        serverRepository.removeServer(config)
    }

    suspend fun statsFor(host: String): ServerStats = serverStatsRepository.statsFor(host)

    fun resync(host: String) = SyncNowWorker.enqueue(context, host, SyncMode.FORCED_PARTIAL)

    fun fullResync(host: String) = SyncNowWorker.enqueue(context, host, SyncMode.FULL_RESYNC)

    /** Every server, one at a time — see [SyncNowWorker.enqueueSequential] for why this isn't
     *  just `hosts.forEach { resync(it) }`. */
    fun resyncAll(hosts: List<String>) = SyncNowWorker.enqueueSequential(context, hosts, SyncMode.FORCED_PARTIAL)

    fun fullResyncAll(hosts: List<String>) = SyncNowWorker.enqueueSequential(context, hosts, SyncMode.FULL_RESYNC)

    fun setLyricsSource(source: LyricsSource) = viewModelScope.launch {
        lyricsSettingsStore.setSource(source)
    }

    fun setRadiantLyricsEnabled(enabled: Boolean) = viewModelScope.launch {
        lyricsSettingsStore.setRadiantLyricsEnabled(enabled)
    }

    fun setRadiantLyricsRomanization(enabled: Boolean) = viewModelScope.launch {
        lyricsSettingsStore.setRadiantLyricsRomanization(enabled)
    }

    fun setRadiantLyricsSparklesEnabled(enabled: Boolean) = viewModelScope.launch {
        lyricsSettingsStore.setRadiantLyricsSparklesEnabled(enabled)
    }

    fun setRadiantLyricsAiSynthesizeEnabled(enabled: Boolean) = viewModelScope.launch {
        lyricsSettingsStore.setRadiantLyricsAiSynthesizeEnabled(enabled)
    }

    fun setLyricsAutoSyncEnabled(enabled: Boolean) = viewModelScope.launch {
        lyricsSettingsStore.setLyricsAutoSyncEnabled(enabled)
    }

    fun setEqEnabled(enabled: Boolean) = viewModelScope.launch { eqSettingsStore.setEnabled(enabled) }

    fun setEqGain(band: Int, value: Float) = viewModelScope.launch { eqSettingsStore.setGain(band, value) }

    fun applyEqPreset(preset: EqPreset) = viewModelScope.launch { eqSettingsStore.applyPreset(preset) }

    fun resetEqToFlat() = viewModelScope.launch { eqSettingsStore.resetToFlat() }

    fun saveCurrentEqAsPreset(name: String) = viewModelScope.launch { eqSettingsStore.saveCurrentAsPreset(name) }

    fun updateEqPreset(preset: EqPreset) = viewModelScope.launch { eqSettingsStore.updatePreset(preset) }

    fun deleteEqPreset(preset: EqPreset) = viewModelScope.launch { eqSettingsStore.deletePreset(preset) }

    fun resetEqPresetToDefault(preset: EqPreset) = viewModelScope.launch {
        val original = BuiltInEqPresets.presets.firstOrNull { it.builtInKey == preset.builtInKey } ?: return@launch
        eqSettingsStore.updatePreset(preset.copy(name = original.name, gains = original.gains))
    }

    /** Mirrors iOS's Logout: removes every configured server (credentials + cached library
     *  data for each, see ServerRepository.removeServer) — AppRootViewModel's route reactively
     *  falls back to the Login screen once the server table is empty, no navigation call needed
     *  here. */
    fun logout() = viewModelScope.launch {
        serverRepository.observeServers().first().forEach { serverRepository.removeServer(it) }
    }
}
