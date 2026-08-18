package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per server host, keyed by `key = serverHost`. Mirrors SyncMetadata, but deliberately
 * per-host rather than the iOS client's single global "key = \"global\"" row — there, the
 * 3-hour partial-sync gate and `isInitialSyncComplete` flag are shared across every configured
 * server, so a freshly-synced server A can block server B's own background sync (or vice versa)
 * purely because A finished syncing recently. Keying per-host removes that cross-server
 * coupling with no behavior change for the single-server case.
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val lastSyncDate: Long,
    val isInitialSyncComplete: Boolean = false
)
