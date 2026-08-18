package de.ilazlow.velosonic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.StandaloneDownloadDao
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.sync.compositeId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DownloadsUiState(
    val playlists: List<PlaylistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val songs: List<TrackEntity> = emptyList(),
    val artists: List<Pair<String, Int>> = emptyList()
)

/**
 * Mirrors LibraryDownloadsView.swift's 4-category hub (Playlists/Albums/Songs/Artists), computed
 * live from [DownloadRepository]'s reactive download state rather than iOS's manual filesystem
 * scan — "fully downloaded" for a playlist/album is recomputed here every time, so unlike iOS's
 * `SDPlaylist.isFullyDownloaded` stored flag it can never drift out of sync with reality and
 * never needed a self-healing pass to fix a stale value.
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val standaloneDownloadDao: StandaloneDownloadDao,
    private val coverArtUrlResolver: CoverArtUrlResolver
) : ViewModel() {
    val uiState: StateFlow<DownloadsUiState> = combine(downloadRepository.downloads, libraryRepository.observePlaylists()) { downloads, playlists ->
        downloads to playlists
    }.map { (downloads, playlists) -> buildState(downloads, playlists) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    /** Drives the queue-toolbar icon's filled/outline state — mirrors iOS's `hasActivity`. */
    val hasActiveDownloads: StateFlow<Boolean> = downloadRepository.activity
        .map { activity -> activity.any { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private suspend fun buildState(downloads: Map<String, Download>, playlists: List<PlaylistEntity>): DownloadsUiState {
        val completedIds = downloads.filterValues { it.state == Download.STATE_COMPLETED }.keys
        if (completedIds.isEmpty()) return DownloadsUiState()

        val downloadedTracks = libraryRepository.getTracksByCompositeIds(completedIds.toList())
        val standaloneIds = standaloneDownloadDao.getAll().map { it.id }.toSet()
        val songs = downloadedTracks.filter { it.id in standaloneIds }

        val albums = downloadedTracks
            .filter { it.albumCompositeId != null }
            .groupBy { it.albumCompositeId!! }
            .mapNotNull { (albumId, groupTracks) ->
                val album = libraryRepository.getAlbumById(albumId) ?: return@mapNotNull null
                val total = album.songCount ?: return@mapNotNull null
                if (groupTracks.size >= total) album else null
            }

        val artists = downloadedTracks
            .groupBy { it.artistName }
            .map { (name, list) -> name to list.size }
            .sortedBy { it.first.lowercase() }

        val fullyDownloadedPlaylists = playlists.filter { playlist ->
            playlist.trackIds.isNotEmpty() && playlist.trackIds.all { compositeId(playlist.serverHost, it) in completedIds }
        }

        return DownloadsUiState(playlists = fullyDownloadedPlaylists, albums = albums, songs = songs, artists = artists)
    }

    fun coverArtUrl(serverHost: String, coverArt: String?, size: Int = 300): String? =
        coverArtUrlResolver.urlFor(serverHost, coverArt, size)

    fun removeDownload(trackId: String) = downloadRepository.removeDownload(trackId)
}
