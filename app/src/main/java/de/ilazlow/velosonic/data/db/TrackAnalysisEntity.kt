package de.ilazlow.velosonic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * On-device BPM/key/mood analysis results (Phase 11) — never synced from the server.
 * `id` is composite: "{serverHost}_{trackSubsonicId}", matching TrackEntity.id. Mirrors
 * SDTrackAnalysis; `soundLabels` mood/instrumental detection comes from YAMNet on Android
 * instead of Apple's SoundAnalysis framework (see the port plan's tech-stack decisions).
 */
@Entity(
    tableName = "track_analysis",
    indices = [Index("serverHost")]
)
data class TrackAnalysisEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val serverHost: String,
    val analyzedAt: Long,
    val audioSourceWasLocal: Boolean = false,

    // Acoustic features
    val bpm: Double? = null,
    val bpmConfidence: Double? = null,
    val energy: Double? = null,
    val loudnessDb: Double? = null,
    val danceability: Double? = null,
    val acousticness: Double? = null,
    val key: String? = null,
    val keyConfidence: Double? = null,
    val mode: String? = null,

    /** "label|confidence,label|confidence,..." sorted by confidence desc, from YAMNet. */
    val soundLabels: String? = null,
    val isInstrumental: Boolean? = null,
    val vocalConfidence: Double? = null,

    /** BCP-47, e.g. "en", "de" — from ML Kit Language Identification on track metadata text. */
    val detectedLanguage: String? = null,
    val languageConfidence: Double? = null,
    val languageSource: String? = null
)
