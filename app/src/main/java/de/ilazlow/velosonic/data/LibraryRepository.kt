package de.ilazlow.velosonic.data

import de.ilazlow.velosonic.data.db.AlbumDao
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ArtistDao
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.db.PlaylistDao
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.RadioStationDao
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.ServerConfigDao
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.TrackEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class HomeSections(
    val recentlyPlayed: List<AlbumEntity> = emptyList(),
    val recentlyAdded: List<AlbumEntity> = emptyList(),
    val frequentlyPlayed: List<AlbumEntity> = emptyList(),
    val random: List<AlbumEntity> = emptyList()
)

/**
 * Wraps the browsing DAOs with the "servers included in combined views" resolution — mirrors
 * `[ServerConfig].visible` on iOS: every server the user hasn't hidden, falling back to all
 * servers if every one is hidden (so combined views never end up permanently empty). There's no
 * UI to actually hide a server yet (that's a Phase 12 settings screen), so today this always
 * resolves to every configured host — but every query is already wired through this so adding
 * that toggle later doesn't require touching any screen.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val serverConfigDao: ServerConfigDao,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val radioStationDao: RadioStationDao,
    private val playlistDao: PlaylistDao
) {
    private val visibleHosts: Flow<List<String>> = serverConfigDao.observeAll().map { configs ->
        configs.filter(ServerConfigEntity::isVisible).ifEmpty { configs }.map(ServerConfigEntity::host)
    }

    /** Public passthrough for callers that need the same "servers included in combined views"
     *  resolution but aren't just wrapping a single Room query with it — e.g.
     *  [de.ilazlow.velosonic.data.HomeSectionsRepository]'s live per-server network fetch. */
    fun observeVisibleHosts(): Flow<List<String>> = visibleHosts

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> scoped(query: (List<String>) -> Flow<T>): Flow<T> =
        visibleHosts.flatMapLatest(query)

    fun observeArtists(): Flow<List<ArtistEntity>> = scoped(artistDao::observeVisible)

    fun observeAlbums(): Flow<List<AlbumEntity>> = scoped(albumDao::observeVisible)

    fun observeAlbumsByArtist(artistCompositeId: String): Flow<List<AlbumEntity>> =
        albumDao.observeByArtist(artistCompositeId)

    fun observeTracksByAlbum(albumCompositeId: String): Flow<List<TrackEntity>> =
        trackDao.observeByAlbum(albumCompositeId)

    fun observeTracks(): Flow<List<TrackEntity>> = scoped(trackDao::observeVisible)

    fun observeFavoriteTracks(): Flow<List<TrackEntity>> = scoped(trackDao::observeFavorites)

    fun observeFavoriteAlbums(): Flow<List<AlbumEntity>> = scoped(albumDao::observeFavorites)

    fun observeRadioStations(): Flow<List<RadioStationEntity>> = scoped(radioStationDao::observeVisible)

    fun observeGenres(): Flow<List<String>> = scoped(trackDao::observeGenres)

    fun observeTracksByGenre(genre: String): Flow<List<TrackEntity>> =
        scoped { hosts -> trackDao.observeByGenre(genre, hosts) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHomeSections(sectionSize: Int = 20): Flow<HomeSections> = scoped { hosts ->
        combineHomeSections(
            albumDao.observeRecentlyPlayed(hosts, sectionSize),
            albumDao.observeNewest(hosts, sectionSize),
            albumDao.observeFrequent(hosts, sectionSize),
            albumDao.observeRandom(hosts, sectionSize)
        )
    }

    fun observePlaylists(): Flow<List<PlaylistEntity>> = scoped(playlistDao::observeVisible)

    fun observeRecentlyPlayedPlaylists(limit: Int = 8): Flow<List<PlaylistEntity>> =
        scoped { hosts -> playlistDao.observeRecentlyPlayed(hosts, limit) }

    fun observePlaylistById(id: String): Flow<PlaylistEntity?> = playlistDao.observeById(id)

    suspend fun markPlaylistPlayed(id: String) = playlistDao.updateLastPlayed(id, System.currentTimeMillis())

    suspend fun getArtistById(id: String): ArtistEntity? = artistDao.getById(id)

    suspend fun getAlbumById(id: String): AlbumEntity? = albumDao.getById(id)

    suspend fun getPlaylistById(id: String): PlaylistEntity? = playlistDao.getById(id)

    suspend fun upsertPlaylist(playlist: PlaylistEntity) = playlistDao.upsert(playlist)

    suspend fun deletePlaylist(id: String) = playlistDao.deleteById(id)

    /** Resolves a playlist's ordered `trackIds` (composite ids) to full [TrackEntity] rows,
     *  preserving order — `getByIds`'s `IN (...)` query doesn't guarantee it, same pattern as
     *  PlaybackEngine's queue-restore. Ids with no matching row (deleted/never-synced track) are
     *  silently dropped. */
    suspend fun getTracksByAlbumIds(albumIds: List<String>): List<TrackEntity> = trackDao.getByAlbumIds(albumIds)

    /** See [de.ilazlow.velosonic.data.db.TrackDao.getWithArtistIdsList] — the raw candidate set
     *  Artist Detail's "Appears On" section filters down to that one artist's actual credits. */
    suspend fun getTracksWithArtistIdsList(serverHost: String): List<TrackEntity> = trackDao.getWithArtistIdsList(serverHost)

    suspend fun getTracksByArtistId(serverHost: String, artistId: String, limit: Int = 15): List<TrackEntity> =
        trackDao.getByArtistId(serverHost, artistId, limit)

    suspend fun getAlbumsByIds(ids: List<String>): List<AlbumEntity> = albumDao.getByIds(ids)

    suspend fun getTracksByCompositeIds(ids: List<String>): List<TrackEntity> {
        val fetched = trackDao.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { fetched[it] }
    }

    /** Lets a caller (see [de.ilazlow.velosonic.ui.playlists.PlaylistDetailViewModel]'s missing-
     *  track heal) persist tracks discovered outside the normal artist/album crawl, so a
     *  subsequent lookup finds them locally instead of re-fetching every time. */
    suspend fun upsertTracks(tracks: List<TrackEntity>) = trackDao.upsertAll(tracks)
}

private fun combineHomeSections(
    recentlyPlayed: Flow<List<AlbumEntity>>,
    recentlyAdded: Flow<List<AlbumEntity>>,
    frequentlyPlayed: Flow<List<AlbumEntity>>,
    random: Flow<List<AlbumEntity>>
): Flow<HomeSections> = kotlinx.coroutines.flow.combine(
    recentlyPlayed, recentlyAdded, frequentlyPlayed, random
) { played, added, frequent, rand ->
    HomeSections(played, added, frequent, rand)
}
