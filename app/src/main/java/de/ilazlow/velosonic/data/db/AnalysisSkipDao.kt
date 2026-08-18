package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AnalysisSkipDao {
    @Query("SELECT * FROM analysis_skips WHERE serverHost = :serverHost")
    suspend fun getAllForServer(serverHost: String): List<AnalysisSkipEntity>

    @Query("SELECT id FROM analysis_skips WHERE serverHost = :serverHost")
    suspend fun getSkippedIds(serverHost: String): List<String>

    @Query("SELECT * FROM analysis_skips WHERE id = :id")
    suspend fun getById(id: String): AnalysisSkipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skip: AnalysisSkipEntity)

    @Query("DELETE FROM analysis_skips WHERE serverHost = :serverHost")
    suspend fun deleteByServer(serverHost: String)
}
