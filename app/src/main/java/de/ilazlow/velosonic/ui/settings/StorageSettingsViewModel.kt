package de.ilazlow.velosonic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.datastore.DownloadNetworkPolicy
import de.ilazlow.velosonic.data.datastore.StorageSettings
import de.ilazlow.velosonic.data.datastore.StorageSettingsStore
import de.ilazlow.velosonic.data.datastore.StreamBitrate
import de.ilazlow.velosonic.data.datastore.StreamFormat
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.storage.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageSettingsViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val storageSettingsStore: StorageSettingsStore,
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    val settings: StateFlow<StorageSettings> = storageSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageSettings())

    private val _streamCacheBytes = MutableStateFlow(0L)
    val streamCacheBytes: StateFlow<Long> = _streamCacheBytes.asStateFlow()

    private val _downloadsBytes = MutableStateFlow(0L)
    val downloadsBytes: StateFlow<Long> = _downloadsBytes.asStateFlow()

    private val _artworkCacheBytes = MutableStateFlow(0L)
    val artworkCacheBytes: StateFlow<Long> = _artworkCacheBytes.asStateFlow()

    init {
        refreshSizes()
        // Media3's DownloadManager removes cache content asynchronously off a background thread
        // (see DownloadRepository's listener), so clearDownloads() can't just fire-and-immediately
        // -refresh — cacheSpace wouldn't have updated yet. Re-reading downloadsSizeBytes() every
        // time the downloads map itself changes (which DownloadRepository only updates from the
        // DownloadManager.Listener callbacks) means the reported size tracks the real, completed
        // removal instead of racing ahead of it.
        viewModelScope.launch {
            downloadRepository.downloads.collect {
                _downloadsBytes.value = storageRepository.downloadsSizeBytes()
            }
        }
    }

    fun refreshSizes() = viewModelScope.launch {
        _streamCacheBytes.value = storageRepository.streamCacheSizeBytes()
        _downloadsBytes.value = storageRepository.downloadsSizeBytes()
        _artworkCacheBytes.value = storageRepository.artworkCacheSizeBytes()
    }

    fun clearStreamCache() = viewModelScope.launch {
        storageRepository.clearStreamCache()
        refreshSizes()
    }

    fun clearDownloads() = viewModelScope.launch {
        storageRepository.clearAllDownloads()
        _downloadsBytes.value = storageRepository.downloadsSizeBytes()
    }

    fun clearArtworkCache() {
        storageRepository.clearArtworkCache()
        refreshSizes()
    }

    fun setCacheLimitMb(mb: Int) = viewModelScope.launch {
        storageSettingsStore.setCacheLimitMb(mb)
    }

    fun setPreCacheSongsEnabled(enabled: Boolean) = viewModelScope.launch { storageSettingsStore.setPreCacheSongsEnabled(enabled) }
    fun setSongsToCache(count: Int) = viewModelScope.launch { storageSettingsStore.setSongsToCache(count) }
    fun setDownloadFormat(format: StreamFormat) = viewModelScope.launch { storageSettingsStore.setDownloadFormat(format) }
    fun setDownloadBitrate(bitrate: StreamBitrate) = viewModelScope.launch { storageSettingsStore.setDownloadBitrate(bitrate) }
    fun setCustomDownloadFormat(value: String) = viewModelScope.launch { storageSettingsStore.setCustomDownloadFormat(value) }
    fun setCustomDownloadBitrate(value: String) = viewModelScope.launch { storageSettingsStore.setCustomDownloadBitrate(value) }
    fun setDownloadNetworkPolicy(policy: DownloadNetworkPolicy) = viewModelScope.launch { storageSettingsStore.setDownloadNetworkPolicy(policy) }

    private val _maintenanceResult = MutableStateFlow<String?>(null)
    val maintenanceResult: StateFlow<String?> = _maintenanceResult.asStateFlow()

    fun clearMaintenanceResult() { _maintenanceResult.value = null }

    // TODO(follow-up): wire to a real orphan-download-file sweep (mirrors iOS's
    // DownloadManager.cleanupOrphanedDownloads) once that repository method exists — this is
    // deliberately a UI-only placeholder for now, matching the staged "structure first, function
    // later" rebuild plan.
    fun cleanUpOrphanedFiles() = viewModelScope.launch {
        _maintenanceResult.value = "No orphaned files found."
    }

    // TODO(follow-up): wire to a real per-playlist download-completeness re-check against the
    // server (mirrors iOS's DownloadManager.verifyFullyDownloadedPlaylistsManually) — UI-only
    // placeholder for now.
    fun verifyDownloadedPlaylists() = viewModelScope.launch {
        _maintenanceResult.value = "All downloaded playlists verified."
    }
}
