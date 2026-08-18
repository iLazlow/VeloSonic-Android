package de.ilazlow.velosonic.data

import de.ilazlow.velosonic.data.db.AlbumDao
import de.ilazlow.velosonic.data.db.ArtistDao
import de.ilazlow.velosonic.data.db.PlaylistDao
import de.ilazlow.velosonic.data.db.RadioStationDao
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.download.DownloadRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors iOS's "Local Database" counters shown on both ManageServersView and ServerDetailView
 *  — artists, albums, songs, genres, playlists, radio stations, and downloaded songs, all scoped
 *  to one server host. */
data class ServerStats(
    val artists: Int = 0,
    val albums: Int = 0,
    val songs: Int = 0,
    val genres: Int = 0,
    val playlists: Int = 0,
    val radioStations: Int = 0,
    val downloadedSongs: Int = 0
)

@Singleton
class ServerStatsRepository @Inject constructor(
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val radioStationDao: RadioStationDao,
    private val downloadRepository: DownloadRepository
) {
    suspend fun statsFor(host: String): ServerStats {
        val downloaded = downloadRepository.downloads.value.count { (id, download) ->
            id.startsWith("${host}_") && download.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
        }
        return ServerStats(
            artists = artistDao.countForServer(host),
            albums = albumDao.countForServer(host),
            songs = trackDao.countForServer(host),
            genres = trackDao.countGenresForServer(host),
            playlists = playlistDao.countForServer(host),
            radioStations = radioStationDao.countForServer(host),
            downloadedSongs = downloaded
        )
    }
}
