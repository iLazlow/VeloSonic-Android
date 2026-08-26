package de.ilazlow.velosonic

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import dagger.hilt.android.HiltAndroidApp
import de.ilazlow.velosonic.data.datastore.LyricsSettingsStore
import de.ilazlow.velosonic.data.lyrics.LyricsSyncScheduler
import de.ilazlow.velosonic.data.network.OfflineModeInterceptor
import de.ilazlow.velosonic.data.sync.RecentPlayFreshnessScheduler
import de.ilazlow.velosonic.data.sync.SyncScheduler
import de.ilazlow.velosonic.di.ApplicationScope
import de.ilazlow.velosonic.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class VeloSonicApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Shared with [de.ilazlow.velosonic.data.storage.StorageRepository] via
     *  [de.ilazlow.velosonic.data.artwork.ImageLoaderModule] — see that module's doc comment for
     *  why this must be one single instance, not a second one built here. */
    @Inject
    lateinit var artworkDiskCache: DiskCache

    @Inject
    lateinit var playbackController: PlaybackController

    /** Same offline-mode gate as [de.ilazlow.velosonic.data.network.NetworkModule]'s shared
     *  OkHttpClient — this artwork-specific client is a deliberately separate instance (see
     *  [artworkHttpClient]'s doc comment), so it needs the interceptor wired in here too rather
     *  than inheriting it for free. */
    @Inject
    lateinit var offlineModeInterceptor: OfflineModeInterceptor

    @Inject
    lateinit var lyricsSettingsStore: LyricsSettingsStore

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Coil3's zero-config default (used everywhere `AsyncImage`/`CoverArtTile` render artwork)
     *  builds an empty [coil3.ComponentRegistry] with no explicit disk cache — network fetching
     *  and disk caching only happen if actually configured. Registering this factory (rather
     *  than relying on defaults) is what makes artwork persist to disk at all, and gives
     *  [artworkDiskCache] a concrete, sized, clearable cache the Storage settings screen can
     *  report on — not an opaque default this app has no handle to. */
    /** `serviceLoaderEnabled(false)` is deliberate: `coil3:coil-gif` (added for animated-WebP
     *  cover art, see [de.ilazlow.velosonic.ui.common.CoverArtTile]) auto-registers its
     *  [coil3.gif.AnimatedImageDecoder] globally via Coil's `ServiceLoader` mechanism the moment
     *  it's on the classpath — which would make every animated WebP autoplay everywhere,
     *  ignoring the `animatedArtworksEnabled`/`animatedAlbumGridEnabled` settings entirely.
     *  Disabling it keeps animation strictly opt-in per request (only call sites that build an
     *  `ImageRequest` with `.decoderFactory(AnimatedImageDecoder.Factory())` ever animate); the
     *  core static decoders ([coil3.decode.BitmapFactoryDecoder]) are registered unconditionally
     *  by Coil regardless of this flag, so this changes nothing about normal image loading. */
    // The zero-arg OkHttpNetworkFetcherFactory() uses a bare `OkHttpClient()` with OkHttp's
    // 10-second default timeouts — real, observed failure mode: Navidrome can serve genuinely
    // animated (multi-frame) WebP cover art, a much larger download+decode than a static image;
    // on a slow connection that legitimately doesn't finish in 10s, and there's no retry, so the
    // request just fails and the artwork silently never appears. A longer timeout gives a slow
    // transfer that's actually still progressing a real chance to finish. `by lazy` so this is
    // built once, not once per request.
    private val artworkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(offlineModeInterceptor)
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache(artworkDiskCache)
            .serviceLoaderEnabled(false)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { artworkHttpClient })) }
            .build()

    override fun onCreate() {
        // Must run before anything else touches Room/DataStore this process lifetime — see
        // BackupRepository.stageRestore's doc comment for why the swap can't happen live from
        // the Settings screen that staged it. Pure filesystem work, no Hilt/DI needed here.
        applyPendingRestoreIfPresent()

        super.onCreate()
        // WorkManager's androidx.startup auto-init is disabled in the manifest (it would run
        // before this onCreate — and before Hilt injects workerFactory above — permanently
        // locking the WorkManager singleton to the wrong factory). Initialize it here instead,
        // now that workerFactory is guaranteed to be injected.
        WorkManager.initialize(this, workManagerConfiguration)
        SyncScheduler.schedule(this)
        // Lyrics auto-sync defaults off (unlike the general library sync) — a per-track lyrics
        // lookup across a large library is real background network+battery cost, so it's an
        // explicit opt-in (Settings → Lyrics). Reactive rather than a one-shot read at startup so
        // toggling the setting mid-session actually starts/stops the periodic worker immediately,
        // not just on the next app launch.
        lyricsSettingsStore.settings
            .map { it.lyricsAutoSyncEnabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                if (enabled) LyricsSyncScheduler.schedule(this) else LyricsSyncScheduler.cancelPeriodic(this)
            }
            .launchIn(appScope)
        RecentPlayFreshnessScheduler.schedule(this)
        // Resume-on-launch: binds the playback service immediately (rather than waiting for the
        // user's first explicit command) if — and only if — a queue was actually saved last
        // session, so the mini player + last track/position are there the moment the UI renders.
        playbackController.initializeOnAppStart()
    }

    private fun applyPendingRestoreIfPresent() {
        val pendingDir = File(filesDir, "pending_restore")
        if (!pendingDir.exists()) return
        try {
            val stagedDb = File(pendingDir, "velosonic.db")
            if (stagedDb.exists()) {
                val dbFile = getDatabasePath("velosonic.db")
                for (suffix in listOf("", "-wal", "-shm")) {
                    File(dbFile.path + suffix).delete()
                }
                stagedDb.copyTo(dbFile, overwrite = true)
            }
            val stagedDatastoreDir = File(pendingDir, "datastore")
            if (stagedDatastoreDir.exists()) {
                val liveDatastoreDir = File(filesDir, "datastore").apply { mkdirs() }
                stagedDatastoreDir.listFiles()?.forEach { it.copyTo(File(liveDatastoreDir, it.name), overwrite = true) }
            }
        } finally {
            pendingDir.deleteRecursively()
        }
    }
}
