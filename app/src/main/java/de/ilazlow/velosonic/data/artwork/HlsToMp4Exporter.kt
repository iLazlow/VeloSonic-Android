package de.ilazlow.velosonic.data.artwork

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HlsToMp4Exporter"

/** Caps a single segment download to this many bytes/second. Isolating this feature onto its own
 *  [OkHttpClient]/[Dispatcher] only prevents connection-pool contention *within* that client — it
 *  does nothing against plain bandwidth saturation at the OS/socket level, which is exactly what
 *  was observed: the static cover art's completely separate Coil request stalled for the entire
 *  export duration on a constrained connection. Throttling actual throughput is the only thing
 *  that guarantees this best-effort background feature leaves headroom for real foreground
 *  traffic, on a slow emulator link or a real, metered cellular one. */
private const val MAX_SEGMENT_BYTES_PER_SECOND = 100_000

/** Matches the exporter's own `maxRequestsPerHost` cap — no point fetching more segments
 *  concurrently than OkHttp will actually run in parallel, and keeping this small bounds peak
 *  memory usage to a couple of segments at a time instead of the whole video. */
private const val SEGMENT_BATCH_SIZE = 2

private data class HlsSegment(val url: HttpUrl, val byteRangeStart: Long?, val byteRangeLength: Long?)

/**
 * Direct port of `AudioPlayerManager.exportHLS` + its private helpers (`bestVariantURL`,
 * `parseHLSSegments`, `downloadHLSSegment`, `hlsByteRange`, attribute parsing). Apple's HLS here
 * uses `#EXT-X-MAP` + `#EXT-X-BYTERANGE` pointing into fragmented MP4 files — concatenating
 * init-segment + media-segment bytes in order produces a valid standalone fMP4 with no
 * transcoding step needed at all, exactly as the iOS source comment documents.
 */
@Singleton
class HlsToMp4Exporter @Inject constructor(
    okHttpClient: OkHttpClient
) {
    /** A background, best-effort feature must never compete with foreground traffic (cover art,
     *  the Subsonic API, the actual audio stream) for bandwidth — deriving a client with its own
     *  capped [Dispatcher] (rather than reusing the shared client's) keeps this export from
     *  saturating the connection and starving everything else while it downloads several MB of
     *  video segments in the background. This was a real, observed regression: exporting an
     *  animated cover made even the plain static artwork stall for the export's entire duration. */
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .dispatcher(Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 2 })
        .build()
    /** No-ops (returns the existing file) if [destination] already has content — safe to call
     *  repeatedly without re-fetching. Deletes a partial file and returns null on any failure. */
    suspend fun export(hlsUrl: String, destination: File): File? = withContext(Dispatchers.IO) {
        if (destination.exists() && destination.length() > 0) return@withContext destination
        try {
            val masterUrl = hlsUrl.toHttpUrlOrNull() ?: run {
                Log.w(TAG, "export: invalid master URL: $hlsUrl")
                return@withContext null
            }
            val masterText = fetchText(masterUrl) ?: run {
                Log.w(TAG, "export: failed to fetch master playlist: $masterUrl")
                return@withContext null
            }
            val variantUrl = bestVariantUrl(masterText, masterUrl) ?: run {
                Log.w(TAG, "export: no usable variant in master playlist:\n$masterText")
                return@withContext null
            }
            val variantText = fetchText(variantUrl) ?: run {
                Log.w(TAG, "export: failed to fetch variant playlist: $variantUrl")
                return@withContext null
            }
            val (initSeg, mediaSegs) = parseSegments(variantText, variantUrl)
            if (mediaSegs.isEmpty()) {
                Log.w(TAG, "export: no media segments parsed from variant playlist:\n$variantText")
                return@withContext null
            }

            // Segments are independent byte-range/whole-file fetches with no ordering dependency
            // between them, so fetching a small batch at a time concurrently (order preserved
            // when writing) is much faster than one at a time. Crucially, this writes each batch
            // to disk and releases its bytes *before* fetching the next — holding every segment
            // of a multi-MB video in memory simultaneously (the original approach) was a real,
            // observed regression: the GC/allocation pressure from tens of MB of ByteArrays
            // competed directly with Coil's own bitmap-decode allocations for the plain static
            // cover art, stalling it for as long as the export ran, on a memory-constrained
            // emulator — exactly correlated with "only happens for tracks with a video result."
            destination.parentFile?.mkdirs()
            destination.outputStream().use { out ->
                initSeg?.let { out.write(downloadSegment(it)) }
                mediaSegs.chunked(SEGMENT_BATCH_SIZE).forEach { batch ->
                    val batchBytes = coroutineScope { batch.map { async { downloadSegment(it) } }.awaitAll() }
                    batchBytes.forEach { out.write(it) }
                }
            }
            Log.d(TAG, "export: wrote ${destination.length()} bytes (${mediaSegs.size} segments) to ${destination.path}")
            destination
        } catch (e: Exception) {
            Log.w(TAG, "export: exception for $hlsUrl", e)
            destination.delete()
            null
        }
    }

    private fun fetchText(url: HttpUrl): String? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private suspend fun downloadSegment(seg: HlsSegment): ByteArray {
        val builder = Request.Builder().url(seg.url)
        val start = seg.byteRangeStart
        val length = seg.byteRangeLength
        if (start != null && length != null) {
            builder.header("Range", "bytes=$start-${start + length - 1}")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HLS segment fetch failed: ${response.code}")
            val body = response.body ?: return ByteArray(0)
            return throttledReadBytes(body.byteStream())
        }
    }

    /** Uses [delay] (suspending), never `Thread.sleep` — this whole export runs on
     *  [Dispatchers.IO], the exact same shared dispatcher Coil uses internally for every image
     *  fetch/decode ([coil3.util.ioCoroutineDispatcher] resolves to `Dispatchers.IO` too). A
     *  blocking sleep would hold a real OS thread hostage doing nothing for up to a full second at
     *  a time, competing with and starving Coil's own work on that same pool — which is exactly
     *  what caused a real, observed regression: the plain static cover art stalled for a full
     *  minute while this export's throttle sat there blocking threads instead of transferring
     *  bytes. `delay` releases the thread back to the pool for the wait instead of parking it. */
    private suspend fun throttledReadBytes(input: InputStream, maxBytesPerSecond: Int = MAX_SEGMENT_BYTES_PER_SECOND): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var bytesThisWindow = 0
        var windowStartMs = System.currentTimeMillis()
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            out.write(chunk, 0, read)
            bytesThisWindow += read
            if (bytesThisWindow >= maxBytesPerSecond) {
                val elapsed = System.currentTimeMillis() - windowStartMs
                if (elapsed < 1000) delay(1000 - elapsed)
                bytesThisWindow = 0
                windowStartMs = System.currentTimeMillis()
            }
        }
        return out.toByteArray()
    }

    /** Picks the highest-bandwidth H.264 (`avc1`) variant at or under 1080px on its longest
     *  side, falling back to any variant under that limit, then any variant at all. */
    private fun bestVariantUrl(master: String, baseUrl: HttpUrl): HttpUrl? {
        data class Variant(val bandwidth: Int, val codecs: String?, val maxDim: Int, val uri: String)

        val variants = mutableListOf<Variant>()
        val lines = master.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = line.substringAfter(":")
                val bandwidth = strAttr(attrs, "BANDWIDTH")?.toIntOrNull() ?: 0
                val codecs = strAttr(attrs, "CODECS")
                val resolution = strAttr(attrs, "RESOLUTION")
                val dims = resolution?.split("x")
                val w = dims?.getOrNull(0)?.toIntOrNull() ?: 0
                val h = dims?.getOrNull(1)?.toIntOrNull() ?: 0
                val uriLine = lines.getOrNull(i + 1)?.trim()
                if (!uriLine.isNullOrBlank() && !uriLine.startsWith("#")) {
                    variants += Variant(bandwidth, codecs, maxOf(w, h), uriLine)
                    i++
                }
            }
            i++
        }
        if (variants.isEmpty()) return null

        val h264UnderLimit = variants
            .filter { (it.codecs?.contains("avc1") == true) && it.maxDim in 1..1080 }
            .maxByOrNull { it.bandwidth }
        val anyUnderLimit = variants.filter { it.maxDim in 1..1080 }.maxByOrNull { it.bandwidth }
        val best = h264UnderLimit ?: anyUnderLimit ?: variants.maxByOrNull { it.bandwidth }
        return best?.let { baseUrl.resolve(it.uri) }
    }

    private fun parseSegments(variant: String, baseUrl: HttpUrl): Pair<HlsSegment?, List<HlsSegment>> {
        var initSeg: HlsSegment? = null
        val mediaSegs = mutableListOf<HlsSegment>()
        var nextOffset = 0L
        var pendingLength: Long? = null
        var pendingOffset: Long? = null
        var havePendingRange = false

        for (rawLine in variant.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-MAP:") -> {
                    val attrs = line.substringAfter(":")
                    val uri = strAttr(attrs, "URI") ?: continue
                    val url = baseUrl.resolve(uri) ?: continue
                    val byterange = strAttr(attrs, "BYTERANGE")
                    if (byterange != null) {
                        val (len, off) = parseByteRangeAttr(byterange)
                        val start = off ?: nextOffset
                        val length = len ?: 0L
                        initSeg = HlsSegment(url, start, length)
                        nextOffset = start + length
                    } else {
                        initSeg = HlsSegment(url, null, null)
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    val (len, off) = parseByteRangeAttr(line.substringAfter(":"))
                    pendingLength = len
                    pendingOffset = off
                    havePendingRange = true
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val url = baseUrl.resolve(line) ?: continue
                    val length = pendingLength
                    if (havePendingRange && length != null) {
                        val start = pendingOffset ?: nextOffset
                        mediaSegs += HlsSegment(url, start, length)
                        nextOffset = start + length
                    } else {
                        mediaSegs += HlsSegment(url, null, null)
                    }
                    havePendingRange = false
                    pendingLength = null
                    pendingOffset = null
                }
            }
        }
        return initSeg to mediaSegs
    }

    /** HLS byte-range syntax: `length[@offset]` — an omitted offset means "contiguous with
     *  whatever came before" (tracked by the caller's running `nextOffset`). */
    private fun parseByteRangeAttr(spec: String): Pair<Long?, Long?> {
        val cleaned = spec.trim().trim('"')
        val parts = cleaned.split("@")
        val length = parts.getOrNull(0)?.toLongOrNull()
        val offset = parts.getOrNull(1)?.toLongOrNull()
        return length to offset
    }

    private fun strAttr(attrs: String, key: String): String? {
        val match = Regex("""$key=(?:"([^"]*)"|([^,]*))""").find(attrs) ?: return null
        val value = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return value.ifBlank { null }
    }
}
