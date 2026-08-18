package de.ilazlow.velosonic.analysis

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

const val ANALYSIS_TARGET_SAMPLE_RATE = 22_050

/** Track too short/undecodable to analyze — mirrors AnalysisError.tooShort, which iOS routes to
 *  an empty-but-persisted analysis stub rather than the failure skip-queue. */
class AnalysisTooShortException(message: String = "Audio window too short to analyze") : Exception(message)

/**
 * Loads a short mono Float32 @22050Hz analysis window for a track, ported from
 * AudioAnalysisManager.swift's `loadSamples`/`readAndConvert` — but **always via a partial HTTP
 * Range GET against the Subsonic stream endpoint**, never a local-file fast path. iOS branches
 * between a direct `AVAudioFile` read for already-downloaded tracks and a Range GET for remote
 * ones; that branch is purely a network-avoidance optimization, not a correctness requirement
 * (the decoded window is the same either way), and reading Media3's [androidx.media3.datasource.cache.SimpleCache]
 * download-cache spans directly would add real complexity for a bandwidth saving that doesn't
 * matter here — analysis is a small (~600KB), user-triggered, one-time-per-track fetch, not a
 * hot path.
 *
 * **Always fetches from byte 0**, unlike iOS's 18%-into-the-track seek heuristic — feeding
 * MediaCodec a byte range sliced from the *middle* of an elementary MP3 stream (no valid
 * leading frame/ID3 header, guessed frame-sync alignment) reliably produced a native SIGSEGV
 * inside the platform's MP3 decoder on real hardware/emulator, which no amount of Kotlin
 * try/catch can recover from since it crashes the whole process, not just this coroutine.
 * Starting from byte 0 guarantees a real, valid stream header, at the cost of always sampling
 * a track's intro rather than iOS's chosen "closer to the hook" 18% mark — for BPM/key/energy
 * feature extraction (not human listening), this doesn't meaningfully change the result and
 * isn't worth the crash risk.
 */
@Singleton
class AudioSampleLoader @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val playbackSubsonicClient: PlaybackSubsonicClient
) {
    /** Returns a mono Float32 buffer @[ANALYSIS_TARGET_SAMPLE_RATE]. Throws
     *  [AnalysisTooShortException] if fewer than 5 decoded seconds are available (matching iOS's
     *  `readFrames > fileRate * 5` guard), or any [IOException] from the network fetch. */
    suspend fun loadSamples(track: TrackEntity): FloatArray = withContext(Dispatchers.IO) {
        val config = playbackSubsonicClient.configFor(track.serverHost)
            ?: throw IOException("No server config for ${track.id}")
        // Forces a 128kbps MP3 transcode server-side regardless of the source file's real
        // codec/container (FLAC, OGG, etc.) — the plain playback stream URL passes the original
        // file through as-is when no maxBitRate/format is given, which MediaExtractor correctly
        // refuses to parse against a hardcoded ".mp3" temp file. Matches the same maxBitRate=128
        // assumption AudioAnalysisManager.swift's own Range-GET byte-offset math relies on.
        val url = SubsonicUrlBuilder.build(
            host = config.host,
            endpoint = "stream",
            username = config.username,
            token = config.token,
            salt = config.salt,
            useJson = false,
            extraParams = mapOf("id" to track.subsonicId, "maxBitRate" to "128", "format" to "mp3")
        )

        // ~37s of audio at an assumed 128kbps transcode — comfortably more than the 15s decode
        // window plus codec startup, entirely from the real start of the stream.
        val bytes = rangeGet(url, 0, 600_000L)
        if (bytes.size < 10_000) throw AnalysisTooShortException()

        val tempFile = File.createTempFile("velosonic_analysis_", ".mp3")
        try {
            tempFile.writeBytes(bytes)
            val (samples, nativeRate) = decodeToMono(tempFile, windowSeconds = 15.0)
            val resampled = AudioAnalysisMath.resampleLinear(samples, nativeRate, ANALYSIS_TARGET_SAMPLE_RATE)
            if (resampled.size < ANALYSIS_TARGET_SAMPLE_RATE * 5) throw AnalysisTooShortException()
            resampled
        } finally {
            tempFile.delete()
        }
    }

    private fun rangeGet(url: String, start: Long, end: Long): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} fetching analysis window")
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    /** Decodes [file] via MediaExtractor+MediaCodec up to [windowSeconds] of audio, downmixed to
     *  mono. Returns the mono samples at the decoder's native output rate (resampling happens
     *  separately) alongside that native rate. */
    private fun decodeToMono(file: File, windowSeconds: Double): Pair<FloatArray, Int> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) throw AnalysisTooShortException()
            extractor.selectTrack(audioTrackIndex)

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val maxSamplesWanted = (sampleRate * windowSeconds).toLong() * channelCount

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val chunks = ArrayList<ShortArray>()
            var collected = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS && collected < maxSamplesWanted) {
                if (!sawInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outBuffer = codec.getOutputBuffer(outIndex)!!
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val chunk = ShortArray(shortBuffer.remaining())
                            shortBuffer.get(chunk)
                            chunks.add(chunk)
                            collected += chunk.size
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                    }
                }
            }

            if (collected == 0L) throw AnalysisTooShortException()

            val allSamples = ShortArray(collected.toInt())
            var pos = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, allSamples, pos, chunk.size)
                pos += chunk.size
            }
            return downmixToMono(allSamples, channelCount) to sampleRate
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }

    private fun downmixToMono(samples: ShortArray, channelCount: Int): FloatArray {
        if (channelCount <= 1) return FloatArray(samples.size) { samples[it] / 32768f }
        val frames = samples.size / channelCount
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channelCount) sum += samples[i * channelCount + c] / 32768f
            mono[i] = sum / channelCount
        }
        return mono
    }

}
