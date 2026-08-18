package de.ilazlow.velosonic.analysis

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

data class KeyResult(
    val key: String = "C",
    val mode: String = "major",
    val confidence: Double = 0.0
)

private const val FFT_SIZE = 4096

private val KEY_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Standard Krumhansl-Kessler 12-element key-profile correlation weights, verbatim from
 *  AudioAnalysisManager.swift's `majorProfile`/`minorProfile`. */
private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

/** Direct port of AudioAnalysisManager.swift's `detectKey` — 4096-sample FFT chromagram (50%
 *  overlap hop), pitch-class binning restricted to 27.5-4200Hz, then a 24-way (12 keys x
 *  major/minor) Pearson correlation search against the Krumhansl-Kessler profiles. */
object KeyAnalyzer {

    fun detectKey(samples: FloatArray, sampleRate: Double): KeyResult {
        if (samples.size < FFT_SIZE) return KeyResult()

        val hopSamples = FFT_SIZE / 2
        val binHz = sampleRate / FFT_SIZE
        val chroma = DoubleArray(12)

        var pos = 0
        while (pos + FFT_SIZE <= samples.size) {
            val frame = samples.copyOfRange(pos, pos + FFT_SIZE)
            val mags = AudioAnalysisMath.fftMagnitudesSquared(frame, FFT_SIZE)
            for (bin in 1 until mags.size) {
                val freq = bin * binHz
                if (freq < 27.5 || freq > 4200.0) continue
                val midiNote = 69.0 + 12.0 * log2(freq / 440.0)
                val pc = ((midiNote.roundToInt() % 12) + 12) % 12
                chroma[pc] += mags[bin].toDouble()
            }
            pos += hopSamples
        }

        val chromaSum = chroma.sum()
        if (chromaSum > 0) for (i in chroma.indices) chroma[i] /= chromaSum

        var bestKeyIdx = 0
        var bestMode = "major"
        var bestCorr = -1.0
        for (i in 0 until 12) {
            val mc = AudioAnalysisMath.pearson(chroma, AudioAnalysisMath.rotated(MAJOR_PROFILE, i))
            val mn = AudioAnalysisMath.pearson(chroma, AudioAnalysisMath.rotated(MINOR_PROFILE, i))
            if (mc > bestCorr) { bestCorr = mc; bestKeyIdx = i; bestMode = "major" }
            if (mn > bestCorr) { bestCorr = mn; bestKeyIdx = i; bestMode = "minor" }
        }

        return KeyResult(key = KEY_NAMES[bestKeyIdx], mode = bestMode, confidence = max(0.0, bestCorr))
    }
}
