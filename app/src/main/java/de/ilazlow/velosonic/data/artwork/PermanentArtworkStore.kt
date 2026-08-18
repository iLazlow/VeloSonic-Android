package de.ilazlow.velosonic.data.artwork

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of ArtworkManager.swift's *permanent* artwork directory
 * (`Library/Downloads/Artworks/`, distinct from its evictable `Caches/artworks/` — the latter's
 * equivalent here is Coil's own disk cache, see [de.ilazlow.velosonic.data.artwork.ImageLoaderModule]).
 * Lives alongside the downloaded audio itself (`getExternalFilesDir(null)/artwork_permanent`,
 * a sibling of [de.ilazlow.velosonic.data.download.DownloadCacheProvider]'s `downloads/`), and
 * is never touched by "Clear Artwork Cache" — only [de.ilazlow.velosonic.data.download.DownloadRepository]
 * writes to or deletes from this store, exactly mirroring a download's own lifecycle.
 *
 * **Deliberately keyed by `{serverHost}_{coverArtId}`, not iOS's bare `{coverArtId}`** — iOS
 * has a real (if unlikely, since Navidrome ids are content hashes) collision risk across
 * multiple servers sharing the same cover-art id; since this port already treats multi-server
 * correctness as load-bearing everywhere else (e.g. the download cache's own stable key), the
 * same host-scoping is applied here too rather than replicating a gap that's cheap to avoid.
 */
@Singleton
class PermanentArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val directory: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "artwork_permanent").apply { mkdirs() }

    private fun fileFor(serverHost: String, coverArtId: String): File =
        File(directory, "${cacheKey(serverHost, coverArtId)}.jpg")

    private fun cacheKey(serverHost: String, coverArtId: String): String {
        val safeHost = serverHost.replace(Regex("[^A-Za-z0-9]"), "_").trim('_')
        return "${safeHost}_${sanitizedArtworkId(coverArtId)}"
    }

    private fun animatedFileFor(serverHost: String, coverArtId: String): File =
        File(directory, "${cacheKey(serverHost, coverArtId)}_animated.mp4")

    /** A `file://` URI if this cover art has already been persisted, else null. */
    fun localUriFor(serverHost: String, coverArtId: String): String? {
        val file = fileFor(serverHost, coverArtId)
        return if (file.exists() && file.length() > 0) Uri.fromFile(file).toString() else null
    }

    /** A `file://` URI for the permanent animated-artwork MP4 (see
     *  [de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository]), else null. */
    fun localAnimatedUriFor(serverHost: String, coverArtId: String): String? {
        val file = animatedFileFor(serverHost, coverArtId)
        return if (file.exists() && file.length() > 0) Uri.fromFile(file).toString() else null
    }

    /** Copies an already-exported MP4 (from [AnimatedArtworkRepository]'s evictable cache) into
     *  the permanent store — no-ops if already present. */
    suspend fun persistAnimatedIfNeeded(serverHost: String, coverArtId: String, sourceFile: File) = withContext(Dispatchers.IO) {
        val dest = animatedFileFor(serverHost, coverArtId)
        if (dest.exists() && dest.length() > 0) return@withContext
        if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext
        try {
            sourceFile.copyTo(dest, overwrite = true)
        } catch (e: Exception) {
            dest.delete()
        }
    }

    /** No-ops if already persisted — safe to call on every download-completion event without
     *  re-fetching. Deletes a partial file on failure rather than leaving a zero-byte entry that
     *  [localUriFor] would otherwise treat as present (it checks length() > 0 defensively too,
     *  but cleaning up here avoids an empty file lingering on disk at all). */
    suspend fun persistIfNeeded(serverHost: String, coverArtId: String, remoteUrl: String) = withContext(Dispatchers.IO) {
        val file = fileFor(serverHost, coverArtId)
        if (file.exists() && file.length() > 0) return@withContext
        try {
            val request = Request.Builder().url(remoteUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext
                val body = response.body ?: return@withContext
                file.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
        } catch (e: Exception) {
            file.delete()
        }
    }

    fun delete(serverHost: String, coverArtId: String) {
        fileFor(serverHost, coverArtId).delete()
        animatedFileFor(serverHost, coverArtId).delete()
    }
}
