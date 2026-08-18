package de.ilazlow.velosonic.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.sync.SyncEngine
import de.ilazlow.velosonic.data.sync.SyncMode
import de.ilazlow.velosonic.data.sync.SyncNowWorker
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mirrors `LibraryFavoritesView.swift`: three independently-hideable sections (starred artists,
 *  albums, songs) rather than a single flat list — the previous Android version reused the plain
 *  Songs `TrackList` here, which only ever showed the songs section. */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val playbackController: PlaybackController,
    private val syncEngine: SyncEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    fun onSearchTextChange(value: String) {
        _searchText.value = value
    }

    val starredArtists: StateFlow<List<ArtistEntity>> =
        combine(libraryRepository.observeArtists(), _searchText) { artists, query ->
            artists.filter { it.isStarred }
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val starredAlbums: StateFlow<List<AlbumEntity>> =
        combine(libraryRepository.observeFavoriteAlbums(), _searchText) { albums, query ->
            if (query.isBlank()) albums else albums.filter {
                it.name.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allStarredTracks: StateFlow<List<TrackEntity>> = libraryRepository.observeFavoriteTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val starredTracks: StateFlow<List<TrackEntity>> =
        combine(allStarredTracks, _searchText) { tracks, query ->
            if (query.isBlank()) tracks else tracks.filter {
                it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        viewModelScope.launch {
            syncEngine.state.map { it.isSyncing }.collect { _isSyncing.value = it }
        }
    }

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 300): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    fun avatarUrl(artist: ArtistEntity, size: Int = 200): String? =
        coverArtUrlResolver.urlFor(artist.serverHost, artist.subsonicId, size)

    fun onTrackClick(track: TrackEntity) {
        val list = starredTracks.value
        val index = list.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playQueue(list, index)
    }

    /** Mirrors `SyncManager.syncStarred` — a forced partial sync always refreshes starred content
     *  regardless of the normal 3-hour gate (see `SyncEngine.performPartialSync`'s `forced` flag). */
    fun refreshStarred() = viewModelScope.launch {
        val hosts = libraryRepository.observeVisibleHosts().first()
        SyncNowWorker.enqueueSequential(context, hosts, SyncMode.FORCED_PARTIAL)
    }
}
