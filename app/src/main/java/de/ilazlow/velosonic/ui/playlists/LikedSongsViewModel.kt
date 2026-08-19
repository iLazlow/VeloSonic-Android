package de.ilazlow.velosonic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.datastore.LikedSongsSettings
import de.ilazlow.velosonic.data.datastore.LikedSongsSettingsStore
import de.ilazlow.velosonic.data.datastore.LikedSongsSortOrder
import de.ilazlow.velosonic.data.datastore.ServerOrderStore
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "Liked Songs" pseudo-playlist — mirrors iOS's `LikedSongsView`: same starred-tracks
 *  query Library's Favorites tab uses ([LibraryRepository.observeFavoriteTracks], already scoped
 *  to visible servers), same play/shuffle/download/per-track actions via the shared
 *  [PlaylistTrackRow], no rename/delete/edit since it isn't a real [de.ilazlow.velosonic.data.db.PlaylistEntity].
 *  Two deliberate Android-only additions beyond iOS parity, explicitly requested: a real sort menu
 *  (iOS hard-codes newest-liked-first) and a per-server include/exclude filter (iOS only has the
 *  blanket app-wide visible-servers toggle every combined screen already respects). */
@HiltViewModel
class LikedSongsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    serverRepository: ServerRepository,
    private val serverOrderStore: ServerOrderStore,
    private val coverArtUrlResolver: CoverArtUrlResolver,
    private val downloadRepository: DownloadRepository,
    private val playbackController: PlaybackController,
    private val playbackSubsonicClient: PlaybackSubsonicClient,
    private val likedSongsSettingsStore: LikedSongsSettingsStore
) : ViewModel() {
    val settings: StateFlow<LikedSongsSettings> = likedSongsSettingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LikedSongsSettings())

    /** Servers included in combined views, in the user's chosen order — same pattern as
     *  [PlaylistsViewModel.visibleServers]; the filter menu offers all of these as toggle rows. */
    val visibleServers: StateFlow<List<ServerConfigEntity>> = combine(
        serverRepository.observeServers(), serverOrderStore.order
    ) { configs, order ->
        val visible = configs.filter { it.isVisible }.ifEmpty { configs }
        serverOrderStore.applyOrder(visible, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<TrackEntity>> = combine(
        libraryRepository.observeFavoriteTracks(), settings
    ) { tracks, settings ->
        sortTracks(tracks.filter { it.serverHost !in settings.excludedHosts }, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nowPlaying: StateFlow<NowPlaying> = playbackController.nowPlaying
    val downloads: StateFlow<Map<String, Download>> = downloadRepository.downloads

    private fun sortTracks(list: List<TrackEntity>, settings: LikedSongsSettings): List<TrackEntity> {
        val sorted = when (settings.sortOrder) {
            LikedSongsSortOrder.DATE_LIKED -> list.sortedBy { it.starredAt ?: Long.MIN_VALUE }
            LikedSongsSortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            LikedSongsSortOrder.ARTIST -> list.sortedBy { it.artistName.lowercase() }
            LikedSongsSortOrder.DURATION -> list.sortedBy { it.duration }
        }
        return if (settings.sortAscending) sorted else sorted.reversed()
    }

    fun selectSortOrder(order: LikedSongsSortOrder) = viewModelScope.launch { likedSongsSettingsStore.selectSortOrder(order) }

    fun toggleServerExcluded(host: String) = viewModelScope.launch { likedSongsSettingsStore.toggleExcludedHost(host) }

    fun coverArtUrl(serverHost: String, coverArt: String?, size: Int = 150): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArt, size)

    fun playAll() {
        if (tracks.value.isNotEmpty()) playbackController.playQueue(tracks.value, 0)
    }

    fun playShuffled() {
        val shuffled = tracks.value.shuffled()
        if (shuffled.isNotEmpty()) playbackController.playQueue(shuffled, 0)
    }

    fun onTrackClick(track: TrackEntity) {
        val index = tracks.value.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playQueue(tracks.value, index)
    }

    fun playNext(track: TrackEntity) = playbackController.insertPlayNext(track)

    fun playInstantMix(track: TrackEntity) = playbackController.playInstantMix(track)

    fun isTrackCached(track: TrackEntity): Boolean = playbackController.isTrackCached(track)

    /** [tracks] is Room-reactive (straight off [LibraryRepository.observeFavoriteTracks]) — the DB
     *  upsert alone refreshes the list (an unstar drops the row out of the `isStarred` query
     *  automatically), unlike a real playlist's manually-populated track list which needs an
     *  explicit optimistic rewrite too. */
    fun toggleTrackFavorite(track: TrackEntity) {
        val config = playbackSubsonicClient.configFor(track.serverHost) ?: return
        val newStarred = !track.isStarred
        viewModelScope.launch {
            libraryRepository.upsertTracks(listOf(track.copy(isStarred = newStarred)))
            if (newStarred) playbackSubsonicClient.star(config, track.subsonicId) else playbackSubsonicClient.unstar(config, track.subsonicId)
        }
    }

    fun downloadAll() {
        downloadRepository.downloadTracks(tracks.value, partOfBulkGroup = true)
    }

    fun toggleTrackDownload(track: TrackEntity) {
        if (downloadRepository.isDownloaded(track.id)) downloadRepository.removeDownload(track.id)
        else downloadRepository.downloadTrack(track, partOfBulkGroup = false)
    }
}
