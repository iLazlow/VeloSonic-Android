package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records a download that didn't come from a fully-downloaded playlist — a standalone album
 * download, a single-track download, or a bulk artist/Liked-Songs download. Without this,
 * orphaned-file cleanup (Phase 7) would have no way to distinguish these from genuinely
 * abandoned files. `id` matches the on-disk cache key, so membership is a direct lookup.
 * Mirrors SDStandaloneDownload.
 */
@Entity(tableName = "standalone_downloads")
data class StandaloneDownloadEntity(
    @PrimaryKey val id: String
)
