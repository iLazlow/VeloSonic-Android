package de.ilazlow.velosonic.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.datastore.RecentSearchesStore
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NetworkAvailability
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.search.SearchSubsonicClient
import de.ilazlow.velosonic.data.sync.toEntity
import de.ilazlow.velosonic.data.sync.toLightweightEntity
import de.ilazlow.velosonic.data.sync.toStandaloneEntity
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter { TOP_HITS, ARTISTS, ALBUMS, SONGS, RADIO }

data class SearchUiState(
    val artists: List<ArtistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val tracks: List<TrackEntity> = emptyList(),
    val radioStations: List<RadioStationEntity> = emptyList(),
    val filter: SearchFilter = SearchFilter.TOP_HITS,
    val recentSearches: List<String> = emptyList(),
    val nowPlaying: NowPlaying = NowPlaying(),
    val downloads: Map<String, Download> = emptyMap(),
    val canReachNetwork: Boolean = true,
    val isSearching: Boolean = false
) {
    val hasResults: Boolean get() = artists.isNotEmpty() || albums.isNotEmpty() || tracks.isNotEmpty() || radioStations.isNotEmpty()
}

private data class RawResults(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val tracks: List<TrackEntity>,
    val radioStations: List<RadioStationEntity>,
    val isSearching: Boolean = false
) {
    companion object {
        val EMPTY = RawResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

private data class LocalLibrarySnapshot(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val tracks: List<TrackEntity>,
    val radioStations: List<RadioStationEntity>
) {
    private val artistsById by lazy(LazyThreadSafetyMode.NONE) { artists.associateBy { it.id } }
    private val albumsById by lazy(LazyThreadSafetyMode.NONE) { albums.associateBy { it.id } }
    private val tracksById by lazy(LazyThreadSafetyMode.NONE) { tracks.associateBy { it.id } }

    /** Same filter predicates as the pre-live-search local-only implementation, scoped to one
     *  host — used both as the initial "something visible immediately" state and as the
     *  permanent fallback for any host whose live `search3` call failed. */
    fun filterFor(host: String, q: String): Triple<List<ArtistEntity>, List<AlbumEntity>, List<TrackEntity>> = Triple(
        artists.filter { it.serverHost == host && it.name.contains(q, ignoreCase = true) },
        albums.filter { it.serverHost == host && (it.name.contains(q, ignoreCase = true) || it.artistName.contains(q, ignoreCase = true)) },
        tracks.filter { it.serverHost == host && (it.title.contains(q, ignoreCase = true) || it.artistName.contains(q, ignoreCase = true)) }
    )

    /** [HostSearchOutcome.Live] entities are freshly mapped from `search3` and aren't backed by
     *  Room, so starring/downloading one wouldn't visibly update (the row isn't part of any
     *  reactive Room flow) and would miss the richer sync-merged fields (rating, play count, ...).
     *  Swapping in the already-synced Room row by id whenever one exists — same "prefer the synced
     *  copy" guidance as [de.ilazlow.velosonic.data.sync.toStandaloneEntity]'s doc comment — fixes
     *  both; an item search3 found that isn't synced yet keeps its network-derived version. */
    fun preferLocal(artist: ArtistEntity): ArtistEntity = artistsById[artist.id] ?: artist
    fun preferLocal(album: AlbumEntity): AlbumEntity = albumsById[album.id] ?: album
    fun preferLocal(track: TrackEntity): TrackEntity = tracksById[track.id] ?: track
}

private sealed interface HostSearchOutcome {
    data class Live(val artists: List<ArtistEntity>, val albums: List<AlbumEntity>, val tracks: List<TrackEntity>) : HostSearchOutcome
    /** `search3` failed for this host (offline, timeout, server error) or hasn't resolved yet —
     *  either way the caller falls back to filtering that host's already-synced local library. */
    data object Unavailable : HostSearchOutcome
}

private data class NetworkSearchOutcome(
    val query: String,
    val hosts: List<String>,
    val perHost: Map<String, HostSearchOutcome>,
    val isSearching: Boolean
) {
    companion object {
        val IDLE = NetworkSearchOutcome("", emptyList(), emptyMap(), isSearching = false)
    }
}

/**
 * Search hits every visible server's `search3` live, same as iOS's on-device search UX goal but
 * a deliberate deviation from `SearchViewModel.swift`'s actual implementation (which is purely
 * local) — requested explicitly since a local-only search misses anything not yet synced. The
 * already-synced Room library (same [LibraryRepository] every other list screen reads from) is
 * kept as a **fallback**, per-host: shown immediately while that host's live query is in flight
 * (avoids a blank flash) and shown permanently if that host's `search3` call fails (offline,
 * timeout, server error) — see [NetworkSearchOutcome]/[HostSearchOutcome]. Radio stations have no
 * live-search endpoint in Subsonic, so they stay local-filtered unconditionally, same as before.
 * 300ms debounce on the query before firing (matches iOS's `Task.sleep(300ms)` local-filter
 * debounce), but clearing the query back to blank clears results immediately rather than waiting
 * out the debounce — see [effectiveResults]'s doc comment.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController,
    private val downloadRepository: DownloadRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val recentSearchesStore: RecentSearchesStore,
    private val playbackSubsonicClient: PlaybackSubsonicClient,
    private val searchSubsonicClient: SearchSubsonicClient,
    private val networkAvailability: NetworkAvailability,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.TOP_HITS)

    val serverNames: StateFlow<Map<String, String>> = serverRepository.observeServers()
        .map { configs -> if (configs.size > 1) configs.associate { it.host to it.name.ifBlank { it.host } } else emptyMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val debouncedQuery = _query.debounce(300).distinctUntilChanged()

    private val localLibrary: Flow<LocalLibrarySnapshot> = combine(
        libraryRepository.observeArtists(),
        libraryRepository.observeAlbums(),
        libraryRepository.observeTracks(),
        libraryRepository.observeRadioStations()
    ) { artists, albums, tracks, radio -> LocalLibrarySnapshot(artists, albums, tracks, radio) }

    private suspend fun searchHost(config: ServerConfigEntity, query: String): HostSearchOutcome {
        val result = searchSubsonicClient.search3(config, query) ?: return HostSearchOutcome.Unavailable
        return HostSearchOutcome.Live(
            artists = result.artist.orEmpty().map { it.toEntity(config.host) },
            albums = result.album.orEmpty().map { it.toLightweightEntity(config.host) },
            tracks = result.song.orEmpty().map { it.toStandaloneEntity(config.host) }
        )
    }

    /** Fires one `search3` per visible server (same "servers included in combined views" scope
     *  every other library screen uses — [LibraryRepository.observeVisibleHosts]) on each new
     *  debounced query, cancelling any in-flight batch from the previous query via
     *  `flatMapLatest`. Skips the network entirely when [NetworkAvailability.canReachNetwork] is
     *  already false — no point racing timeouts when we already know we're offline, straight to
     *  the local fallback instead. */
    private val networkSearch: Flow<NetworkSearchOutcome> = combine(
        debouncedQuery,
        libraryRepository.observeVisibleHosts()
    ) { q, hosts -> q to hosts }
        .distinctUntilChanged()
        .flatMapLatest { (q, hosts) ->
            if (q.isBlank() || hosts.isEmpty() || !networkAvailability.canReachNetwork.value) {
                flowOf(NetworkSearchOutcome.IDLE.copy(query = q, hosts = hosts))
            } else {
                flow {
                    emit(NetworkSearchOutcome(q, hosts, emptyMap(), isSearching = true))
                    val perHost = coroutineScope {
                        hosts.map { host ->
                            async {
                                val config = coverArtUrlResolver.configFor(host)
                                host to if (config != null) searchHost(config, q) else HostSearchOutcome.Unavailable
                            }
                        }.awaitAll().toMap()
                    }
                    emit(NetworkSearchOutcome(q, hosts, perHost, isSearching = false))
                }
            }
        }

    private val debouncedResults: Flow<RawResults> = combine(networkSearch, localLibrary) { network, local ->
        if (network.query.isBlank()) return@combine RawResults.EMPTY

        val perHostArtists = mutableListOf<List<ArtistEntity>>()
        val perHostAlbums = mutableListOf<List<AlbumEntity>>()
        val perHostTracks = mutableListOf<List<TrackEntity>>()
        network.hosts.forEach { host ->
            when (val outcome = network.perHost[host]) {
                is HostSearchOutcome.Live -> {
                    perHostArtists += outcome.artists.map(local::preferLocal)
                    perHostAlbums += outcome.albums.map(local::preferLocal)
                    perHostTracks += outcome.tracks.map(local::preferLocal)
                }
                is HostSearchOutcome.Unavailable, null -> {
                    val (a, al, t) = local.filterFor(host, network.query)
                    perHostArtists += a
                    perHostAlbums += al
                    perHostTracks += t
                }
            }
        }
        RawResults(
            artists = interleave(perHostArtists),
            albums = interleave(perHostAlbums),
            tracks = interleave(perHostTracks),
            radioStations = local.radioStations.filter { it.name.contains(network.query, ignoreCase = true) },
            isSearching = network.isSearching
        )
    }

    /** Round-robins across servers (one item from each host in turn) instead of concatenating
     *  host-by-host — matters because Top Hits then caps each category (`take(1)` artists,
     *  `take(3)` albums, `take(5)` tracks): a plain concatenation lets whichever host happens to
     *  come first in [networkSearch]'s host list fill the *entire* cap before a second server's
     *  matches are even considered, silently hiding them from Top Hits even though the full
     *  per-category tab (uncapped) would show every server correctly either way. */
    private fun <T> interleave(perHost: List<List<T>>): List<T> {
        if (perHost.size <= 1) return perHost.firstOrNull().orEmpty()
        val result = mutableListOf<T>()
        var index = 0
        while (true) {
            var addedAny = false
            for (list in perHost) {
                if (index < list.size) {
                    result += list[index]
                    addedAny = true
                }
            }
            if (!addedAny) break
            index++
        }
        return result
    }

    /** The debounced flow above still lags 300ms behind when the query is cleared — re-combining
     *  with the raw (non-debounced) query and overriding to empty the instant it's blank keeps
     *  clearing the search bar instant, matching iOS's `searchText.isEmpty` early-clear branch. */
    private val effectiveResults = combine(_query, debouncedResults) { raw, debounced ->
        if (raw.isBlank()) RawResults.EMPTY else debounced
    }

    private val playbackAndDownloadState = combine(
        playbackController.nowPlaying,
        downloadRepository.downloads,
        networkAvailability.canReachNetwork
    ) { nowPlaying, downloads, canReachNetwork -> Triple(nowPlaying, downloads, canReachNetwork) }

    val uiState: StateFlow<SearchUiState> = combine(
        effectiveResults,
        _filter,
        recentSearchesStore.terms,
        playbackAndDownloadState
    ) { results, filter, recents, (nowPlaying, downloads, canReachNetwork) ->
        SearchUiState(
            artists = results.artists,
            albums = results.albums,
            tracks = results.tracks,
            radioStations = results.radioStations,
            filter = filter,
            recentSearches = recents,
            nowPlaying = nowPlaying,
            downloads = downloads,
            canReachNetwork = canReachNetwork,
            isSearching = results.isSearching
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(text: String) {
        val wasBlank = _query.value.isBlank()
        _query.value = text
        if (wasBlank && text.isNotBlank()) _filter.value = SearchFilter.TOP_HITS
    }

    fun onFilterSelect(filter: SearchFilter) {
        _filter.value = filter
    }

    fun onSubmit() = commitSearch()

    fun onRecentSearchTap(term: String) {
        _query.value = term
    }

    fun removeRecentSearch(term: String) = viewModelScope.launch { recentSearchesStore.remove(term) }

    fun clearRecentSearches() = viewModelScope.launch { recentSearchesStore.clear() }

    fun onArtistTap() = commitSearch()

    fun onAlbumTap() = commitSearch()

    fun onTrackTap(track: TrackEntity) {
        commitSearch()
        playbackController.playTrack(track)
    }

    fun onRadioTap(station: RadioStationEntity) {
        commitSearch()
        val current = uiState.value.nowPlaying
        if (current.radioStation?.id == station.id) playbackController.togglePlayPause()
        else playbackController.playRadio(station)
    }

    fun playNext(track: TrackEntity) = playbackController.insertPlayNext(track)

    fun playInstantMix(track: TrackEntity) = playbackController.playInstantMix(track)

    /** [uiState]'s tracks flow from [libraryRepository.observeTracks], a Room-reactive query — the
     *  upsert alone re-emits the updated row, same as [de.ilazlow.velosonic.ui.album.AlbumDetailViewModel]'s
     *  version of this. */
    fun toggleTrackFavorite(track: TrackEntity) {
        val config = playbackSubsonicClient.configFor(track.serverHost) ?: return
        val newStarred = !track.isStarred
        viewModelScope.launch {
            libraryRepository.upsertTracks(listOf(track.copy(isStarred = newStarred)))
            if (newStarred) playbackSubsonicClient.star(config, track.subsonicId) else playbackSubsonicClient.unstar(config, track.subsonicId)
        }
    }

    fun toggleTrackDownload(track: TrackEntity) {
        if (downloadRepository.isDownloaded(track.id)) downloadRepository.removeDownload(track.id)
        else downloadRepository.downloadTrack(track, partOfBulkGroup = false)
    }

    fun isTrackCached(track: TrackEntity): Boolean = playbackController.isTrackCached(track)

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 150): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    private fun commitSearch() {
        val term = _query.value
        if (term.isNotBlank()) viewModelScope.launch { recentSearchesStore.add(term) }
    }
}
