package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists WHERE serverHost IN (:visibleHosts) ORDER BY name")
    fun observeVisible(visibleHosts: List<String>): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE serverHost = :serverHost")
    suspend fun getAllForServer(serverHost: String): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM artists WHERE serverHost = :serverHost")
    suspend fun deleteByServer(serverHost: String)

    @Query("SELECT COUNT(*) FROM artists WHERE serverHost = :serverHost")
    suspend fun countForServer(serverHost: String): Int
}
