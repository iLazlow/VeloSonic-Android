package de.ilazlow.velosonic.analysis

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Hand-rolled radix-2 Cooley-Tukey FFT plus the small numeric helpers
 *  AudioAnalysisManager.swift/AudioAnalysisMath.swift built on top of Accelerate (vDSP) — no FFT
 *  library dependency needed since every call site in this codebase uses a fixed power-of-two
 *  size (4096). Uses a plain (denormalized) Hann window rather than vDSP's `HANN_NORM` variant
 *  (which just multiplies by a constant `sqrt(8/3)` to normalize average power to 1) — harmless
 *  since every consumer either L1-normalizes the result (chroma) or takes a self-relative ratio
 *  (acousticness, BPM autocorrelation), so a uniform scale factor cancels out either way.
 */
object AudioAnalysisMath {

    /** In-place iterative radix-2 FFT. [re]/[im] length must be a power of two. */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n and (n - 1) == 0) { "FFT size must be a power of two, was $n" }
        if (n <= 1) return

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angleStep = -2.0 * Math.PI / len
            var i = 0
            while (i < n) {
                for (k in 0 until halfLen) {
                    val angle = angleStep * k
                    val wr = cos(angle).toFloat()
                    val wi = sin(angle).toFloat()
                    val evenIdx = i + k
                    val oddIdx = i + k + halfLen
                    val oddRe = re[oddIdx] * wr - im[oddIdx] * wi
                    val oddIm = re[oddIdx] * wi + im[oddIdx] * wr
                    re[oddIdx] = re[evenIdx] - oddRe
                    im[oddIdx] = im[evenIdx] - oddIm
                    re[evenIdx] += oddRe
                    im[evenIdx] += oddIm
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Hann-windowed squared-magnitude spectrum of a real-valued [frame], zero-padded/truncated
     *  to [fftSize]. Squared (re^2+im^2, matching vDSP_zvmags — not sqrt-scaled) since every
     *  caller only ever compares/sums magnitudes, never needs true amplitude. Returns
     *  fftSize/2+1 bins (0..Nyquist inclusive). */
    fun fftMagnitudesSquared(frame: FloatArray, fftSize: Int): FloatArray {
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val n = minOf(frame.size, fftSize)
        for (i in 0 until n) {
            val w = 0.5f * (1f - cos(2.0 * Math.PI * i / (fftSize - 1)).toFloat())
            re[i] = frame[i] * w
        }
        fft(re, im)
        val bins = fftSize / 2 + 1
        val mags = FloatArray(bins)
        for (i in 0 until bins) {
            mags[i] = re[i] * re[i] + im[i] * im[i]
        }
        return mags
    }

    /** Direct port of AudioAnalysisMath.swift's `_analysisPearson`. */
    fun pearson(x: DoubleArray, y: DoubleArray): Double {
        val n = minOf(x.size, y.size)
        if (n == 0) return 0.0
        var mx = 0.0
        var my = 0.0
        for (i in 0 until n) { mx += x[i]; my += y[i] }
        mx /= n; my /= n
        var num = 0.0
        var dx = 0.0
        var dy = 0.0
        for (i in 0 until n) {
            val a = x[i] - mx
            val b = y[i] - my
            num += a * b
            dx += a * a
            dy += b * b
        }
        val den = sqrt(dx * dy)
        return if (den > 0.0) num / den else 0.0
    }

    /** Circular left-rotate by [amount] positions, matching Array._analysisRotated(by:). */
    fun rotated(array: DoubleArray, amount: Int): DoubleArray {
        val n = array.size
        if (n == 0) return array
        val k = ((amount % n) + n) % n
        return DoubleArray(n) { i -> array[(i + k) % n] }
    }

    /** Simple linear-interpolation resampler — a deliberately simpler stand-in for iOS's
     *  `AVAudioConverter` (which does proper bandlimited resampling). Good enough for analysis
     *  purposes: BPM/key/classification all work on energy/spectral-shape features that are
     *  insensitive to the mild high-frequency aliasing linear interpolation introduces. */
    fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val ratio = toRate.toDouble() / fromRate.toDouble()
        val outLength = (input.size * ratio).toInt()
        val output = FloatArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i / ratio
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val a = input[idx.coerceIn(0, input.size - 1)]
            val b = input[(idx + 1).coerceIn(0, input.size - 1)]
            output[i] = a + (b - a) * frac
        }
        return output
    }
}
