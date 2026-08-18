package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE key = :host")
    suspend fun getForHost(host: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE key = :host")
    fun observeForHost(host: String): Flow<SyncMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE key = :host")
    suspend fun deleteForHost(host: String)
}
