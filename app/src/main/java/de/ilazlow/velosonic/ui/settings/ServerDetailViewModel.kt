package de.ilazlow.velosonic.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.ServerStats
import de.ilazlow.velosonic.data.ServerStatsRepository
import de.ilazlow.velosonic.data.admin.AdminSubsonicClient
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.dto.NowPlayingEntryDto
import de.ilazlow.velosonic.data.network.dto.ScanStatusDto
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.data.sync.SyncMode
import de.ilazlow.velosonic.data.sync.SyncNowWorker
import de.ilazlow.velosonic.data.sync.SyncState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SCAN_POLL_INTERVAL_MS = 2_000L
private const val NOW_PLAYING_POLL_INTERVAL_MS = 5_000L

@HiltViewModel
class ServerDetailViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val serverStatsRepository: ServerStatsRepository,
    private val syncEngine: SyncEngine,
    private val adminSubsonicClient: AdminSubsonicClient,
    private val serverConfigDao: ServerConfigDao,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val servers: StateFlow<List<ServerConfigEntity>> = serverRepository.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val syncState: StateFlow<SyncState> = syncEngine.state

    private val _stats = MutableStateFlow(ServerStats())
    val stats: StateFlow<ServerStats> = _stats.asStateFlow()

    private val _scanStatus = MutableStateFlow<ScanStatusDto?>(null)
    val scanStatus: StateFlow<ScanStatusDto?> = _scanStatus.asStateFlow()

    private val _nowPlayingEntries = MutableStateFlow<List<NowPlayingEntryDto>>(emptyList())
    val nowPlayingEntries: StateFlow<List<NowPlayingEntryDto>> = _nowPlayingEntries.asStateFlow()

    /** Wall-clock time of the last successful [nowPlayingEntries] fetch — lets the UI extrapolate
     *  a live-advancing position between polls instead of only updating every 5s (mirrors iOS's
     *  `nowPlayingFetchedAt` + `TimelineView`). */
    private val _nowPlayingFetchedAt = MutableStateFlow(0L)
    val nowPlayingFetchedAt: StateFlow<Long> = _nowPlayingFetchedAt.asStateFlow()

    private var scanPollJob: Job? = null
    private var nowPlayingPollJob: Job? = null

    fun loadStats(host: String) = viewModelScope.launch {
        _stats.value = serverStatsRepository.statsFor(host)
    }

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 100): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    fun resync(host: String) = SyncNowWorker.enqueue(context, host, SyncMode.FORCED_PARTIAL)

    fun fullResync(host: String) = SyncNowWorker.enqueue(context, host, SyncMode.FULL_RESYNC)

    fun removeServer(config: ServerConfigEntity) = viewModelScope.launch {
        serverRepository.removeServer(config)
    }

    /** Mirrors ServerDetailView.swift's `onAppear` — re-checks the account's actual admin status
     *  (a plain `getUser` call; [ServerConfigEntity.isAdmin] otherwise only ever reflects whatever
     *  was true, or guessed false, at add-time) and persists it if it changed, then — only for a
     *  confirmed admin — starts the two admin-only polls. Safe to call every time the screen
     *  appears; [viewModelScope] being cleared when the screen is left stops both polls the same
     *  way iOS's `onDisappear` cancels its poll tasks. */
    fun onAdminScreenShown(host: String) = viewModelScope.launch {
        val config = serverConfigDao.getByHost(host) ?: return@launch
        val admin = adminSubsonicClient.fetchIsAdmin(config)
        if (admin != null && admin != config.isAdmin) {
            serverConfigDao.update(config.copy(isAdmin = admin))
        }
        if (admin == true) {
            refreshScanStatus(config)
            startNowPlayingPolling(config)
        }
    }

    private suspend fun refreshScanStatus(config: ServerConfigEntity) {
        val status = adminSubsonicClient.getScanStatus(config)
        _scanStatus.value = status
        if (status?.scanning == true) startScanPolling(config)
    }

    fun triggerScan(host: String, fullScan: Boolean) = viewModelScope.launch {
        val config = serverConfigDao.getByHost(host) ?: return@launch
        val status = adminSubsonicClient.startScan(config, fullScan)
        _scanStatus.value = status
        if (status?.scanning == true) startScanPolling(config)
    }

    private fun startScanPolling(config: ServerConfigEntity) {
        scanPollJob?.cancel()
        scanPollJob = viewModelScope.launch {
            while (isActive) {
                delay(SCAN_POLL_INTERVAL_MS)
                val status = adminSubsonicClient.getScanStatus(config)
                _scanStatus.value = status
                if (status?.scanning != true) break
            }
        }
    }

    private fun startNowPlayingPolling(config: ServerConfigEntity) {
        nowPlayingPollJob?.cancel()
        nowPlayingPollJob = viewModelScope.launch {
            while (isActive) {
                _nowPlayingEntries.value = adminSubsonicClient.getNowPlaying(config)
                    .sortedBy { it.username.lowercase() }
                _nowPlayingFetchedAt.value = System.currentTimeMillis()
                delay(NOW_PLAYING_POLL_INTERVAL_MS)
            }
        }
    }
}
