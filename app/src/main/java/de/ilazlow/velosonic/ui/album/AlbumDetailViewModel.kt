package de.ilazlow.velosonic.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NetworkAvailability
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.sync.resolveCompositeId
import de.ilazlow.velosonic.data.sync.toFreshEntity
import de.ilazlow.velosonic.data.sync.toStandaloneEntity
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import de.ilazlow.velosonic.ui.navigation.AlbumDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val playbackController: PlaybackController,
    private val subsonicClient: PlaybackSubsonicClient,
    private val downloadRepository: DownloadRepository,
    private val animatedArtworkRepository: AnimatedArtworkRepository,
    networkAvailability: NetworkAvailability,
    appearanceSettingsStore: AppearanceSettingsStore
) : ViewModel() {
    private val route: AlbumDetailRoute = savedStateHandle.toRoute()

    private val _album = MutableStateFlow<AlbumEntity?>(null)
    val album: StateFlow<AlbumEntity?> = _album.asStateFlow()

    /** Populated only when this album isn't synced into Room at all yet (e.g. navigated to from a
     *  live search3 result — see the init block's fallback) — [tracks] prefers the Room-observed
     *  list whenever it's non-empty, so this is superseded automatically the moment a background
     *  sync actually picks the album up. */
    private val _networkFallbackTracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val tracks: StateFlow<List<TrackEntity>> = combine(
        libraryRepository.observeTracksByAlbum(route.albumId),
        _networkFallbackTracks
    ) { local, network -> local.ifEmpty { network } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val animateWebpArtwork: StateFlow<Boolean> = appearanceSettingsStore.settings
        .map { it.animatedArtworksEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Uses the album's first track as the identifying (artist, album, title) triplet for the
     *  animated-artwork API — the album header has no single "song" of its own, and this is the
     *  closest equivalent to what iOS's per-track `fetchAppleAnimatedURLs` call needs. */
    val animatedVideoUri: StateFlow<String?> = combine(_album.filterNotNull(), tracks) { album, tracks -> album to tracks.firstOrNull() }
        .distinctUntilChangedBy { (album, firstTrack) -> album.id to firstTrack?.id }
        .flatMapLatest { (album, firstTrack) ->
            if (firstTrack == null) return@flatMapLatest flowOf(null)
            flow<String?> {
                emit(null)
                emit(
                    animatedArtworkRepository.resolve(
                        serverHost = album.serverHost,
                        coverArtId = album.coverArt,
                        trackId = firstTrack.id,
                        artist = album.artistName,
                        album = album.name,
                        title = firstTrack.title
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    /** Keyed by track composite id — mirrors iOS's per-track download status (TrackRow.swift),
     *  not a stateful header button (the real iOS header download trigger is a plain static
     *  menu item with no progress/checkmark, see the port research — only track rows show live
     *  status there, and that's what this is for). */
    val downloads: StateFlow<Map<String, Download>> = downloadRepository.downloads

    val canReachNetwork: StateFlow<Boolean> = networkAvailability.canReachNetwork

    /** Other albums by this album's artist — local-first, since a full sync already pulls every
     *  artist's complete album list (see SyncEngine's getArtist call), so no on-demand network
     *  fetch is needed here. */
    val moreFromArtist: StateFlow<List<AlbumEntity>> = _album
        .filterNotNull()
        .flatMapLatest { current ->
            val artistCid = current.artistCompositeId
            if (artistCid == null) flowOf(emptyList())
            else libraryRepository.observeAlbumsByArtist(artistCid).map { list -> list.filter { it.id != current.id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // Not synced locally yet — e.g. navigated to from a live search3 result that was
            // never part of a background sync. Resolve which server it came from against the
            // composite id, then fetch fresh via getAlbum instead of leaving the whole screen
            // blank.
            val local = libraryRepository.getAlbumById(route.albumId)
            if (local != null) {
                _album.value = local
                return@launch
            }
            val hosts = coverArtUrlResolver.allConfigs().map { it.host }
            val (host, subsonicId) = resolveCompositeId(route.albumId, hosts) ?: return@launch
            val config = subsonicClient.configFor(host) ?: return@launch
            val detail = subsonicClient.fetchAlbum(config, subsonicId) ?: return@launch
            _album.value = detail.toFreshEntity(host)
            _networkFallbackTracks.value = detail.song.orEmpty().map { it.toStandaloneEntity(host) }
        }
    }

    fun coverArtUrl(serverHost: String, coverArtId: String?, size: Int = 300): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArtId, size)

    fun serverName(serverHost: String): String? =
        subsonicClient.configFor(serverHost)?.name?.takeIf { it.isNotBlank() } ?: serverHost

    fun onTrackClick(index: Int) {
        playbackController.playQueue(tracks.value, index, albumId = route.albumId)
    }

    fun playAll() {
        if (tracks.value.isNotEmpty()) playbackController.playQueue(tracks.value, 0, albumId = route.albumId)
    }

    fun playShuffled() {
        val shuffled = tracks.value.shuffled()
        if (shuffled.isNotEmpty()) playbackController.playQueue(shuffled, 0, albumId = route.albumId)
    }

    fun playNext(track: TrackEntity) = playbackController.insertPlayNext(track)

    fun playInstantMix(track: TrackEntity) = playbackController.playInstantMix(track)

    /** Optimistic — [tracks] is a Room-observed [libraryRepository.observeTracksByAlbum] flow, so
     *  the upsert alone re-emits the updated row; no separate local list mutation needed here,
     *  unlike [ArtistDetailViewModel]/[de.ilazlow.velosonic.ui.playlists.PlaylistDetailViewModel]'s
     *  own versions of this, whose track lists aren't Room-reactive. */
    fun toggleTrackFavorite(track: TrackEntity) {
        val config = subsonicClient.configFor(track.serverHost) ?: return
        val newStarred = !track.isStarred
        viewModelScope.launch {
            libraryRepository.upsertTracks(listOf(track.copy(isStarred = newStarred)))
            if (newStarred) subsonicClient.star(config, track.subsonicId) else subsonicClient.unstar(config, track.subsonicId)
        }
    }

    fun totalDurationSeconds(): Int = tracks.value.sumOf { it.duration }

    fun toggleTrackDownload(track: TrackEntity) {
        if (downloadRepository.isDownloaded(track.id)) downloadRepository.removeDownload(track.id)
        else downloadRepository.downloadTrack(track, partOfBulkGroup = false)
    }

    fun isTrackCached(track: TrackEntity): Boolean = playbackController.isTrackCached(track)

    fun downloadAlbum() {
        downloadRepository.downloadTracks(tracks.value, partOfBulkGroup = false)
    }

    fun removeAlbumDownloads() {
        downloadRepository.removeDownloads(tracks.value.map { it.id })
    }

    fun toggleAlbumFavorite() {
        val current = _album.value ?: return
        val config = subsonicClient.configFor(current.serverHost) ?: return
        val newStarred = !current.isStarred
        // Optimistic — the next partial/full sync reconciles this with the server's real
        // starred state regardless, same trust model as the rest of the sync engine.
        _album.value = current.copy(isStarred = newStarred)
        viewModelScope.launch {
            if (newStarred) subsonicClient.star(config, current.subsonicId) else subsonicClient.unstar(config, current.subsonicId)
        }
    }
}
