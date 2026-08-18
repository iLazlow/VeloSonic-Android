package de.ilazlow.velosonic.analysis

import de.ilazlow.velosonic.data.db.TrackAnalysisEntity
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Direct port of `TrackStyleFeatures`/its `similarity(to:)` from BPMHeartRateMatcher.swift —
 * built here (rather than deferred to the later Wear OS phase that actually needs
 * `BPMHeartRateMatcher` itself) because [de.ilazlow.velosonic.playback.ContinuousMixResolver]'s
 * local-similarity tier has an immediate use for it today. Weights (label cosine x3.0, energy
 * proximity x1.5, language match x2.0, instrumental/vocal match x0.5) and the two soft-mismatch
 * scores (language mismatch 0.15, instrumental mismatch 0.5, rather than 0) are verbatim from
 * the Swift source, not approximated.
 */
data class TrackStyleFeatures(
    val soundLabels: List<Pair<String, Double>> = emptyList(),
    val energy: Double? = null,
    val language: String? = null,
    val isInstrumental: Boolean? = null
) {
    fun similarity(other: TrackStyleFeatures): Double {
        var totalScore = 0.0
        var totalWeight = 0.0

        if (soundLabels.isNotEmpty() && other.soundLabels.isNotEmpty()) {
            val a = soundLabels.take(12).groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
            val b = other.soundLabels.take(12).groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
            var dot = 0.0
            for ((label, conf) in a) dot += conf * (b[label] ?: 0.0)
            val magA = sqrt(a.values.sumOf { it * it })
            val magB = sqrt(b.values.sumOf { it * it })
            val cosine = if (magA > 0 && magB > 0) dot / (magA * magB) else 0.0
            totalScore += cosine * 3.0
            totalWeight += 3.0
        }

        val e1 = energy
        val e2 = other.energy
        if (e1 != null && e2 != null) {
            totalScore += maxOf(0.0, 1.0 - abs(e1 - e2) * 1.5) * 1.5
            totalWeight += 1.5
        }

        val l1 = language
        val l2 = other.language
        if (!l1.isNullOrEmpty() && !l2.isNullOrEmpty()) {
            totalScore += (if (l1 == l2) 1.0 else 0.15) * 2.0
            totalWeight += 2.0
        }

        val v1 = isInstrumental
        val v2 = other.isInstrumental
        if (v1 != null && v2 != null) {
            totalScore += (if (v1 == v2) 1.0 else 0.5) * 0.5
            totalWeight += 0.5
        }

        return if (totalWeight > 0) totalScore / totalWeight else 0.5
    }

    companion object {
        fun from(entity: TrackAnalysisEntity): TrackStyleFeatures = TrackStyleFeatures(
            soundLabels = parseLabels(entity.soundLabels),
            energy = entity.energy,
            language = entity.detectedLanguage,
            isInstrumental = entity.isInstrumental
        )

        private fun parseLabels(encoded: String?): List<Pair<String, Double>> {
            if (encoded.isNullOrEmpty()) return emptyList()
            return encoded.split(",").mapNotNull { part ->
                val idx = part.lastIndexOf('|')
                if (idx <= 0) return@mapNotNull null
                val label = part.substring(0, idx)
                val confidence = part.substring(idx + 1).toDoubleOrNull() ?: return@mapNotNull null
                label to confidence
            }.sortedByDescending { it.second }
        }
    }
}
