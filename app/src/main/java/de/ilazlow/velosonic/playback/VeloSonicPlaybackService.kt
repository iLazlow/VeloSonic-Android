package de.ilazlow.velosonic.playback

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import de.ilazlow.velosonic.data.datastore.EqSettingsStore
import de.ilazlow.velosonic.data.datastore.PlaybackSettingsStore
import de.ilazlow.velosonic.data.datastore.PlaybackStateStore
import de.ilazlow.velosonic.data.datastore.StorageSettingsStore
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.debug.LogManager
import de.ilazlow.velosonic.data.download.DownloadCacheProvider
import de.ilazlow.velosonic.data.network.OfflineModeGate
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.playback.ScrobbleQueue
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
 * boundary, and since this is a same-process, single-app UI (not Android Auto/Wear/Assistant),
 * there's no need to pay for that IPC layer just to talk to itself. The real MediaSession this
 * service exposes via onGetSession still handles all *external* control surfaces (notification,
 * Bluetooth, lock screen) exactly as it would if the UI went through MediaController too.
 */
@AndroidEntryPoint
class VeloSonicPlaybackService : MediaSessionService() {

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
            scrobbleQueue = scrobbleQueue
        )
        // Without this, onGetSession still lets controllers connect, but the base class never
        // attaches its MediaNotificationManager to the session — so it never calls
        // Service.startForeground() itself, and the system media notification never appears.
        addSession(engine.mediaSession)
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) LocalBinder() else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = engine.mediaSession

    override fun onDestroy() {
        _engine?.let { removeSession(it.mediaSession) }
        _engine?.release()
        _engine = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
