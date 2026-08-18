package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE serverHost IN (:visibleHosts) ORDER BY name")
    fun observeVisible(visibleHosts: List<String>): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE serverHost IN (:visibleHosts) AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(visibleHosts: List<String>, limit: Int): Flow<List<PlaylistEntity>>

    @Query("UPDATE playlists SET lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun updateLastPlayed(id: String, timestamp: Long)

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE serverHost = :serverHost")
    suspend fun getAllForServer(serverHost: String): List<PlaylistEntity>

    @Query("SELECT COUNT(*) FROM playlists WHERE serverHost = :serverHost")
    suspend fun countForServer(serverHost: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playlists WHERE serverHost = :serverHost")
    suspend fun deleteByServer(serverHost: String)
}
