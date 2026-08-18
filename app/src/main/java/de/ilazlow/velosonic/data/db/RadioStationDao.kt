package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {
    @Query("SELECT * FROM radio_stations WHERE serverHost IN (:visibleHosts) ORDER BY name")
    fun observeVisible(visibleHosts: List<String>): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM radio_stations WHERE serverHost = :serverHost")
    suspend fun getAllForServer(serverHost: String): List<RadioStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stations: List<RadioStationEntity>)

    @Query("DELETE FROM radio_stations WHERE serverHost = :serverHost")
    suspend fun deleteByServer(serverHost: String)

    @Query("SELECT COUNT(*) FROM radio_stations WHERE serverHost = :serverHost")
    suspend fun countForServer(serverHost: String): Int
}
