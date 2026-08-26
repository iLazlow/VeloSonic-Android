package de.ilazlow.velosonic.playback

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.datastore.EqSettingsStore
import de.ilazlow.velosonic.data.datastore.PlaybackSettingsStore
import de.ilazlow.velosonic.data.datastore.PlaybackStateStore
import de.ilazlow.velosonic.data.datastore.StorageSettingsStore
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.debug.LogManager
import de.ilazlow.velosonic.data.download.DownloadCacheProvider
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.OfflineModeGate
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.playback.ScrobbleQueue
import de.ilazlow.velosonic.data.playlist.PlaylistSubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

/** Distinguishes PlaybackController's own in-process bind from the system's MediaController
 *  bind (declared in the manifest with the standard androidx.media3.session.MediaSessionService
 *  action) — onBind below dispatches on this so both coexist on the one service. */
const val ACTION_LOCAL_BIND = "de.ilazlow.velosonic.playback.LOCAL_BIND"

/**
 * Thin MediaSessionService wrapper — all real logic lives in [PlaybackEngine], which this
 * service owns for exactly as long as the process keeps it alive. Relies on Media3's default
 * notification (DefaultMediaNotificationProvider) rather than a custom one — a reasonable v1
 * simplification; a branded notification icon is a cheap follow-up once this is proven to work.
 *
 * [PlaybackController] (the UI-facing façade) binds directly to this service in-process rather
 * than going through a MediaController — the richer [PlaybackEngine.nowPlaying] state (real
 * TrackEntity objects, not just MediaMetadata) doesn't cross the MediaController/Session IPC
 * boundary, and since the phone UI is a same-process, single-app UI, there's no need to pay for
 * that IPC layer just to talk to itself. The real [MediaLibrarySession] this service exposes via
 * onGetSession still handles every *external* control surface (notification, Bluetooth, lock
 * screen, Android Auto's browse tree) exactly as it would if the phone UI went through
 * MediaController too — [MediaLibraryService] (not the plain [MediaSessionService] this replaces)
 * is what makes that session's content actually browsable to Android Auto/Assistant/Wear/any
 * other MediaBrowser client, via [AutoLibrarySessionCallback].
 */
@AndroidEntryPoint
class VeloSonicPlaybackService : MediaLibraryService() {

    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var subsonicClient: PlaybackSubsonicClient
    @Inject lateinit var continuousMixResolver: ContinuousMixResolver
    @Inject lateinit var playbackStateStore: PlaybackStateStore
    @Inject lateinit var playbackSettingsStore: PlaybackSettingsStore
    @Inject lateinit var downloadCacheProvider: DownloadCacheProvider
    @Inject lateinit var eqSettingsStore: EqSettingsStore
    @Inject lateinit var storageSettingsStore: StorageSettingsStore
    @Inject lateinit var logManager: LogManager
    @Inject lateinit var offlineModeGate: OfflineModeGate
    @Inject lateinit var scrobbleQueue: ScrobbleQueue
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var serverRepository: ServerRepository
    @Inject lateinit var playlistSubsonicClient: PlaylistSubsonicClient
    @Inject lateinit var coverArtUrlResolver: CoverArtUrlResolver

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var _engine: PlaybackEngine? = null
    val engine: PlaybackEngine get() = _engine!!

    inner class LocalBinder : Binder() {
        val service: VeloSonicPlaybackService get() = this@VeloSonicPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        _engine = PlaybackEngine(
            context = this,
            scope = serviceScope,
            trackDao = trackDao,
            subsonicClient = subsonicClient,
            continuousMixResolver = continuousMixResolver,
            playbackStateStore = playbackStateStore,
            playbackSettingsStore = playbackSettingsStore,
            downloadCacheProvider = downloadCacheProvider,
            eqSettingsStore = eqSettingsStore,
            storageSettingsStore = storageSettingsStore,
            logManager = logManager,
            offlineModeGate = offlineModeGate,
            scrobbleQueue = scrobbleQueue,
            libraryRepository = libraryRepository,
            serverRepository = serverRepository,
            playlistSubsonicClient = playlistSubsonicClient,
            coverArtUrlResolver = coverArtUrlResolver
        )
        // Without this, onGetSession still lets controllers connect, but the base class never
        // attaches its MediaNotificationManager to the session — so it never calls
        // Service.startForeground() itself, and the system media notification never appears.
        addSession(engine.mediaSession)
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) LocalBinder() else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = engine.mediaSession

    /** Media3's `MediaSessionService` has no default behavior here — without this override,
     *  swiping the app away from Recents removes the task/UI but leaves the foreground playback
     *  service (and its process) running exactly as before, which is the normal, deliberate
     *  behavior most Android music players rely on. Explicitly requested to work differently here
     *  instead: swiping away should stop playback outright, same as force-quitting on iOS
     *  actually terminates the process there. Pausing before stopSelf() (rather than relying on
     *  the eventual onDestroy() teardown alone) mirrors Media3's own official sample pattern —
     *  guarantees audio actually stops immediately rather than possibly continuing for a moment
     *  while the service winds down. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        _engine?.mediaSession?.player?.pause()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        _engine?.let { removeSession(it.mediaSession) }
        _engine?.release()
        _engine = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
