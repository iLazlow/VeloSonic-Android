package de.ilazlow.velosonic.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_configs ORDER BY name")
    fun observeAll(): Flow<List<ServerConfigEntity>>

    @Query("SELECT * FROM server_configs ORDER BY name")
    suspend fun getAll(): List<ServerConfigEntity>

    @Query("SELECT * FROM server_configs WHERE host = :host")
    suspend fun getByHost(host: String): ServerConfigEntity?

    /** True add order (SQLite's implicit rowid), not alphabetical — a reasonable bootstrap
     *  default for anything that should reflect "your servers, in the order you set them up"
     *  (see [de.ilazlow.velosonic.data.datastore.ServerOrderStore], which is authoritative once
     *  populated; this is just what seeds/falls back for servers added before that existed).
     *  Note this drifts on every re-upsert of an existing host (REPLACE deletes+reinserts,
     *  bumping its rowid) — fine as a one-time bootstrap, not a substitute for the real store. */
    @Query("SELECT * FROM server_configs ORDER BY rowid")
    suspend fun getAllOrderedByInsertion(): List<ServerConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ServerConfigEntity)

    @Update
    suspend fun update(config: ServerConfigEntity)

    @Delete
    suspend fun delete(config: ServerConfigEntity)
}
