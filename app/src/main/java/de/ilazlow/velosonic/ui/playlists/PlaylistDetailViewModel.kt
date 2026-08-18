package de.ilazlow.velosonic.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository
import de.ilazlow.velosonic.data.datastore.AppearanceSettingsStore
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.PlaylistSortField
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.NavidromeJwtClient
import de.ilazlow.velosonic.data.network.NetworkAvailability
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.playlist.PlaylistArtworkUploader
import de.ilazlow.velosonic.data.playlist.PlaylistSubsonicClient
import de.ilazlow.velosonic.data.sync.compositeId
import de.ilazlow.velosonic.data.sync.toStandaloneEntity
import de.ilazlow.velosonic.domain.isNavidrome
import de.ilazlow.velosonic.domain.supportsPlaylistArtworkUpload
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import de.ilazlow.velosonic.ui.navigation.PlaylistDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val playbackController: PlaybackController,
    private val playlistSubsonicClient: PlaylistSubsonicClient,
    private val playbackSubsonicClient: PlaybackSubsonicClient,
    private val playlistArtworkUploader: PlaylistArtworkUploader,
    private val navidromeJwtClient: NavidromeJwtClient,
    private val downloadRepository: DownloadRepository,
    private val animatedArtworkRepository: AnimatedArtworkRepository,
    networkAvailability: NetworkAvailability,
    appearanceSettingsStore: AppearanceSettingsStore
) : ViewModel() {
    private val route: PlaylistDetailRoute = savedStateHandle.toRoute()

    val playlist: StateFlow<PlaylistEntity?> = libraryRepository.observePlaylistById(route.playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val tracks: StateFlow<List<TrackEntity>> = _tracks.asStateFlow()

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying

    val downloads: StateFlow<Map<String, Download>> = downloadRepository.downloads

    val canReachNetwork: StateFlow<Boolean> = networkAvailability.canReachNetwork

    val animateWebpArtwork: StateFlow<Boolean> = appearanceSettingsStore.settings
        .map { it.animatedArtworksEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Uses the playlist's first track as the identifying (artist, album, title) triplet —
     *  same rationale as AlbumDetailViewModel's own version of this, see its doc comment. */
    val animatedVideoUri: StateFlow<String?> = combine(playlist.filterNotNull(), tracks) { playlist, tracks -> playlist to tracks.firstOrNull() }
        .distinctUntilChangedBy { (playlist, firstTrack) -> playlist.id to firstTrack?.id }
        .flatMapLatest { (playlist, firstTrack) ->
            if (firstTrack == null) return@flatMapLatest flowOf(null)
            flow<String?> {
                emit(null)
                emit(
                    animatedArtworkRepository.resolve(
                        serverHost = playlist.serverHost,
                        coverArtId = playlist.coverArt,
                        trackId = firstTrack.id,
                        artist = firstTrack.artistName,
                        album = firstTrack.albumName.orEmpty(),
                        title = firstTrack.title
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            playlist.collect { p ->
                if (p == null) {
                    _tracks.value = emptyList()
                    return@collect
                }
                val compositeIds = p.trackIds.map { compositeId(p.serverHost, it) }
                val fetchedById = libraryRepository.getTracksByCompositeIds(compositeIds).associateBy { it.subsonicId }
                val missingIds = p.trackIds.filter { it !in fetchedById }

                // Show whatever's already local immediately — healMissingTracks below is a
                // network round-trip, and leaving the whole screen blank until it finishes (or
                // fails offline) meant a playlist with even one unsynced track showed "No tracks"
                // the entire time, instead of everything that was already there locally.
                _tracks.value = sortTracks(p.trackIds.mapNotNull { fetchedById[it] }, p.sortField, p.isSortReversed)

                if (missingIds.isNotEmpty()) {
                    val healed = healMissingTracks(p, missingIds)
                    if (healed.isNotEmpty()) {
                        val merged = p.trackIds.mapNotNull { fetchedById[it] ?: healed[it] }
                        _tracks.value = sortTracks(merged, p.sortField, p.isSortReversed)
                    }
                }
            }
        }
    }

    /** A playlist can list tracks the normal artist/album sync crawl never found — confirmed live
     *  against a Tidal-wrapper OpenSubsonic proxy: that server's playlists draw from Tidal's full
     *  remote catalog, while the artist/album crawl only ever sees the user's own ~24 indexed
     *  "library" artists, so [getTracksByCompositeIds][de.ilazlow.velosonic.data.LibraryRepository.getTracksByCompositeIds]
     *  silently dropped every one of them and the playlist looked empty. Mirrors iOS's
     *  `PlaylistDetailViewModel.loadTracks`: one `getPlaylist` round-trip already returns full
     *  track objects (not just ids) for the whole playlist, so whatever's missing gets built from
     *  that response directly — no per-track `getSong` round-trips — and persisted locally so a
     *  later open of the same playlist never needs to re-fetch it. */
    private suspend fun healMissingTracks(p: PlaylistEntity, missingIds: List<String>): Map<String, TrackEntity> {
        val config = playlistSubsonicClient.configFor(p.serverHost) ?: return emptyMap()
        val detail = playlistSubsonicClient.getPlaylistDetail(config, p.subsonicId) ?: return emptyMap()
        val missingSet = missingIds.toSet()
        val healedEntities = detail.entry.orEmpty()
            .filter { it.id in missingSet }
            .map { it.toStandaloneEntity(p.serverHost) }
        if (healedEntities.isNotEmpty()) {
            libraryRepository.upsertTracks(healedEntities)
        }
        return healedEntities.associateBy { it.subsonicId }
    }

    private fun sortTracks(list: List<TrackEntity>, field: PlaylistSortField, reversed: Boolean): List<TrackEntity> {
        val sorted = when (field) {
            PlaylistSortField.CUSTOM -> list
            PlaylistSortField.TITLE -> list.sortedBy { it.title.lowercase() }
            PlaylistSortField.DURATION -> list.sortedBy { it.duration }
        }
        return if (reversed) sorted.reversed() else sorted
    }

    fun coverArtUrl(serverHost: String, coverArt: String?, size: Int = 800): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArt, size)

    fun playAll() {
        if (tracks.value.isNotEmpty()) playbackController.playQueue(tracks.value, 0, playlistId = route.playlistId)
        markPlayed()
    }

    fun playShuffled() {
        val shuffled = tracks.value.shuffled()
        if (shuffled.isNotEmpty()) playbackController.playQueue(shuffled, 0, playlistId = route.playlistId)
        markPlayed()
    }

    fun onTrackClick(track: TrackEntity) {
        val index = tracks.value.indexOfFirst { it.id == track.id }
        if (index == -1) return
        playbackController.playQueue(tracks.value, index, playlistId = route.playlistId)
        markPlayed()
    }

    private fun markPlayed() {
        viewModelScope.launch { libraryRepository.markPlaylistPlayed(route.playlistId) }
    }

    fun playNext(track: TrackEntity) = playbackController.insertPlayNext(track)

    fun playInstantMix(track: TrackEntity) = playbackController.playInstantMix(track)

    fun isTrackCached(track: TrackEntity): Boolean = playbackController.isTrackCached(track)

    /** [_tracks] is a plain manually-populated list (see the `init` block's `healMissingTracks`
     *  flow), not a Room-reactive observer like [AlbumDetailViewModel]'s own [tracks] — so unlike
     *  that version, the DB upsert alone wouldn't update this screen; the local list also needs an
     *  explicit optimistic rewrite. */
    fun toggleTrackFavorite(track: TrackEntity) {
        val config = playbackSubsonicClient.configFor(track.serverHost) ?: return
        val newStarred = !track.isStarred
        _tracks.value = _tracks.value.map { if (it.id == track.id) it.copy(isStarred = newStarred) else it }
        viewModelScope.launch {
            libraryRepository.upsertTracks(listOf(track.copy(isStarred = newStarred)))
            if (newStarred) playbackSubsonicClient.star(config, track.subsonicId) else playbackSubsonicClient.unstar(config, track.subsonicId)
        }
    }

    fun setSortField(field: PlaylistSortField) {
        val p = playlist.value ?: return
        viewModelScope.launch { libraryRepository.upsertPlaylist(p.copy(sortField = field)) }
    }

    fun toggleSortReversed() {
        val p = playlist.value ?: return
        viewModelScope.launch { libraryRepository.upsertPlaylist(p.copy(isSortReversed = !p.isSortReversed)) }
    }

    /** Removes by the track's own id rather than its position in [tracks] — [tracks] may be
     *  display-sorted (Title/Duration/reversed), so a display index doesn't line up with the
     *  position `removeTrackAt` needs in the playlist's real (server) order. */
    fun removeTrack(track: TrackEntity) {
        val p = playlist.value ?: return
        val realIndex = p.trackIds.indexOf(track.subsonicId)
        if (realIndex < 0) return
        val config = playlistSubsonicClient.configFor(p.serverHost) ?: return
        val updatedIds = p.trackIds.toMutableList().also { it.removeAt(realIndex) }
        viewModelScope.launch {
            libraryRepository.upsertPlaylist(p.copy(trackIds = updatedIds, songCount = updatedIds.size))
            playlistSubsonicClient.removeTrackAt(config, p.subsonicId, realIndex)
        }
    }

    /** Bulk playlist download — excluded from standalone bookkeeping (matches iOS: only a
     *  playlist's own bulk download is tracked via a group, everything else, including a
     *  single track downloaded from within this same screen's row menu, is standalone). */
    fun downloadPlaylist() {
        downloadRepository.downloadTracks(tracks.value, partOfBulkGroup = true)
    }

    fun removeAllDownloads() {
        downloadRepository.removeDownloads(tracks.value.map { it.id })
    }

    fun toggleTrackDownload(track: TrackEntity) {
        if (downloadRepository.isDownloaded(track.id)) downloadRepository.removeDownload(track.id)
        else downloadRepository.downloadTrack(track, partOfBulkGroup = false)
    }

    /** True whenever the current playlist's server accepts a custom cover-art upload — gates
     *  the artwork-picker row in [de.ilazlow.velosonic.ui.playlists.CreateEditPlaylistSheet]. */
    fun supportsArtworkUpload(): Boolean {
        val p = playlist.value ?: return false
        return playlistSubsonicClient.configFor(p.serverHost)?.supportsPlaylistArtworkUpload ?: false
    }

    fun coverArtUrlForEdit(): String? {
        val p = playlist.value ?: return null
        return coverArtUrlResolver.urlFor(p.serverHost, p.coverArt, 300)
    }

    /** True whenever the current playlist is a smart playlist on a Navidrome server — gates
     *  whether the edit sheet shows the rules editor at all. */
    fun isSmartPlaylistEditable(): Boolean {
        val p = playlist.value ?: return false
        return p.isSmartPlaylist && (playlistSubsonicClient.configFor(p.serverHost)?.isNavidrome ?: false)
    }

    fun smartRulesJsonForEdit(): String? = playlist.value?.rulesJSON

    /** Lazily fetches the current playlist's rules when they weren't already synced locally —
     *  see [CreateEditPlaylistSheet]'s `fetchSmartRules` parameter. */
    suspend fun fetchSmartRules(): String? {
        val p = playlist.value ?: return null
        val config = playlistSubsonicClient.configFor(p.serverHost) ?: return null
        return navidromeJwtClient.fetchNativePlaylistRules(config, p.subsonicId)
    }

    /** [newCoverBytes]/[newCoverMimeType] stay null on a plain rename — artwork is only ever
     *  touched when the user actually picked a new image in the edit sheet. A smart playlist's
     *  rename goes entirely through [NavidromeJwtClient.updateNativePlaylistRules] instead (one
     *  PUT sets name/public/rules together); artwork upload doesn't apply there, matching iOS's
     *  mutually-exclusive `if isSmart {...} else {artwork}` edit-sheet branches. */
    fun rename(
        newName: String,
        isPublic: Boolean,
        newCoverBytes: ByteArray? = null,
        newCoverMimeType: String? = null,
        smartRulesJson: String? = null
    ) {
        val p = playlist.value ?: return
        val config = playlistSubsonicClient.configFor(p.serverHost) ?: return
        viewModelScope.launch {
            if (p.isSmartPlaylist && config.isNavidrome) {
                val ok = navidromeJwtClient.updateNativePlaylistRules(config, p.subsonicId, newName, isPublic, smartRulesJson)
                if (ok) libraryRepository.upsertPlaylist(p.copy(name = newName, publicStatus = isPublic, rulesJSON = smartRulesJson))
                return@launch
            }
            libraryRepository.upsertPlaylist(p.copy(name = newName, publicStatus = isPublic))
            playlistSubsonicClient.renamePlaylist(config, p.subsonicId, newName, isPublic)
            if (newCoverBytes != null && newCoverMimeType != null) {
                playlistArtworkUploader.upload(config, p, newCoverBytes, newCoverMimeType)
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val p = playlist.value ?: return
        val config = playlistSubsonicClient.configFor(p.serverHost) ?: return
        viewModelScope.launch {
            libraryRepository.deletePlaylist(p.id)
            playlistSubsonicClient.deletePlaylist(config, p.subsonicId)
            onDeleted()
        }
    }
}
