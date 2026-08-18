package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums WHERE serverHost IN (:visibleHosts) ORDER BY name")
    fun observeVisible(visibleHosts: List<String>): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE artistCompositeId = :artistId ORDER BY year DESC")
    fun observeByArtist(artistId: String): Flow<List<AlbumEntity>>

    // `created` is when Navidrome added the album to its library (matches getAlbumList2's
    // type=newest semantics on iOS) — deliberately NOT year/date (the album's release date),
    // which is a completely different axis: an old release just added today still belongs here.
    // NULLs (an album whose getAlbum detail merge never landed) sort last in SQLite's DESC order,
    // not first, so a sync gap can't wrongly bubble an unknown-created-date album to the top.
    @Query("SELECT * FROM albums WHERE serverHost IN (:visibleHosts) ORDER BY created DESC LIMIT :limit")
    fun observeNewest(visibleHosts: List<String>, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverHost IN (:visibleHosts) AND played IS NOT NULL ORDER BY played DESC LIMIT :limit")
    fun observeRecentlyPlayed(visibleHosts: List<String>, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverHost IN (:visibleHosts) AND playCount IS NOT NULL AND playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun observeFrequent(visibleHosts: List<String>, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE serverHost IN (:visibleHosts) ORDER BY RANDOM() LIMIT :limit")
    fun observeRandom(visibleHosts: List<String>, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE isStarred = 1 AND serverHost IN (:visibleHosts) ORDER BY name")
    fun observeFavorites(visibleHosts: List<String>): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE serverHost = :serverHost")
    suspend fun getAllForServer(serverHost: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<AlbumEntity>

    /** Raw (non-composite) Navidrome artist id — matches SDAlbum.artistId lookups in the iOS sync engine. */
    @Query("SELECT * FROM albums WHERE artistId = :artistId AND serverHost = :serverHost")
    suspend fun getByArtistRawId(artistId: String, serverHost: String): List<AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums WHERE artistId = :artistId AND serverHost = :serverHost")
    suspend fun countByArtistRawId(artistId: String, serverHost: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM albums WHERE artistId = :artistId AND serverHost = :serverHost")
    suspend fun deleteByArtistRawId(artistId: String, serverHost: String)

    @Query("DELETE FROM albums WHERE serverHost = :serverHost")
    suspend fun deleteByServer(serverHost: String)

    @Query("SELECT COUNT(*) FROM albums WHERE serverHost = :serverHost")
    suspend fun countForServer(serverHost: String): Int
}
