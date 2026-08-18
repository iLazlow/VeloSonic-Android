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
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val canReachNetwork: Boolean = true
) {
    val hasResults: Boolean get() = artists.isNotEmpty() || albums.isNotEmpty() || tracks.isNotEmpty() || radioStations.isNotEmpty()
}

private data class RawResults(
    val artists: List<ArtistEntity>,
    val albums: List<AlbumEntity>,
    val tracks: List<TrackEntity>,
    val radioStations: List<RadioStationEntity>
) {
    companion object {
        val EMPTY = RawResults(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * Mirrors SearchViewModel.swift: search is purely local — filters the already-synced Room
 * library (same [LibraryRepository] every other list screen reads from), never hits the network.
 * 300ms debounce on the query before re-filtering (matches iOS's `Task.sleep(300ms)`), but
 * clearing the query back to blank clears results immediately rather than waiting out the
 * debounce — see [effectiveResults]'s doc comment.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackController: PlaybackController,
    private val downloadRepository: DownloadRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val recentSearchesStore: RecentSearchesStore,
    private val playbackSubsonicClient: PlaybackSubsonicClient,
    networkAvailability: NetworkAvailability,
    serverRepository: ServerRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.TOP_HITS)

    val serverNames: StateFlow<Map<String, String>> = serverRepository.observeServers()
        .map { configs -> if (configs.size > 1) configs.associate { it.host to it.name.ifBlank { it.host } } else emptyMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val debouncedQuery = _query.debounce(300).distinctUntilChanged()

    private val debouncedResults: kotlinx.coroutines.flow.Flow<RawResults> = combine(
        debouncedQuery,
        libraryRepository.observeArtists(),
        libraryRepository.observeAlbums(),
        libraryRepository.observeTracks(),
        libraryRepository.observeRadioStations()
    ) { q, artists, albums, tracks, radio ->
        if (q.isBlank()) {
            RawResults.EMPTY
        } else {
            RawResults(
                artists = artists.filter { it.name.contains(q, ignoreCase = true) },
                albums = albums.filter { it.name.contains(q, ignoreCase = true) || it.artistName.contains(q, ignoreCase = true) },
                tracks = tracks.filter { it.title.contains(q, ignoreCase = true) || it.artistName.contains(q, ignoreCase = true) },
                radioStations = radio.filter { it.name.contains(q, ignoreCase = true) }
            )
        }
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
            canReachNetwork = canReachNetwork
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
