package de.ilazlow.velosonic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.HomeSections
import de.ilazlow.velosonic.data.HomeSectionsRepository
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import de.ilazlow.velosonic.data.datastore.ServerOrderStore
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NetworkAvailability
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.domain.supportsOpenSubsonicExtensions
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val homeSectionsRepository: HomeSectionsRepository,
    private val serverRepository: ServerRepository,
    private val serverOrderStore: ServerOrderStore,
    private val downloadRepository: DownloadRepository,
    private val syncEngine: SyncEngine,
    private val playbackController: PlaybackController,
    private val playbackSubsonicClient: PlaybackSubsonicClient,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    networkAvailability: NetworkAvailability,
    appearanceSettingsStore: AppearanceSettingsStore
) : ViewModel() {

    /** Mirrors iOS's two-phase Home load: the local, Room-derived query shows something instantly
     *  (and works fully offline), superseded the moment the live per-server fetch resolves — see
     *  [HomeSectionsRepository]'s doc comment for why that live fetch, not a client-side re-sort
     *  of locally cached fields, is what actually determines section order. */
    private val _liveSections = MutableStateFlow<HomeSections?>(null)

    val sections: StateFlow<HomeSections> = combine(
        libraryRepository.observeHomeSections(),
        _liveSections
    ) { local, live -> live ?: local }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSections())

    init {
        viewModelScope.launch { refreshLiveSections() }
        // Re-fetch the moment the network becomes reachable again — either Offline Mode gets
        // switched off or real connectivity comes back — rather than leaving Home stuck showing
        // stale/local-only sections until the user happens to pull-to-refresh. `drop(1)` skips
        // the initial combined value so this doesn't double-fetch alongside the launch above.
        viewModelScope.launch {
            networkAvailability.canReachNetwork.drop(1).distinctUntilChanged().filter { it }.collect { refreshLiveSections() }
        }
    }

    private suspend fun refreshLiveSections() {
        val visibleHosts = libraryRepository.observeVisibleHosts().first()
        if (visibleHosts.isEmpty()) return
        val fetched = homeSectionsRepository.fetchHomeSections(visibleHosts)
        // Every per-server fetch inside fetchHomeSections swallows failures into an empty list
        // (offline mode, no connectivity, a server being down) rather than throwing — so an
        // all-empty result here means "the live fetch didn't actually work," not "the library is
        // empty." Assigning it anyway would permanently blank out the local, Room-derived
        // sections (`live ?: local` never falls back to local again once live is non-null),
        // defeating the whole point of Home working offline. Leaving _liveSections untouched lets
        // local keep showing until a live fetch actually produces something.
        if (fetched == HomeSections()) return
        _liveSections.value = fetched
    }

    val recentPlaylists: StateFlow<List<PlaylistEntity>> = libraryRepository.observeRecentlyPlayedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val animatedAlbumGridEnabled: StateFlow<Boolean> = appearanceSettingsStore.settings
        .map { it.animatedAlbumGridEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val servers: StateFlow<List<ServerConfigEntity>> = combine(
        serverRepository.observeServers(), serverOrderStore.order
    ) { configs, order -> serverOrderStore.applyOrder(configs, order) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    val activeDownloadCount: StateFlow<Int> = downloadRepository.activity
        .map { activity -> activity.count { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isFetchingQueue = MutableStateFlow(false)
    val isFetchingQueue: StateFlow<Boolean> = _isFetchingQueue.asStateFlow()

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 300): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    fun setServerVisible(config: ServerConfigEntity, visible: Boolean) {
        viewModelScope.launch { serverRepository.setServerVisible(config, visible) }
    }

    /** Pull-to-refresh — mirrors iOS's `.refreshable` on Home: a partial *library* sync (albums/
     *  artists/tracks/playlists) across every configured server. Not to be confused with
     *  [loadQueueFromServer], a completely different iOS feature that happens to share a toolbar
     *  slot there (`arrow.clockwise.circle.fill` bound to `HomeViewModel.refreshQueue`). */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncEngine.performPartialSyncAllServers(forced = true)
                refreshLiveSections()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Mirrors iOS's Home toolbar action (`HomeViewModel.refreshQueue` calling
     *  `AudioPlayerManager.loadQueueFromServer`) — pulls the OpenSubsonic-saved play queue/
     *  position (from this device or another client) onto this one, paused. iOS hardcodes this to
     *  `configs.first` (there's no per-server toolbar action there either), which silently misses
     *  the saved queue whenever the "first" server happens to be one with nothing saved — visible
     *  with 2+ servers configured. Instead of guessing, this checks every OpenSubsonic-capable
     *  server's saved queue and picks whichever was changed most recently. */
    fun loadQueueFromServer() {
        if (_isFetchingQueue.value) return
        viewModelScope.launch {
            _isFetchingQueue.value = true
            try {
                val candidates = servers.value.filter { it.supportsOpenSubsonicExtensions }
                var bestHost: String? = null
                var bestChanged: String? = null
                for (config in candidates) {
                    val node = playbackSubsonicClient.getPlayQueue(config) ?: continue
                    if (node.entry.isNullOrEmpty()) continue
                    if (bestHost == null || (node.changed != null && (bestChanged == null || node.changed > bestChanged))) {
                        bestHost = config.host
                        bestChanged = node.changed
                    }
                }
                bestHost?.let { playbackController.loadQueueFromServer(it) }
            } finally {
                _isFetchingQueue.value = false
            }
        }
    }
}
