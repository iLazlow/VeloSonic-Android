package de.ilazlow.velosonic.data.storage

import android.content.Context
import coil3.disk.DiskCache
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository
import de.ilazlow.velosonic.data.artwork.StaticArtworkFrameCache
import de.ilazlow.velosonic.data.download.DownloadCacheProvider
import de.ilazlow.velosonic.data.download.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors StorageSettingsView.swift's size reporting/clear actions: evictable stream cache,
 * permanent downloads, and the evictable artwork cache (Coil's [DiskCache], shared via
 * [de.ilazlow.velosonic.data.artwork.ImageLoaderModule] — see that module's doc comment).
 * Deliberately excludes [de.ilazlow.velosonic.data.artwork.PermanentArtworkStore] entirely —
 * that store is tied 1:1 to downloads and is never independently sized/cleared from here, same
 * as iOS's `Downloads/Artworks` having no button of its own outside of full account logout.
 */
@Singleton
class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadCacheProvider: DownloadCacheProvider,
    private val downloadRepository: DownloadRepository,
    private val artworkDiskCache: DiskCache,
    private val animatedArtworkRepository: AnimatedArtworkRepository,
    private val staticArtworkFrameCache: StaticArtworkFrameCache
) {
    private val streamCacheDir get() = File(context.cacheDir, "media3_stream_cache")

    suspend fun streamCacheSizeBytes(): Long = withContext(Dispatchers.IO) { directorySize(streamCacheDir) }

    fun downloadsSizeBytes(): Long = downloadCacheProvider.downloadCache.cacheSpace

    /** Coil's evictable disk cache (static + animated-WebP artwork), the evictable exported
     *  animated-MP4 cache (see [AnimatedArtworkRepository]), and the evictable static-first-frame
     *  cache (see [StaticArtworkFrameCache]) — one combined "artwork cache" bucket, same as iOS
     *  treating its WebP and Apple-animated-video caches as a single `artworks` dir. */
    fun artworkCacheSizeBytes(): Long =
        artworkDiskCache.size + animatedArtworkRepository.cacheSizeBytes() + staticArtworkFrameCache.cacheSizeBytes()

    fun clearArtworkCache() {
        artworkDiskCache.clear()
        animatedArtworkRepository.clearCache()
        staticArtworkFrameCache.clearCache()
    }

    /** Best-effort direct file delete — Media3's [androidx.media3.datasource.cache.SimpleCache]
     *  has no live "clear" API safe to call while a [de.ilazlow.velosonic.playback.PlaybackEngine]
     *  instance might have it open, so this works most reliably when nothing is actively
     *  streaming; the Storage screen says as much rather than silently implying otherwise. */
    suspend fun clearStreamCache() = withContext(Dispatchers.IO) {
        streamCacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Removing tracked [DownloadRepository] entries alone can leave bytes behind: any content
     *  that ever landed in [DownloadCacheProvider.downloadCache] without a matching download-index
     *  entry (e.g. residue from a since-fixed bug that let plain streaming write into this cache)
     *  has no [de.ilazlow.velosonic.data.download.DownloadRepository.removeDownload] call that
     *  would ever touch it. Directly purging every cache key after the tracked removal guarantees
     *  this actually zeroes out what [downloadsSizeBytes] reports, not just what the index knows
     *  about. */
    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        downloadRepository.removeDownloads(downloadRepository.downloads.value.keys.toList())
        val cache = downloadCacheProvider.downloadCache
        cache.keys.toList().forEach { cache.removeResource(it) }
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
