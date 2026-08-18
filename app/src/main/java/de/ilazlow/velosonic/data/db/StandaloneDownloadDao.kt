package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StandaloneDownloadDao {
    @Query("SELECT * FROM standalone_downloads")
    suspend fun getAll(): List<StandaloneDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: StandaloneDownloadEntity)

    @Delete
    suspend fun delete(entry: StandaloneDownloadEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM standalone_downloads WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
