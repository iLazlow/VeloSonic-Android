package de.ilazlow.velosonic.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** WebP orders chunks as VP8X, then optional ICC/EXIF/XMP, then ANIM — comfortably covers real
 *  files without ever needing to fetch anywhere near a full animated image just to sniff it. */
private const val ANIMATION_SNIFF_BYTES = 4096

/**
 * Direct port of `ArtworkManager.swift`'s `generateStaticJPG` — Navidrome can serve genuinely
 * animated (multi-frame) WebP cover art, and every non-animated surface (list rows, mini player,
 * grid cells with animation off) has no business re-downloading and re-decoding that whole file
 * every time it just wants a still image. iOS solves this by generating a static first-frame JPEG
 * once per artwork and pointing every non-animated view at that instead of the raw WebP; this is
 * the same idea, keyed by the resolved cover-art URL itself rather than a separate artworkId,
 * since [de.ilazlow.velosonic.ui.common.CoverArtTile] only ever sees the final URL.
 *
 * This was a real, observed bug: an animated-WebP cover art request could take 60+ seconds (or
 * outright time out) on a slow connection, and that cost was being paid on *every single view* —
 * mini player, track rows, grid cells — not just the one detail screen that actually wants the
 * animation.
 */
@Singleton
class StaticArtworkFrameCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val directory: File get() = File(context.cacheDir, "artwork_static_frame").apply { mkdirs() }

    private fun cacheKey(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fileFor(url: String): File = File(directory, "${cacheKey(url)}.jpg")

    fun localUriFor(url: String): String? {
        val file = fileFor(url)
        return if (file.exists() && file.length() > 0) Uri.fromFile(file).toString() else null
    }

    fun cacheSizeBytes(): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearCache() {
        directory.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Fire-and-forget on the application scope — [de.ilazlow.velosonic.ui.common.CoverArtTile]
     *  calls this from a `LaunchedEffect`, but a lazy-list row can be disposed (scrolled away)
     *  before the fetch finishes; running the actual work here rather than in the caller's own
     *  scope means generation still completes and gets cached for the next view either way. */
    fun scheduleGeneration(url: String) {
        if (localUriFor(url) != null) return
        appScope.launch { ensureGenerated(url) }
    }

    /** No-ops instantly if already cached/not animated, otherwise downloads+decodes+caches a
     *  static frame. Exposed as `suspend` (rather than folded entirely into [scheduleGeneration])
     *  so a caller that's still alive when this finishes — e.g. the full player, not a lazy-list
     *  row — can await it directly and upgrade to the fast local copy within the same view,
     *  instead of only benefiting the next time this artwork is shown.
     *
     *  Peeks only [ANIMATION_SNIFF_BYTES] up front rather than downloading the full body first —
     *  the overwhelming majority of covers are plain static images, and doubling every single
     *  cover-art fetch app-wide just to answer "is this animated" would be a worse regression
     *  than the one this class exists to fix. The full body is only read once that peek actually
     *  finds an animation marker. */
    suspend fun ensureGenerated(url: String) = withContext(Dispatchers.IO) {
        val file = fileFor(url)
        if (file.exists() && file.length() > 0) return@withContext
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val body = response.body ?: return@withContext
                val source = body.source()
                source.request(ANIMATION_SNIFF_BYTES.toLong())
                val prefixSize = minOf(ANIMATION_SNIFF_BYTES.toLong(), source.buffer.size)
                val prefix = source.buffer.copy().readByteArray(prefixSize)
                if (!isLikelyAnimated(prefix)) return@withContext
                val bytes = source.readByteArray()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
                try {
                    file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            file.delete()
        }
    }

    /** Cheap magic-byte sniff — GIF is inherently frame-based (any GIF is worth the static-frame
     *  treatment), WebP is only worth it if it actually contains an `ANIM` chunk (the marker
     *  every animated WebP has and no static one does); ordinary static covers (the overwhelming
     *  majority) are left alone since Coil's own disk cache already serves those efficiently. */
    private fun isLikelyAnimated(bytes: ByteArray): Boolean {
        if (bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) {
            return true
        }
        if (bytes.size < 16) return false
        val isWebp = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        if (!isWebp) return false
        val anim = byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'I'.code.toByte(), 'M'.code.toByte())
        var i = 12
        while (i <= bytes.size - anim.size) {
            if (bytes[i] == anim[0] && bytes[i + 1] == anim[1] && bytes[i + 2] == anim[2] && bytes[i + 3] == anim[3]) {
                return true
            }
            i++
        }
        return false
    }
}
