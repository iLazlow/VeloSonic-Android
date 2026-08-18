package de.ilazlow.velosonic.data.sync

import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.room.withTransaction
import de.ilazlow.velosonic.data.db.AnalysisSkipDao
import de.ilazlow.velosonic.data.db.ArtistDao
import de.ilazlow.velosonic.data.db.AlbumDao
import de.ilazlow.velosonic.data.db.PlaylistDao
import de.ilazlow.velosonic.data.db.RadioStationDao
import de.ilazlow.velosonic.data.db.StandaloneDownloadDao
import de.ilazlow.velosonic.data.db.StandaloneDownloadEntity
import de.ilazlow.velosonic.data.db.SyncMetadataDao
import de.ilazlow.velosonic.data.db.TrackAnalysisDao
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.VeloSonicDatabase
import de.ilazlow.velosonic.data.download.DownloadCacheProvider
import de.ilazlow.velosonic.data.download.DownloadRepository
import de.ilazlow.velosonic.data.lyrics.RadiantLyricsCacheStore
import de.ilazlow.velosonic.data.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migrates all host-scoped local data (composite ids across every Room table, the Media3
 * download index, the on-disk Radiant Lyrics cache, and the Keystore credential entry) when a
 * server's address changes in the edit-server flow. Mirrors iOS's `ServerMigrationManager`, with
 * one addition iOS doesn't need: Android's downloads are indexed by Media3's own SQLite-backed
 * [androidx.media3.exoplayer.offline.DownloadIndex] rather than a custom dictionary, so that gets
 * rewritten too — see [migrateDownloadIndex]'s doc comment for why that's safe.
 *
 * Must run before the caller creates/updates the new [de.ilazlow.velosonic.data.db.ServerConfigEntity]
 * row — see [de.ilazlow.velosonic.data.ServerRepository.editServer], the only call site.
 */
@Singleton
class ServerMigrationManager @Inject constructor(
    private val database: VeloSonicDatabase,
    private val trackDao: TrackDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val playlistDao: PlaylistDao,
    private val radioStationDao: RadioStationDao,
    private val trackAnalysisDao: TrackAnalysisDao,
    private val analysisSkipDao: AnalysisSkipDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val standaloneDownloadDao: StandaloneDownloadDao,
    private val credentialStore: CredentialStore,
    private val downloadCacheProvider: DownloadCacheProvider,
    private val downloadRepository: DownloadRepository,
    private val radiantLyricsCacheStore: RadiantLyricsCacheStore
) {
    /** @param onProgress called with a value in 0f..1f as work completes. */
    suspend fun migrate(
        oldHost: String,
        newHost: String,
        username: String,
        onProgress: (Float) -> Unit = {}
    ) {
        if (oldHost == newHost) return
        withContext(Dispatchers.IO) {
            onProgress(0f)

            // Cancel anything mid-flight for the old host first, so nothing completes mid-
            // migration and writes a file under a cache key this migration never sees.
            downloadRepository.cancelActiveDownloadsForHost(oldHost)

            val tracks = trackDao.getAllForServer(oldHost)
            val artists = artistDao.getAllForServer(oldHost)
            val albums = albumDao.getAllForServer(oldHost)
            val playlists = playlistDao.getAllForServer(oldHost)
            val radios = radioStationDao.getAllForServer(oldHost)
            val analyses = trackAnalysisDao.getAllForServer(oldHost)
            val skips = analysisSkipDao.getAllForServer(oldHost)
            val trackIdPrefix = "${oldHost}_"
            val standaloneDownloads = standaloneDownloadDao.getAll().filter { it.id.startsWith(trackIdPrefix) }

            val total = (tracks.size + artists.size + albums.size + playlists.size + radios.size +
                analyses.size + skips.size + standaloneDownloads.size + 1).coerceAtLeast(1)
            var processed = 0
            fun bump() {
                processed++
                onProgress(processed.toFloat() / total)
            }

            database.withTransaction {
                val migratedTracks = tracks.map { track ->
                    track.copy(
                        id = compositeId(newHost, track.subsonicId),
                        serverHost = newHost,
                        albumCompositeId = track.albumCompositeId?.let { compositeId(newHost, track.albumId) }
                    )
                }
                trackDao.deleteByServer(oldHost)
                trackDao.upsertAll(migratedTracks)
                repeat(tracks.size) { bump() }

                val migratedArtists = artists.map { it.copy(id = compositeId(newHost, it.subsonicId), serverHost = newHost) }
                artistDao.deleteByServer(oldHost)
                artistDao.upsertAll(migratedArtists)
                repeat(artists.size) { bump() }

                val migratedAlbums = albums.map { album ->
                    album.copy(
                        id = compositeId(newHost, album.subsonicId),
                        serverHost = newHost,
                        artistCompositeId = album.artistId?.let { compositeId(newHost, it) }
                    )
                }
                albumDao.deleteByServer(oldHost)
                albumDao.upsertAll(migratedAlbums)
                repeat(albums.size) { bump() }

                val migratedPlaylists = playlists.map { it.copy(id = compositeId(newHost, it.subsonicId), serverHost = newHost) }
                playlistDao.deleteByServer(oldHost)
                playlistDao.upsertAll(migratedPlaylists)
                repeat(playlists.size) { bump() }

                val migratedRadios = radios.map { it.copy(id = compositeId(newHost, it.subsonicId), serverHost = newHost) }
                radioStationDao.deleteByServer(oldHost)
                radioStationDao.upsertAll(migratedRadios)
                repeat(radios.size) { bump() }

                // Audio-analysis/skip records key off the track's composite id rather than a
                // separate subsonicId field, so the new trackId/id are derived by re-prefixing it.
                val migratedAnalyses = analyses.map { analysis ->
                    val newTrackId = compositeId(newHost, analysis.trackId.removePrefix(trackIdPrefix))
                    analysis.copy(id = newTrackId, trackId = newTrackId, serverHost = newHost)
                }
                trackAnalysisDao.deleteByServer(oldHost)
                trackAnalysisDao.upsertAll(migratedAnalyses)
                repeat(analyses.size) { bump() }

                val migratedSkips = skips.map { skip ->
                    val newTrackId = compositeId(newHost, skip.trackId.removePrefix(trackIdPrefix))
                    skip.copy(id = newTrackId, trackId = newTrackId, serverHost = newHost)
                }
                analysisSkipDao.deleteByServer(oldHost)
                migratedSkips.forEach { analysisSkipDao.upsert(it) }
                repeat(skips.size) { bump() }

                syncMetadataDao.getForHost(oldHost)?.let { meta ->
                    syncMetadataDao.upsert(meta.copy(key = newHost))
                    syncMetadataDao.deleteForHost(oldHost)
                }

                standaloneDownloads.forEach { download ->
                    val newId = compositeId(newHost, download.id.removePrefix(trackIdPrefix))
                    standaloneDownloadDao.delete(download)
                    standaloneDownloadDao.insert(StandaloneDownloadEntity(newId))
                }
                repeat(standaloneDownloads.size) { bump() }
            }

            migrateDownloadIndex(oldHost, newHost, trackIdPrefix)
            downloadRepository.refreshFromIndex()

            credentialStore.loadPassword(oldHost, username)?.let { password ->
                credentialStore.savePassword(newHost, username, password)
                credentialStore.deletePassword(oldHost, username)
            }

            radiantLyricsCacheStore.migrateHost(oldHost, newHost)

            bump()
            onProgress(1f)
        }
    }

    /** Rewrites completed/stopped downloads' index rows onto the new host's composite id — a
     *  direct [androidx.media3.exoplayer.offline.WritableDownloadIndex] row rewrite, not a
     *  [androidx.media3.exoplayer.offline.DownloadManager] remove+re-add, so the actual cached
     *  audio bytes (keyed independently by hostname+subsonicId, see
     *  [de.ilazlow.velosonic.data.download.stableCacheKeyFactory]) are never touched — only the
     *  bookkeeping row that maps a track id to its download state. [DownloadRepository] is told
     *  to re-scan the index afterward since it never observed this rewrite (it only reacts to
     *  DownloadManager's own listener callbacks). */
    private fun migrateDownloadIndex(oldHost: String, newHost: String, trackIdPrefix: String) {
        val index = downloadCacheProvider.downloadIndex
        val newAuthority = Uri.parse(newHost)
        val toMigrate = mutableListOf<Download>()
        index.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val download = cursor.download
                if (download.request.id.startsWith(trackIdPrefix)) toMigrate += download
            }
        }
        for (download in toMigrate) {
            val newId = compositeId(newHost, download.request.id.removePrefix(trackIdPrefix))
            val newUri = download.request.uri.buildUpon()
                .scheme(newAuthority.scheme)
                .encodedAuthority(newAuthority.encodedAuthority)
                .build()
            val newRequest = DownloadRequest.Builder(newId, newUri).build()
            val migrated = Download(newRequest, download.state, download.startTimeMs, download.updateTimeMs, download.contentLength, download.stopReason, download.failureReason)
            index.putDownload(migrated)
            index.removeDownload(download.request.id)
        }
    }
}
