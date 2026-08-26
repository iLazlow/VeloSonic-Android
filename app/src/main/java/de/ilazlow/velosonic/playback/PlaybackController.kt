package de.ilazlow.velosonic.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import de.ilazlow.velosonic.data.datastore.PlaybackStateStore
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI-facing façade over [VeloSonicPlaybackService] — binds to it in-process (see the service's
 * doc comment for why this skips MediaController) and re-exposes [PlaybackEngine.nowPlaying]
 * plus its command methods. Every command silently no-ops until the bind completes.
 *
 * The bind is kicked off in one of two ways: eagerly, once, via [initializeOnAppStart] — called
 * from [de.ilazlow.velosonic.VeloSonicApp] — *only if* a queue was actually saved from last time,
 * so the mini player and the resumed track/position are already there the moment the UI first
 * renders, matching iOS's own "restore is decoupled from playback" behavior; or lazily, on first
 * real command, for a session that never had anything saved. Either way a session with truly
 * nothing to restore and no command issued still never pays for starting the service.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val playbackStateStore: PlaybackStateStore
) {
    // ExoPlayer enforces "only touched from the thread it was created on" (main) — every
    // command must run here, never on appScope's Default dispatcher (confirmed the hard way:
    // the fallback branch below crashed with "Player is accessed on the wrong thread" the
    // first time a command landed before the service bind had completed).
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var engine: PlaybackEngine? = null
    private val engineReady = MutableStateFlow(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? VeloSonicPlaybackService.LocalBinder)?.service ?: return
            engine = service.engine
            engineReady.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engine = null
            engineReady.value = false
        }
    }

    private var bound = false

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val nowPlaying: StateFlow<NowPlaying> = engineReady.flatMapLatest { ready ->
        if (ready) engine?.nowPlaying ?: MutableStateFlow(NowPlaying()) else MutableStateFlow(NowPlaying())
    }.stateIn(appScope, SharingStarted.WhileSubscribed(5_000), NowPlaying())

    /** Plain bind only — [VeloSonicPlaybackService] promotes itself to a foreground service (and
     *  posts the system media notification) internally, via Media3's own notification manager,
     *  the moment playback actually starts (see [VeloSonicPlaybackService.onCreate]'s `addSession`
     *  call). Calling `ContextCompat.startForegroundService` from here too was tried first and
     *  reverted — it fires at bind time, which can be well before (or without) any actual
     *  playback, e.g. [initializeOnAppStart] restoring a saved-but-paused queue at launch; Android
     *  requires `Service.startForeground()` within a few seconds of that call, and since nothing
     *  was actually about to play, that promise went unfulfilled and the OS killed the app with
     *  an ANR ("Context.startForegroundService() did not then call Service.startForeground()"). */
    private fun ensureBound() {
        if (bound) return
        bound = true
        val intent = Intent(context, VeloSonicPlaybackService::class.java).setAction(ACTION_LOCAL_BIND)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /** Called once from [de.ilazlow.velosonic.VeloSonicApp.onCreate] — binds immediately if (and
     *  only if) a queue was saved from last session, so [PlaybackEngine]'s own `restoreSavedQueue`
     *  (run from its `init` block, the moment the bind completes) populates [nowPlaying] before
     *  any screen has a chance to render, without unconditionally starting the service for a
     *  session that has nothing to resume. */
    fun initializeOnAppStart() {
        appScope.launch {
            if (playbackStateStore.current().queueTrackIds.isNotEmpty()) {
                mainScope.launch { ensureBound() }
            }
        }
    }

    private fun withEngine(block: (PlaybackEngine) -> Unit) {
        ensureBound()
        val current = engine
        if (current != null) {
            block(current)
        } else {
            mainScope.launch {
                // Wait for the bind to complete — first command after a cold start.
                var attempts = 0
                while (engine == null && attempts < 50) {
                    delay(50)
                    attempts++
                }
                engine?.let(block)
            }
        }
    }

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int, albumId: String? = null, playlistId: String? = null) =
        withEngine { it.playQueue(tracks, startIndex, albumId, playlistId) }

    fun playTrack(track: TrackEntity) = withEngine { it.playTrack(track) }

    fun playInstantMix(track: TrackEntity) = withEngine { it.playInstantMix(track) }

    fun playRadio(station: RadioStationEntity) = withEngine { it.playRadio(station) }

    fun togglePlayPause() = withEngine { it.togglePlayPause() }

    fun seekTo(positionMs: Long) = withEngine { it.seekTo(positionMs) }

    fun skipToNext() = withEngine { it.skipToNext() }

    fun skipToPrevious() = withEngine { it.skipToPrevious() }

    fun jumpTo(index: Int) = withEngine { it.jumpTo(index) }

    fun toggleShuffle() = withEngine { it.toggleShuffle() }

    fun cycleRepeatMode() = withEngine { it.cycleRepeatMode() }

    fun insertPlayNext(track: TrackEntity) = withEngine { it.insertPlayNext(track) }

    fun toggleFavoriteForCurrentTrack() = withEngine { it.toggleFavoriteForCurrentTrack() }

    fun clearQueue() = withEngine { it.clearQueue() }

    /** Synchronous, read-only — unlike every command above, there's nothing to queue up if the
     *  service isn't bound yet (no `ensureBound()` call): a track row just shows no cached
     *  indicator until playback has actually started at least once this session, same as every
     *  other bit of [nowPlaying]-adjacent state being empty pre-bind. */
    fun isTrackCached(track: TrackEntity): Boolean = engine?.isTrackCached(track) ?: false

    /** Same read-only, no-`ensureBound()` shape as [isTrackCached] — the Cast button just stays
     *  hidden pre-bind rather than triggering a service start on its own. */
    val isCastAvailable: Boolean get() = engine?.isCastAvailable ?: false

    fun removeAt(index: Int) = withEngine { it.removeAt(index) }

    fun moveItem(from: Int, to: Int) = withEngine { it.moveItem(from, to) }

    /** Suspend, unlike every other command here — the caller (Home's "load queue from server"
     *  action) shows a spinner for the duration of the round-trip, so it needs to actually know
     *  when this finishes rather than firing and forgetting like [withEngine]'s commands do. */
    suspend fun loadQueueFromServer(host: String) {
        ensureBound()
        var current = engine
        if (current == null) {
            var attempts = 0
            while (engine == null && attempts < 50) {
                delay(50)
                attempts++
            }
            current = engine
        }
        current?.loadQueueFromServer(host)
    }
}
