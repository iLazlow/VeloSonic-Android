package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackAnalysisDao {
    @Query("SELECT * FROM track_analysis WHERE id = :id")
    suspend fun getById(id: String): TrackAnalysisEntity?

    @Query("SELECT * FROM track_analysis WHERE serverHost = :serverHost")
    suspend fun getAllForServer(serverHost: String): List<TrackAnalysisEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(analyses: List<TrackAnalysisEntity>)

    @Query("DELETE FROM track_analysis WHERE serverHost = :serverHost")
    suspend fun deleteByServer(serverHost: String)
}
