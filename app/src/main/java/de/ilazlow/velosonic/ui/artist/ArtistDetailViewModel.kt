package de.ilazlow.velosonic.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.artist.ArtistSubsonicClient
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NetworkAvailability
import de.ilazlow.velosonic.data.network.dto.SimilarArtistDto
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.sync.resolveCompositeId
import de.ilazlow.velosonic.data.sync.toFreshEntity
import de.ilazlow.velosonic.data.sync.toLightweightEntity
import de.ilazlow.velosonic.data.sync.toStandaloneEntity
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import de.ilazlow.velosonic.ui.navigation.ArtistDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun String.stripHtmlTags(): String = replace(Regex("<[^>]+>"), "")

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val playbackController: PlaybackController,
    private val playbackSubsonicClient: PlaybackSubsonicClient,
    private val artistSubsonicClient: ArtistSubsonicClient,
    private val downloadRepository: DownloadRepository,
    private val serverRepository: ServerRepository,
    networkAvailability: NetworkAvailability,
    appearanceSettingsStore: AppearanceSettingsStore
) : ViewModel() {
    private val route: ArtistDetailRoute = savedStateHandle.toRoute()

    val artistName: String get() = route.artistName

    private val _artist = MutableStateFlow<ArtistEntity?>(null)
    val artist: StateFlow<ArtistEntity?> = _artist.asStateFlow()

    /** Populated only when this artist isn't synced into Room at all yet (e.g. navigated to from
     *  a live search3 result — see the init block's fallback) — [albums] prefers the Room-observed
     *  list whenever it's non-empty, so this is superseded automatically the moment a background
     *  sync actually picks the artist up. */
    private val _networkFallbackAlbums = MutableStateFlow<List<AlbumEntity>>(emptyList())
    val albums: StateFlow<List<AlbumEntity>> = combine(
        libraryRepository.observeAlbumsByArtist(route.artistId),
        _networkFallbackAlbums
    ) { local, network -> local.ifEmpty { network } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _topSongs = MutableStateFlow<List<TrackEntity>>(emptyList())
    val topSongs: StateFlow<List<TrackEntity>> = _topSongs.asStateFlow()

    private val _bio = MutableStateFlow<String?>(null)
    val bio: StateFlow<String?> = _bio.asStateFlow()

    private val _similarArtists = MutableStateFlow<List<SimilarArtistDto>>(emptyList())
    val similarArtists: StateFlow<List<SimilarArtistDto>> = _similarArtists.asStateFlow()

    /** Albums where this artist is credited on a track (`artistIdsList`) but isn't the album's
     *  primary artist (`artistId`) — features/guest spots. Mirrors iOS's `appearsInAlbums`;
     *  disjoint from [albums] by construction, since that's filtered to primary-artist albums. */
    private val _appearsInAlbums = MutableStateFlow<List<AlbumEntity>>(emptyList())
    val appearsInAlbums: StateFlow<List<AlbumEntity>> = _appearsInAlbums.asStateFlow()

    private val _appearsInTrackCount = MutableStateFlow(0)

    val downloads: StateFlow<Map<String, Download>> = downloadRepository.downloads

    val canReachNetwork: StateFlow<Boolean> = networkAvailability.canReachNetwork

    /** Every track across this artist's own albums — re-fetched whenever [albums] changes, purely
     *  to reactively drive the header Download/Remove Downloads toggle (mirrors Album/Playlist
     *  Detail's own `isFullyDownloaded`); [downloadArtist] does its own one-shot equivalent fetch
     *  at click time and doesn't depend on this. */
    val artistTracks: StateFlow<List<TrackEntity>> = albums
        .flatMapLatest { albumList -> flow { emit(libraryRepository.getTracksByAlbumIds(albumList.map { it.id })) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    val servers: StateFlow<List<ServerConfigEntity>> = serverRepository.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val animatedAlbumGridEnabled: StateFlow<Boolean> = appearanceSettingsStore.settings
        .map { it.animatedAlbumGridEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Own-album song counts (reactive, from Room) plus the featured-track count from
     *  [_appearsInTrackCount] (set once, after that one-shot fetch completes) — mirrors iOS's
     *  `totalSongCount += featuredSongCount`. */
    val totalSongCount: StateFlow<Int> = combine(albums, _appearsInTrackCount) { list, extra ->
        list.sumOf { it.songCount ?: 0 } + extra
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            // Not synced locally yet — e.g. navigated to from a live search3 result that was
            // never part of a background sync. Resolve which server it came from against the
            // composite id, then fetch fresh via getArtist instead of leaving the whole screen
            // blank (matches the same "server as fallback" gap fixed for Album Detail).
            val entity = libraryRepository.getArtistById(route.artistId) ?: run {
                val hosts = coverArtUrlResolver.allConfigs().map { it.host }
                val (fallbackHost, subsonicId) = resolveCompositeId(route.artistId, hosts) ?: return@launch
                val config = artistSubsonicClient.configFor(fallbackHost) ?: return@launch
                val detail = artistSubsonicClient.fetchArtist(config, subsonicId) ?: return@launch
                _networkFallbackAlbums.value = detail.album.orEmpty().map { it.toLightweightEntity(fallbackHost) }
                detail.toFreshEntity(fallbackHost)
            }
            _artist.value = entity
            val host = entity.serverHost

            // Shown immediately, before any network call — mirrors iOS's `preTracks`. Without
            // this, opening an artist while offline (or just on a slow connection) left "Top
            // Songs" completely blank until the fetch below either succeeded or gave up, even
            // though this artist's own already-synced tracks were sitting right there locally.
            _topSongs.value = libraryRepository.getTracksByArtistId(host, entity.subsonicId)

            val candidates = libraryRepository.getTracksWithArtistIdsList(host)
            val matchingTracks = candidates.filter { track ->
                track.artistId != entity.subsonicId && track.artistIdsList?.contains(entity.subsonicId) == true
            }
            _appearsInTrackCount.value = matchingTracks.size
            val matchingAlbumIds = matchingTracks.mapNotNull { it.albumCompositeId }.distinct()
            if (matchingAlbumIds.isNotEmpty()) {
                _appearsInAlbums.value = libraryRepository.getAlbumsByIds(matchingAlbumIds).sortedByDescending { it.year ?: 0 }
            }

            val config = artistSubsonicClient.configFor(host) ?: return@launch
            // A failed/empty network fetch (offline, timeout) keeps the local fallback above
            // rather than clearing Top Songs back to nothing — only a real, non-empty result
            // from the server (which also carries the server's own ranking, unlike the local
            // fallback's arbitrary order) replaces it.
            // .distinctBy: confirmed live against a real server — fetchTopSongs can return the
            // same track twice (e.g. it exists on two different releases/albums), and TopSongsSection
            // renders these by id.
            val fetchedTopSongs = artistSubsonicClient.fetchTopSongs(config, route.artistName).map { it.toStandaloneEntity(host) }.distinctBy { it.id }
            if (fetchedTopSongs.isNotEmpty()) _topSongs.value = fetchedTopSongs

            val info = artistSubsonicClient.fetchArtistInfo(config, entity.subsonicId)
            _bio.value = info?.biography?.stripHtmlTags()?.trim()?.takeIf(String::isNotEmpty)
            // .distinctBy: confirmed live against a real server — getArtistInfo's similarArtist
            // list can contain the same artist id twice, which crashed SimilarArtistsSection's
            // LazyRow (`items(similar, key = { it.id })` requires unique keys).
            _similarArtists.value = info?.similarArtist.orEmpty().distinctBy { it.id }
        }
    }

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 400): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    fun avatarUrl(size: Int = 400): String? {
        val entity = _artist.value ?: return null
        return coverArtUrlResolver.urlFor(entity.serverHost, entity.subsonicId, size)
    }

    fun playShuffled() {
        val songs = topSongs.value
        if (songs.isNotEmpty()) playbackController.playQueue(songs.shuffled(), 0)
    }

    fun playTopSongs(startIndex: Int = 0) {
        val songs = topSongs.value
        if (songs.isNotEmpty()) playbackController.playQueue(songs, startIndex)
    }

    fun toggleFavorite() {
        val current = _artist.value ?: return
        val config = playbackSubsonicClient.configFor(current.serverHost) ?: return
        val newStarred = !current.isStarred
        // Optimistic — same trust model as Album/Playlist detail: the next sync reconciles this
        // with the server's real starred state regardless.
        _artist.value = current.copy(isStarred = newStarred)
        viewModelScope.launch {
            if (newStarred) playbackSubsonicClient.star(config, current.subsonicId)
            else playbackSubsonicClient.unstar(config, current.subsonicId)
        }
    }

    fun downloadArtist() {
        viewModelScope.launch {
            val tracks = libraryRepository.getTracksByAlbumIds(albums.value.map { it.id })
            downloadRepository.downloadTracks(tracks, partOfBulkGroup = false)
        }
    }

    fun removeArtistDownloads() {
        downloadRepository.removeDownloads(artistTracks.value.map { it.id })
    }

    fun toggleTrackDownload(track: TrackEntity) {
        if (downloadRepository.isDownloaded(track.id)) downloadRepository.removeDownload(track.id)
        else downloadRepository.downloadTrack(track, partOfBulkGroup = false)
    }

    fun playNext(track: TrackEntity) = playbackController.insertPlayNext(track)

    fun playInstantMix(track: TrackEntity) = playbackController.playInstantMix(track)

    fun isTrackCached(track: TrackEntity): Boolean = playbackController.isTrackCached(track)

    /** [_topSongs] is a plain manually-populated list (local fallback, then overwritten by a
     *  network fetch — see the `init` block), not a Room-reactive observer, so the DB upsert alone
     *  wouldn't update this screen; the local list also needs an explicit optimistic rewrite, same
     *  as [de.ilazlow.velosonic.ui.playlists.PlaylistDetailViewModel]'s version of this. */
    fun toggleTrackFavorite(track: TrackEntity) {
        val config = playbackSubsonicClient.configFor(track.serverHost) ?: return
        val newStarred = !track.isStarred
        _topSongs.value = _topSongs.value.map { if (it.id == track.id) it.copy(isStarred = newStarred) else it }
        viewModelScope.launch {
            libraryRepository.upsertTracks(listOf(track.copy(isStarred = newStarred)))
            if (newStarred) playbackSubsonicClient.star(config, track.subsonicId) else playbackSubsonicClient.unstar(config, track.subsonicId)
        }
    }
}
