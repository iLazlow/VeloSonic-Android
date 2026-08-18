package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE serverHost IN (:visibleHosts) ORDER BY title")
    fun observeVisible(visibleHosts: List<String>): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumCompositeId = :albumId ORDER BY discNumber, trackNumber")
    fun observeByAlbum(albumId: String): Flow<List<TrackEntity>>

    /** Every track across a set of albums — backs "Download Artist" (all tracks in all of an
     *  artist's local albums), given the artist's already-loaded [de.ilazlow.velosonic.data.db.AlbumEntity] ids. */
    @Query("SELECT * FROM tracks WHERE albumCompositeId IN (:albumIds)")
    suspend fun getByAlbumIds(albumIds: List<String>): List<TrackEntity>

    /** Candidates for Artist Detail's "Appears On" section — every multi-artist-credited track
     *  on this host, filtered further in Kotlin (against [TrackEntity.artistIdsList], a
     *  Room-converted list column with no SQL-level "array contains" to query on) to the ones
     *  actually crediting the artist in question. Mirrors the iOS engine's own fetch-then-filter
     *  shape for the same reason. */
    @Query("SELECT * FROM tracks WHERE serverHost = :serverHost AND artistIdsList IS NOT NULL")
    suspend fun getWithArtistIdsList(serverHost: String): List<TrackEntity>

    /** Local fallback for Artist Detail's "Top Songs" while the real server-ranked list is still
     *  loading — or unavailable entirely (offline, a slow connection) — mirrors iOS's `preTracks`
     *  (own local query, shown immediately, kept if the network fetch comes back empty rather
     *  than clearing to nothing). Not ordered by anything meaningful; the server's own ranking is
     *  what actually matters once/if it arrives. */
    @Query("SELECT * FROM tracks WHERE artistId = :artistId AND serverHost = :serverHost LIMIT :limit")
    suspend fun getByArtistId(serverHost: String, artistId: String, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isStarred = 1 AND serverHost IN (:visibleHosts) ORDER BY starredAt DESC")
    fun observeFavorites(visibleHosts: List<String>): Flow<List<TrackEntity>>

    @Query("SELECT DISTINCT genre FROM tracks WHERE genre IS NOT NULL AND genre != '' AND serverHost IN (:visibleHosts) ORDER BY genre")
    fun observeGenres(visibleHosts: List<String>): Flow<List<String>>

    @Query("SELECT * FROM tracks WHERE genre = :genre AND serverHost IN (:visibleHosts) ORDER BY title")
    fun observeByGenre(genre: String, visibleHosts: List<String>): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE serverHost = :serverHost")
    suspend fun getAllForServer(serverHost: String): List<TrackEntity>

    /** [limit]-bounded — see [de.ilazlow.velosonic.data.lyrics.LyricsSyncWorker]'s doc comment on
     *  why an unbounded fetch here is a real battery-drain risk on a large library. */
    @Query("SELECT * FROM tracks WHERE lyricsCheckedAt IS NULL AND serverHost = :serverHost LIMIT :limit")
    suspend fun getPendingLyricsCheck(serverHost: String, limit: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE lyricsCheckedAt IS NULL AND serverHost = :serverHost")
    suspend fun countPendingLyricsCheck(serverHost: String): Int

    /** Library-wide, every server — matches LyricsSyncManager's `refreshCounts` (unfiltered by
     *  visibility/host), backing the Lyrics Sync section's idle "checked / total" display. */
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE lyricsCheckedAt IS NOT NULL")
    suspend fun countAllLyricsChecked(): Int

    /** "Full Resync" — clears every track's lyrics-checked stamp so the next sync pass re-queries
     *  the whole library instead of only what's never been checked. Mirrors LyricsSyncManager's
     *  `resetFirst` path. */
    @Query("UPDATE tracks SET lyricsCheckedAt = NULL WHERE serverHost = :serverHost")
    suspend fun resetLyricsCheckedAt(serverHost: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    /** Raw (non-composite) Navidrome album id — matches SDTrack.albumId lookups in the iOS sync engine. */
    @Query("DELETE FROM tracks WHERE albumId = :albumId AND serverHost = :serverHost")
    suspend fun deleteByAlbumRawId(albumId: String, serverHost: String)

    @Query("DELETE FROM tracks WHERE serverHost = :serverHost")
    suspend fun deleteByServer(serverHost: String)

    @Query("SELECT COUNT(*) FROM tracks WHERE serverHost = :serverHost")
    suspend fun countForServer(serverHost: String): Int

    @Query("SELECT COUNT(DISTINCT genre) FROM tracks WHERE genre IS NOT NULL AND genre != '' AND serverHost = :serverHost")
    suspend fun countGenresForServer(serverHost: String): Int
}
