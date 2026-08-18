package de.ilazlow.velosonic.data.download

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// 1, not 3 — mirrors SyncNowWorker's sequential-chain fix for the same underlying issue: with
// several tracks downloading at once, Media3's own aggregate notification helper combines all of
// their percentages into one number that can visibly jump around as each track's own progress
// updates land at different times (confirmed live: "the progress bar jumps back and forth"). One
// download at a time makes "what's downloading and how far along is it" unambiguous, matching the
// single-clear-notification UX Sync now has.
private const val MAX_PARALLEL_DOWNLOADS = 1

/**
 * Stable cache key derived from the stream URL's `id` query param + host, ignoring the volatile
 * `u`/`t`/`s` (username/token/salt) params — mirrors iOS's `StreamingCache.cacheKey(serverHost:
 * trackId:)`. Without this, a re-login that rotates the Subsonic auth salt/token would change the
 * "same track's" stream URL and make an already-downloaded file invisible to the player, since
 * Media3's default [CacheKeyFactory] keys purely off the full request URI.
 */
val stableCacheKeyFactory = CacheKeyFactory { dataSpec ->
    val uri = dataSpec.uri
    "${uri.host}_${uri.getQueryParameter("id")}"
}

/**
 * Owns the permanent download cache (separate from [de.ilazlow.velosonic.playback.PlaybackEngine]'s
 * evictable streaming cache — a `NoOpCacheEvictor`'d cache backed by app-private *files* storage,
 * not *cache* storage, so the OS never reclaims a user's downloads under storage pressure) and the
 * Media3 [DownloadManager] that populates it. Mirrors iOS's split between a temp `Caches/streams/`
 * dir and a permanent `Library/Downloads/` dir.
 */
@Singleton
class DownloadCacheProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    private val databaseProvider = StandaloneDatabaseProvider(context)

    val downloadCache: SimpleCache = SimpleCache(
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads"),
        NoOpCacheEvictor(),
        databaseProvider
    )

    private val downloaderCacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setCacheKeyFactory(stableCacheKeyFactory)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Kept as its own writable-typed property (rather than only reachable via
     *  `downloadManager.downloadIndex`, which types it down to the read-only [DownloadIndex]) so
     *  [de.ilazlow.velosonic.data.sync.ServerMigrationManager] can rewrite a download's id/host
     *  in place — [DefaultDownloadIndex.putDownload]/`removeDownload` are pure SQLite row
     *  operations against this index table, entirely separate from [downloadCache]'s actual
     *  cached bytes (keyed independently by [stableCacheKeyFactory]), so this never touches
     *  already-downloaded audio. */
    val downloadIndex = DefaultDownloadIndex(databaseProvider)

    val downloadManager: DownloadManager = DownloadManager(
        context,
        downloadIndex,
        DefaultDownloaderFactory(downloaderCacheDataSourceFactory)
    ).apply {
        maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
        // Matches iOS's wifiOnly default (StreamingSettings.downloadNetworkPolicy) — there's no
        // settings UI to change this yet (that's a later Settings-screen phase), so this is the
        // one fixed policy for now rather than an unreachable toggle.
        requirements = Requirements(Requirements.NETWORK_UNMETERED)
    }
}
