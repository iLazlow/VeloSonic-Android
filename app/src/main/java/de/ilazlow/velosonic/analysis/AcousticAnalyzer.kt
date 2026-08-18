package de.ilazlow.velosonic.analysis

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AcousticResult(
    val bpm: Double? = null,
    val bpmConfidence: Double? = null,
    val energy: Double? = null,
    val loudnessDb: Double? = null,
    val danceability: Double? = null,
    val acousticness: Double? = null
)

private const val FFT_SIZE = 4096

/** Direct port of AudioAnalysisManager.swift's `computeAcousticFeatures` — RMS-envelope onset-
 *  strength function, autocorrelation-based BPM search with an octave-boost toward double-tempo,
 *  and a derived danceability score — plus a separate FFT-based acousticness estimate. All
 *  constants below are verbatim from the Swift source, not approximated. */
object AcousticAnalyzer {

    fun analyze(samples: FloatArray, sampleRate: Double): AcousticResult {
        if (samples.size <= 500) return AcousticResult()

        var sumSq = 0.0
        for (s in samples) sumSq += (s * s).toDouble()
        val rms = sqrt(sumSq / samples.size)
        val loudnessDb = if (rms > 0) 20.0 * log10(rms) else -60.0
        val energy = min(1.0, rms * 10)

        val hopSamples = max(1, (sampleRate * 0.01).toInt())
        val winSamples = max(hopSamples, (sampleRate * 0.05).toInt())

        val envFrames = ArrayList<Float>(samples.size / hopSamples)
        var pos = 0
        while (pos + winSamples <= samples.size) {
            var frameSumSq = 0.0
            for (i in pos until pos + winSamples) frameSumSq += (samples[i] * samples[i]).toDouble()
            envFrames.add(sqrt(frameSumSq / winSamples).toFloat())
            pos += hopSamples
        }

        if (envFrames.size < 2) return AcousticResult(energy = energy, loudnessDb = loudnessDb)

        val odf = FloatArray(envFrames.size)
        for (i in 1 until envFrames.size) odf[i] = max(0f, envFrames[i] - envFrames[i - 1])

        var maxOdf = 0f
        for (v in odf) if (v > maxOdf) maxOdf = v
        if (maxOdf > 0) for (i in odf.indices) odf[i] /= maxOdf

        val hopDuration = hopSamples / sampleRate
        val maxLag = min(ceil(1.5 / hopDuration).toInt(), odf.size - 1)
        if (maxLag < 1) return AcousticResult(energy = energy, loudnessDb = loudnessDb)

        val acf = DoubleArray(maxLag + 1)
        val n = odf.size
        for (lag in 0..maxLag) {
            var dot = 0.0
            for (i in 0 until n - lag) dot += odf[i] * odf[i + lag]
            acf[lag] = dot
        }

        var bestBpm = 120.0
        var bestScore = 0.0
        var bpmCandidate = 40.0
        while (bpmCandidate <= 220.0) {
            val lag = (60.0 / bpmCandidate / hopDuration).roundToInt()
            if (lag > 0 && lag <= maxLag) {
                val halfLag = max(1, lag / 2)
                val score = acf[lag] + if (halfLag <= maxLag) acf[halfLag] * 0.5 else 0.0
                if (score > bestScore) {
                    bestScore = score
                    bestBpm = bpmCandidate
                }
            }
            bpmCandidate += 0.5
        }
        bestBpm = (bestBpm * 2).roundToInt() / 2.0

        val selfCorr = max(acf.getOrElse(0) { 1e-6 }, 1e-6)
        val bpmConf = min(1.0, bestScore / selfCorr)

        val bpmFactor = if (bestBpm in 80.0..160.0) 1.0 else max(0.0, 1.0 - abs(bestBpm - 120.0) / 120.0)
        val danceability = min(1.0, max(0.0, bpmFactor * 0.5 + bpmConf * 0.3 + energy * 0.2))

        val acousticness = computeAcousticness(samples, sampleRate)

        return AcousticResult(
            bpm = bestBpm,
            bpmConfidence = bpmConf,
            energy = energy,
            loudnessDb = loudnessDb,
            danceability = danceability,
            acousticness = acousticness
        )
    }

    /** Sub-bass (&lt;250Hz) vs. mid-band (250-4000Hz) spectral energy ratio, non-overlapping
     *  4096-sample frames — the exact frame hop wasn't recoverable from the Swift source (only
     *  the band boundaries and FFT size were), so non-overlapping frames were chosen as the
     *  simplest faithful reading; this is a coarse derived ratio, not something that needs
     *  frame-accurate replication. */
    private fun computeAcousticness(samples: FloatArray, sampleRate: Double): Double {
        if (samples.size < FFT_SIZE) return 0.5
        val binHz = sampleRate / FFT_SIZE
        var subBassEnergy = 0.0
        var midEnergy = 0.0
        var framePos = 0
        while (framePos + FFT_SIZE <= samples.size) {
            val frame = samples.copyOfRange(framePos, framePos + FFT_SIZE)
            val mags = AudioAnalysisMath.fftMagnitudesSquared(frame, FFT_SIZE)
            for (bin in 1 until mags.size) {
                val freq = bin * binHz
                when {
                    freq < 250.0 -> subBassEnergy += mags[bin]
                    freq in 250.0..4000.0 -> midEnergy += mags[bin]
                }
            }
            framePos += FFT_SIZE
        }
        val total = subBassEnergy + midEnergy
        return if (total > 0) midEnergy / total else 0.5
    }
}
