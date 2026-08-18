package de.ilazlow.velosonic.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

val EQ_BAND_FREQUENCIES = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
const val EQ_BAND_COUNT = 10
const val EQ_GAIN_MIN = -12f
const val EQ_GAIN_MAX = 12f
private const val GAIN_EPSILON = 0.05f

private data class BandCoeff(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)

/**
 * Direct port of EQManager.swift's 10-band biquad cascade (RBJ Audio EQ Cookbook formulas —
 * band 0 low-shelf, band 9 high-shelf, bands 1-8 peaking, fixed Q=1/shelf-slope=1 exactly like
 * iOS, never user-adjustable there either) as a Media3 [AudioProcessor] instead of iOS's manual
 * `MTAudioProcessingTap` — same math, idiomatic Media3 plumbing.
 *
 * [enabled]/[gains] are mutated live from [de.ilazlow.velosonic.data.datastore.EqSettingsStore]
 * while this runs on the audio thread — rather than relying on Media3's [isActive] bypass
 * (whose live-toggle timing while a track is already playing isn't something to bet correctness
 * on), the enabled/near-zero-gain check happens inside [queueInput] itself, falling back to a
 * plain passthrough copy. Coefficients are recomputed every call (not cached), matching iOS's
 * own "recompute live every audio-tap callback" approach.
 *
 * Needs its own separate instance per [androidx.media3.exoplayer.ExoPlayer] — crossfade briefly
 * runs two players' audio concurrently, and this processor's per-channel biquad history state
 * would corrupt if shared between them.
 */
class EqAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false
    @Volatile var gains: FloatArray = FloatArray(EQ_BAND_COUNT)

    private var channelCount = 0
    private var sampleRate = 0

    /** [channel][band] -> {x1, x2, y1, y2} direct-form-1 history. */
    private var state: Array<Array<FloatArray>> = emptyArray()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        state = Array(channelCount) { Array(EQ_BAND_COUNT) { FloatArray(4) } }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val currentGains = gains
        val anyBandActive = currentGains.any { abs(it) > GAIN_EPSILON }
        if (!enabled || !anyBandActive) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(remaining)
        val coeffs = Array(EQ_BAND_COUNT) { band -> coefficientsFor(band, currentGains[band]) }

        val inShort = inputBuffer.asShortBuffer()
        val outShort = outputBuffer.asShortBuffer()
        val sampleCount = inShort.remaining() / channelCount
        for (i in 0 until sampleCount) {
            for (ch in 0 until channelCount) {
                var sample = inShort.get(i * channelCount + ch) / 32768f
                for (band in 0 until EQ_BAND_COUNT) {
                    if (abs(currentGains[band]) <= GAIN_EPSILON) continue
                    val c = coeffs[band]
                    val s = state[ch][band]
                    val x1 = s[0]
                    val x2 = s[1]
                    val y1 = s[2]
                    val y2 = s[3]
                    val y = c.b0 * sample + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
                    s[0] = sample
                    s[1] = x1
                    s[2] = y
                    s[3] = y1
                    sample = y
                }
                val clamped = (sample * 32768f).coerceIn(-32768f, 32767f)
                outShort.put(i * channelCount + ch, clamped.toInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        outputBuffer.position(outputBuffer.limit()).flip().position(0)
        outputBuffer.limit(remaining)
    }

    override fun onFlush() {
        for (channelBands in state) for (bandHistory in channelBands) bandHistory.fill(0f)
    }

    override fun onReset() {
        channelCount = 0
        sampleRate = 0
        state = emptyArray()
    }

    private fun coefficientsFor(band: Int, gainDb: Float): BandCoeff {
        val freq = EQ_BAND_FREQUENCIES[band]
        return when (band) {
            0 -> lowShelf(freq, gainDb, sampleRate.toFloat())
            EQ_BAND_COUNT - 1 -> highShelf(freq, gainDb, sampleRate.toFloat())
            else -> peaking(freq, gainDb, sampleRate.toFloat())
        }
    }

    private fun peaking(freq: Float, gainDb: Float, sr: Float, q: Float = 1f): BandCoeff {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * Math.PI.toFloat() * freq / sr
        val alpha = sin(w0) / (2f * q)
        val a0 = 1f + alpha / a
        return BandCoeff(
            b0 = (1f + alpha * a) / a0,
            b1 = (-2f * cos(w0)) / a0,
            b2 = (1f - alpha * a) / a0,
            a1 = (-2f * cos(w0)) / a0,
            a2 = (1f - alpha / a) / a0
        )
    }

    private fun lowShelf(freq: Float, gainDb: Float, sr: Float, s: Float = 1f): BandCoeff {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * Math.PI.toFloat() * freq / sr
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / 2f * sqrt((a + 1f / a) * (1f / s - 1f) + 2f)
        val a0 = (a + 1) + (a - 1) * cosw + 2 * sqrt(a) * alpha
        return BandCoeff(
            b0 = a * ((a + 1) - (a - 1) * cosw + 2 * sqrt(a) * alpha) / a0,
            b1 = 2 * a * ((a - 1) - (a + 1) * cosw) / a0,
            b2 = a * ((a + 1) - (a - 1) * cosw - 2 * sqrt(a) * alpha) / a0,
            a1 = -2 * ((a - 1) + (a + 1) * cosw) / a0,
            a2 = ((a + 1) + (a - 1) * cosw - 2 * sqrt(a) * alpha) / a0
        )
    }

    private fun highShelf(freq: Float, gainDb: Float, sr: Float, s: Float = 1f): BandCoeff {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * Math.PI.toFloat() * freq / sr
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / 2f * sqrt((a + 1f / a) * (1f / s - 1f) + 2f)
        val a0 = (a + 1) - (a - 1) * cosw + 2 * sqrt(a) * alpha
        return BandCoeff(
            b0 = a * ((a + 1) + (a - 1) * cosw + 2 * sqrt(a) * alpha) / a0,
            b1 = -2 * a * ((a - 1) + (a + 1) * cosw) / a0,
            b2 = a * ((a + 1) + (a - 1) * cosw - 2 * sqrt(a) * alpha) / a0,
            a1 = 2 * ((a - 1) - (a + 1) * cosw) / a0,
            a2 = ((a + 1) - (a - 1) * cosw - 2 * sqrt(a) * alpha) / a0
        )
    }
}
