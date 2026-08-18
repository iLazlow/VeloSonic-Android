package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Failure-tracking skip queue for audio analysis (Phase 11) — avoids endlessly retrying a
 * pathological track. `id` matches TrackEntity.id. Mirrors SDAnalysisSkip.
 */
@Entity(
    tableName = "analysis_skips",
    indices = [Index("serverHost")]
)
data class AnalysisSkipEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val serverHost: String,
    val failureCount: Int = 1,
    val lastFailureAt: Long,
    val lastFailureReason: String
)
