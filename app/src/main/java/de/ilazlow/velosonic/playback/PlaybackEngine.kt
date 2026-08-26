package de.ilazlow.velosonic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import de.ilazlow.velosonic.MainActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.android.gms.cast.framework.CastContext
import de.ilazlow.velosonic.data.LibraryRepository
import de.ilazlow.velosonic.data.ServerRepository
import de.ilazlow.velosonic.data.datastore.EqSettingsStore
import de.ilazlow.velosonic.data.datastore.PlaybackSettings
import de.ilazlow.velosonic.data.datastore.PlaybackSettingsStore
import de.ilazlow.velosonic.data.datastore.StorageSettingsStore
import de.ilazlow.velosonic.data.debug.LogManager
import de.ilazlow.velosonic.data.download.DownloadCacheProvider
import de.ilazlow.velosonic.data.download.stableCacheKeyFactory
import de.ilazlow.velosonic.data.datastore.PlaybackState
import de.ilazlow.velosonic.data.datastore.PlaybackStateStore
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.TrackDao
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.CoverArtUrlResolver
import de.ilazlow.velosonic.data.network.OfflineModeGate
import de.ilazlow.velosonic.data.network.SubsonicUrlBuilder
import de.ilazlow.velosonic.data.playback.PlaybackSubsonicClient
import de.ilazlow.velosonic.data.playback.ScrobbleQueue
import de.ilazlow.velosonic.data.playlist.PlaylistSubsonicClient
import de.ilazlow.velosonic.data.sync.compositeId
import de.ilazlow.velosonic.domain.supportsOpenSubsonicExtensions
import de.ilazlow.velosonic.domain.supportsReportPlayback
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.max
import kotlin.math.pow

private const val TAG = "PlaybackEngine"
private const val POSITION_SAVE_INTERVAL_MS = 15_000L
private const val NOW_PLAYING_PING_INTERVAL_MS = 60_000L
private const val SCROBBLE_MAX_SECONDS = 240
private const val CONTINUOUS_MIX_FETCH_SIZE = 20

data class NowPlaying(
    val track: TrackEntity? = null,
    val radioStation: RadioStationEntity? = null,
    val radioStreamTitle: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<TrackEntity> = emptyList(),
    val currentIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Whether the current track's stream bytes are present in the evictable stream cache (not
     *  an explicit download — see [de.ilazlow.velosonic.data.download.DownloadRepository] for
     *  that) — backs the player pill's Streaming/Cached distinction. Recomputed once per track
     *  transition, not every position tick — see [PlaybackEngine]'s own doc comment on why. */
    val isCurrentTrackCached: Boolean = false,
    /** True while a Google Cast session is actively receiving playback (see [PlaybackEngine]'s
     *  [CastPlayer] wrapper) — EQ, ReplayGain, crossfade, and live ICY radio titles all no-op
     *  while this is true, since none of them have a Cast-receiver-side equivalent. */
    val isCasting: Boolean = false,
    /** The connected Cast receiver's friendly name (e.g. "Living Room TV"), non-null only while
     *  [isCasting] is true — drives the "Casting to …" indicator in the Player/mini-player. */
    val castDeviceName: String? = null
) {
    val isPlayingRadio: Boolean get() = radioStation != null
}

/**
 * Owns the ExoPlayer(s), the MediaSession, and every playback-adjacent side effect (scrobbling,
 * continuous-mix queue extension, ReplayGain, radio ICY titles, crossfade, state persistence) —
 * ports AudioPlayerManager.swift's core, minus the platform-exclusive pieces (SharePlay, Apple
 * Watch handoff, Music Haptics, Apple Animated Artwork — see the port plan's won't-port list)
 * and minus Workout Mode (heart-rate-driven track selection needs Health Connect + a paired
 * wearable; there's no sensor to even test it against, and it's a large feature in its own
 * right — deferred to its own phase rather than guessed at blind).
 *
 * Gapless is native ExoPlayer playlist behavior (no manual dual-player preload bookkeeping
 * needed, unlike AVPlayer). Crossfade is NOT native to ExoPlayer, so it's ported closer to
 * iOS's own approach: a second, temporary ExoPlayer is faded in while the main one fades out,
 * then promoted to replace it (including reassigning [MediaSession.setPlayer]) — crossfade is
 * disabled while shuffle is on, since the "obvious next track" lookup assumes queue's own
 * sequential order, not ExoPlayer's internal shuffle order.
 *
 * Lives for as long as [VeloSonicPlaybackService] does; the service owns this engine's lifecycle
 * (created in onCreate, released in onDestroy) and exposes [mediaSession] via onGetSession.
 */
class PlaybackEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val trackDao: TrackDao,
    private val subsonicClient: PlaybackSubsonicClient,
    private val continuousMixResolver: ContinuousMixResolver,
    private val playbackStateStore: PlaybackStateStore,
    private val playbackSettingsStore: PlaybackSettingsStore,
    private val downloadCacheProvider: DownloadCacheProvider,
    private val eqSettingsStore: EqSettingsStore,
    private val storageSettingsStore: StorageSettingsStore,
    private val logManager: LogManager,
    private val offlineModeGate: OfflineModeGate,
    private val scrobbleQueue: ScrobbleQueue,
    private val libraryRepository: LibraryRepository,
    private val serverRepository: ServerRepository,
    private val playlistSubsonicClient: PlaylistSubsonicClient,
    private val coverArtUrlResolver: CoverArtUrlResolver
) {
    /** Every playback log line, in both Logcat and [LogManager] (in-app viewer, gated on the
     *  user's own logging toggle) — one call instead of two at every site. */
    private fun log(message: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, message, error) else Log.i(TAG, message)
        logManager.write("[Playback] $message")
    }
    /** Cache limit is read once, synchronously, the first time [cache] is actually accessed
     *  (i.e. once per process lifetime) — Media3's [LeastRecentlyUsedCacheEvictor] takes its
     *  byte limit at construction with no live-resize API, so a limit changed in Settings takes
     *  effect on the next app start, not immediately. Documented in the Storage settings screen
     *  itself rather than pretending this is instant. */
    private val cache: SimpleCache by lazy {
        val limitMb = runBlocking { storageSettingsStore.settings.first().cacheLimitMb }
        SimpleCache(
            File(context.cacheDir, "media3_stream_cache"),
            LeastRecentlyUsedCacheEvictor(limitMb * 1024L * 1024L)
        )
    }

    /** A real, modern OkHttp client — deliberately NOT [androidx.media3.datasource.DefaultHttpDataSource],
     *  which is backed by Android's own bundled/legacy `HttpURLConnection` stack. Confirmed live via
     *  full response-header logging against an Icecast/Shoutcast radio stream: that platform stack
     *  silently drops every `icy-*` response header (icy-metaint included) while every other header
     *  from the exact same response survives — Media3 already sends the `Icy-MetaData: 1` request
     *  header unconditionally on every load (see ExtractingLoadable's ICY_METADATA_HEADERS), so the
     *  request side was never the problem. A dedicated client, not the app's shared API `OkHttpClient`
     *  (Retrofit/auth interceptors have no business anywhere near raw media byte streaming). */
    private val streamingOkHttpClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    private val httpDataSourceFactory: OkHttpDataSource.Factory by lazy {
        OkHttpDataSource.Factory(streamingOkHttpClient)
    }

    private val streamCacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(OfflineGatedDataSourceFactory(httpDataSourceFactory, offlineModeGate))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Set immediately before every [playRadio] call, cleared by [playQueue] — lets
     *  [radioBypassDataSourceFactory] recognize a radio station's own request and route it past
     *  both cache layers (see that factory's doc comment for why caching a live stream breaks ICY
     *  metadata, confirmed live: a station's stream got cached at http/https response-header
     *  granularity even though `Accept-Ranges: none`/no `Content-Length` marks it as a live,
     *  effectively infinite resource — once *anything* was cached for that URL, replaying it
     *  became a cache hit with no live HTTP response at all, so `IcyHeaders.parse(dataSource.
     *  getResponseHeaders())` in Media3 could never see `icy-metaint` again for that station,
     *  regardless of request headers). */
    private var activeRadioStreamUrl: String? = null

    /** Bypasses [cacheDataSourceFactory]/[streamCacheDataSourceFactory] entirely for whichever
     *  request matches [activeRadioStreamUrl] — every other request (regular Navidrome track
     *  streams) goes through the normal cached path unchanged. See [activeRadioStreamUrl]'s doc
     *  comment for why a live radio stream must never be cached at all, not even the evictable
     *  stream cache. */
    private val radioBypassDataSourceFactory: DataSource.Factory by lazy {
        DataSource.Factory {
            RadioBypassDataSource(
                cached = cacheDataSourceFactory.createDataSource(),
                uncached = OfflineGatedDataSourceFactory(httpDataSourceFactory, offlineModeGate).createDataSource(),
                isBypassUri = { uri -> uri.toString() == activeRadioStreamUrl }
            )
        }
    }

    /** Reads from the permanent download cache first (see [DownloadCacheProvider]), falling
     *  through to the evictable stream cache/network for anything not downloaded — offline
     *  playback for downloaded tracks falls out of this for free, with no separate "is this
     *  downloaded, use a local file:// URI instead" branch needed anywhere else in this class:
     *  every MediaItem still just points at the normal stream URL.
     *
     *  `setCacheWriteDataSinkFactory(null)` is deliberate: this factory must only ever *read*
     *  from [DownloadCacheProvider.downloadCache], never write to it. Without it, every plain
     *  stream (not just an actual user-initiated download) gets duplicated into the permanent,
     *  NoOpCacheEvictor'd download cache as it passes through — the permanent cache is meant to
     *  be populated exclusively by [DownloadCacheProvider]'s own `DownloadManager`, not by
     *  casual playback. */
    private val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        CacheDataSource.Factory()
            .setCache(downloadCacheProvider.downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setCacheKeyFactory(stableCacheKeyFactory)
            .setUpstreamDataSourceFactory(streamCacheDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // Crossfade briefly runs two ExoPlayers' audio concurrently, so each needs its own EQ
    // processor instance — a shared one's per-channel biquad history would corrupt across two
    // interleaved streams. Both stay in sync by observing the same [EqSettingsStore]. Since
    // `player`/`crossfadePlayer` swap roles on every crossfade (the promoted player keeps
    // whichever processor it was built with), track processor-by-role via [playerEqProcessor]
    // rather than assuming "player" always means processor A — otherwise a second crossfade
    // could hand the same live processor instance to two concurrently-active players.
    private val eqProcessorA = EqAudioProcessor()
    private val eqProcessorB = EqAudioProcessor()

    private var playerEqProcessor: EqAudioProcessor = eqProcessorA
    private var crossfadePlayerEqProcessor: EqAudioProcessor? = null

    private var player: ExoPlayer = buildPlayer(playerEqProcessor)
    private var crossfadePlayer: ExoPlayer? = null
    private var crossfadeJob: Job? = null
    private var tickerJob: Job? = null

    private var settings = PlaybackSettings()
    private var currentReplayGain: Float = 1.0f
    private var hasScrobbledCurrent = false
    private var lastPositionSaveMs = 0L
    private var lastNowPlayingPingMs = 0L
    private var isExtendingQueue = false
    private var isInstantMixActive = false
    private var currentAlbumId: String? = null
    private var currentPlaylistId: String? = null
    private var currentTrackCached = false

    private var queue: List<TrackEntity> = emptyList()

    /** Media3's default [androidx.media3.datasource.cache.CacheKeyFactory] (used by
     *  [streamCacheDataSourceFactory], deliberately not [stableCacheKeyFactory] — that one's only
     *  for the permanent download cache) keys purely off the request URI string, so re-deriving
     *  today's stream URL for this track is the same key ExoPlayer itself used while fetching it.
     *  A non-empty span set means *some* of the track's bytes are already local — good enough for
     *  a Streaming-vs-Cached distinction without needing to know the exact content length. */
    /** Not [track.serverHost]_[track.subsonicId] by coincidence — [CacheDataSource] rebuilds
     *  each `DataSpec` it opens with `.key` set to whatever its own [CacheKeyFactory] computed,
     *  before ever handing that (now-keyed) DataSpec to its upstream. [cacheDataSourceFactory]
     *  (the one actually wired to playback) sits in front of [streamCacheDataSourceFactory] and
     *  is the one with [stableCacheKeyFactory] configured — so by the time a request reaches
     *  [cache], it's already carrying that stable key, not the raw stream URL. Building the query
     *  key any other way (e.g. the full stream URL — confirmed live: a track that really was
     *  cached still reported "Streaming") looks up the wrong bucket and silently finds nothing. */
    /** False on any device with no functioning Google Play Services — see [castContext]'s doc
     *  comment. Gates whether the UI shows a Cast button at all, since [buildCastPlayer] already
     *  falls back to plain local playback either way. */
    val isCastAvailable: Boolean get() = castContext != null

    fun isTrackCached(track: TrackEntity): Boolean {
        val streamUrl = subsonicClient.streamUrlFor(track.serverHost, track.subsonicId) ?: return false
        val key = stableCacheKeyFactory.buildCacheKey(DataSpec(Uri.parse(streamUrl)))
        return cache.getCachedSpans(key).isNotEmpty()
    }

    /** Tapping the media notification's body (not the transport buttons) launches this — without
     *  it, Media3's default notification has no content intent at all and tapping it does
     *  nothing. `FLAG_ACTIVITY_NEW_TASK` is required since this fires from outside any Activity
     *  context (the notification/system triggers it, not an Activity); `FLAG_ACTIVITY_SINGLE_TOP`
     *  plus the manifest's own `launchMode="singleTop"` on MainActivity keep this from spawning a
     *  duplicate instance if it's already running. */
    private val sessionActivityPendingIntent: PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Null (and Cast permanently off for this process) on any device without functioning Google
     *  Play Services (some tablets/Chromebooks, de-Googled ROMs, emulators without the Play Store
     *  image) — [CastContext.getSharedInstance] throws rather than returning null in that case, so
     *  every call site treats a null [castContext] (or a null [buildCastPlayer] result) as "Cast is
     *  simply unavailable," never a crash. */
    private val castContext: CastContext? = try {
        CastContext.getSharedInstance(context)
    } catch (e: Exception) {
        log("Cast unavailable on this device: ${e.message}")
        null
    }

    private val castSessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            val deviceName = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
            log("Cast session available: $deviceName")
            _nowPlaying.update { it.copy(isCasting = true, castDeviceName = deviceName) }
        }
        override fun onCastSessionUnavailable() {
            log("Cast session unavailable")
            _nowPlaying.update { it.copy(isCasting = false, castDeviceName = null) }
        }
    }

    /** Wraps [localPlayer] in a fresh [CastPlayer] so [mediaSession] can handle both local and
     *  remote (Cast) playback through one [Player] instance, per Media3 1.9+'s recommended pattern
     *  ([CastPlayer.Builder.setLocalPlayer]). Confirmed against the Media3 source that [CastPlayer]
     *  has no API to swap its wrapped local player after construction — the wrapper must be rebuilt
     *  from scratch on every local-player swap ([promoteCrossfadedPlayer]'s crossfade promotion is
     *  currently the only such site), not just reassigned via `mediaSession.player = ...` like the
     *  raw-[ExoPlayer] swap this replaces. This is safe specifically because crossfade is disabled
     *  while actively casting (see [maybeScheduleCrossfade]'s `isCasting` guard) — the rebuild only
     *  ever happens while [castPlayer] is wrapping local-only playback, never mid-remote-session. */
    private fun buildCastPlayer(localPlayer: ExoPlayer): CastPlayer? {
        // Presence check only — CastPlayer.Builder takes the plain Context (it resolves its own
        // CastContext internally), but we still gate on castContext here so a device where
        // CastContext.getSharedInstance() already failed once never attempts this at all.
        if (castContext == null) return null
        return try {
            CastPlayer.Builder(context).setLocalPlayer(localPlayer).build().also {
                it.setSessionAvailabilityListener(castSessionAvailabilityListener)
            }
        } catch (e: Exception) {
            log("Failed to build CastPlayer: ${e.message}", e)
            null
        }
    }

    private var castPlayer: CastPlayer? = buildCastPlayer(player)

    /** The player every command and every state read must go through — [CastPlayer] implements
     *  the full [Player] interface and internally decides whether a call actually reaches the
     *  local [player] or the remote Cast receiver, based on whether a session is connected (see
     *  [castSessionAvailabilityListener]). Calling straight through to [player] anywhere outside
     *  crossfade's own local-only dual-player mechanics bypasses that routing entirely — confirmed
     *  live: doing so left [castPlayer] with an empty timeline (a picked Cast device showed the
     *  receiver's idle "Default Media Receiver" screen, no title/artist/art) while [player] kept
     *  playing out loud locally at the same time, since nothing had ever told the CastPlayer what
     *  to play. */
    private val activePlayer: Player get() = castPlayer ?: player

    /** Android Auto's browse tree — see that class's doc comment. Passed the exact same
     *  track/radio → playable-[MediaItem] builders this engine's own queue uses ([buildTrackMediaItem],
     *  [buildRadioMediaItem]), so a track tapped in Auto ends up with the identical stream URL/
     *  artwork/MIME type it would get from the phone UI. */
    private val autoLibrarySessionCallback = AutoLibrarySessionCallback(
        context = context,
        scope = scope,
        libraryRepository = libraryRepository,
        serverRepository = serverRepository,
        playlistSubsonicClient = playlistSubsonicClient,
        coverArtUrlResolver = coverArtUrlResolver,
        resolveTrackMediaItem = ::buildTrackMediaItem,
        resolveRadioMediaItem = ::buildRadioMediaItem
    )

    /** [MediaLibrarySession] (not a plain [MediaSession]) is what makes this session's content
     *  browsable at all — Android Auto (and Assistant/Wear/any MediaBrowser) discovers a browse
     *  tree only through this subtype's [androidx.media3.session.MediaLibraryService.MediaLibrarySession.Callback],
     *  which a plain `MediaSession.Callback` has no equivalent for. Built around [activePlayer]
     *  exactly like the plain-MediaSession version this replaces — Cast keeps working unchanged. */
    val mediaSession: MediaLibrarySession = MediaLibrarySession.Builder(context, activePlayer, autoLibrarySessionCallback)
        .setSessionActivity(sessionActivityPendingIntent)
        .build()

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    // ── Public playback control ────────────────────────────────────────────────

    fun playQueue(
        tracks: List<TrackEntity>,
        startIndex: Int,
        albumId: String? = null,
        playlistId: String? = null,
        instantMix: Boolean = false
    ) {
        if (tracks.isEmpty()) return
        cancelCrossfade()
        isInstantMixActive = instantMix
        currentAlbumId = albumId
        currentPlaylistId = playlistId
        queue = tracks
        _nowPlaying.update { it.copy(radioStation = null, radioStreamTitle = null) }
        activeRadioStreamUrl = null
        val items = tracks.map { it.toMediaItem() }
        activePlayer.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0)
        activePlayer.prepare()
        activePlayer.play()
        emitState()
    }

    fun playTrack(track: TrackEntity) = playQueue(listOf(track), 0)

    fun playInstantMix(track: TrackEntity) = playQueue(listOf(track), 0, instantMix = true)

    fun playRadio(station: RadioStationEntity) {
        cancelCrossfade()
        queue = emptyList()
        currentAlbumId = null
        currentPlaylistId = null
        val item = buildRadioMediaItem(station)
        _nowPlaying.update { it.copy(radioStation = station, radioStreamTitle = null, track = null, queue = emptyList()) }
        activeRadioStreamUrl = station.streamUrl
        activePlayer.setMediaItem(item)
        activePlayer.prepare()
        activePlayer.play()
    }

    /** Shared by [playRadio] and [AutoLibrarySessionCallback] (Android Auto's browse tree resolves
     *  a tapped radio station leaf through this same builder, so both surfaces agree on artwork/
     *  MIME type). Subsonic's internet-radio artwork convention prefixes the cover-art id with
     *  "ra-" — a station's own `coverArt` value isn't a valid `getCoverArt` id by itself (mirrors
     *  iOS's `"ra-\(station.id)"`; see RadioScreen.kt, the UI-side call site using the same
     *  convention). Without this the OS media notification/lock-screen showed no artwork at all
     *  for radio, even for stations that do have cover art. */
    internal fun buildRadioMediaItem(station: RadioStationEntity): MediaItem {
        val config = station.coverArt?.let { subsonicClient.configFor(station.serverHost) }
        val artworkUrl = config?.let {
            SubsonicUrlBuilder.build(
                host = it.host, endpoint = "getCoverArt", username = it.username,
                token = it.token, salt = it.salt, useJson = false,
                extraParams = mapOf("id" to "ra-${station.subsonicId}", "size" to "600")
            )
        }
        return MediaItem.Builder()
            .setMediaId("radio_${station.id}")
            .setUri(station.streamUrl)
            // No per-station format is known ahead of time (Subsonic doesn't expose one for
            // internet radio) — MP3 is the overwhelmingly common Icecast/Shoutcast default, and a
            // wrong guess only matters while actually casting a station (local ExoPlayer sniffs
            // content regardless of this value).
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.name)
                    .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
            )
            .build()
    }

    fun togglePlayPause() {
        if (activePlayer.playbackState == Player.STATE_IDLE) activePlayer.prepare()
        if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
    }

    fun seekTo(positionMs: Long) = activePlayer.seekTo(positionMs)

    fun skipToNext() = activePlayer.seekToNextMediaItem()

    fun skipToPrevious() {
        if (activePlayer.currentPosition > 3_000) activePlayer.seekTo(0) else activePlayer.seekToPreviousMediaItem()
    }

    fun jumpTo(index: Int) {
        if (index in queue.indices) activePlayer.seekTo(index, 0)
    }

    fun toggleShuffle() {
        activePlayer.shuffleModeEnabled = !activePlayer.shuffleModeEnabled
        emitState()
    }

    fun cycleRepeatMode() {
        activePlayer.repeatMode = when (activePlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        emitState()
    }

    fun insertPlayNext(track: TrackEntity) {
        val insertAt = (activePlayer.currentMediaItemIndex + 1).coerceAtMost(activePlayer.mediaItemCount)
        activePlayer.addMediaItem(insertAt, track.toMediaItem())
        queue = queue.toMutableList().apply { add(insertAt.coerceAtMost(size), track) }
        emitState()
        persistQueueState()
    }

    /** Removes one track from the queue by position — no-ops for an out-of-range index. Removing
     *  the currently-playing item is fine; ExoPlayer advances to whatever's next (or stops if the
     *  queue is now empty) on its own. */
    fun removeAt(index: Int) {
        if (index !in queue.indices) return
        activePlayer.removeMediaItem(index)
        queue = queue.toMutableList().apply { removeAt(index) }
        emitState()
        persistQueueState()
    }

    /** Reorders the queue (drag-to-reorder in the Up Next screen) — mirrors the position change
     *  on both the app-level [queue] list and the player's own media-item list so they never
     *  drift apart. */
    fun moveItem(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        activePlayer.moveMediaItem(from, to)
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        emitState()
        persistQueueState()
    }

    fun toggleFavoriteForCurrentTrack() {
        val track = _nowPlaying.value.track ?: return
        val config = subsonicClient.configFor(track.serverHost) ?: return
        scope.launch {
            if (track.isStarred) subsonicClient.unstar(config, track.subsonicId)
            else subsonicClient.star(config, track.subsonicId)
        }
    }

    fun clearQueue() {
        cancelCrossfade()
        activePlayer.clearMediaItems()
        queue = emptyList()
        currentAlbumId = null
        currentPlaylistId = null
        currentTrackCached = false
        _nowPlaying.value = NowPlaying()
        scope.launch { playbackStateStore.clear() }
    }

    fun release() {
        // Last chance to capture an accurate position — must run before player.release() below,
        // since persistQueueState() reads player state synchronously and ExoPlayer forbids
        // touching a released player at all.
        if (queue.isNotEmpty()) persistQueueState()
        tickerJob?.cancel()
        crossfadeJob?.cancel()
        crossfadePlayer?.release()
        activePlayer.removeListener(mainPlayerListener)
        player.release()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        mediaSession.release()
        cache.release()
    }

    // ── Player instance & media items ──────────────────────────────────────────

    /** [eqProcessor] is injected via a custom [DefaultRenderersFactory.buildAudioSink] override —
     *  the standard Media3 hook point for a PCM [androidx.media3.common.audio.AudioProcessor],
     *  equivalent to how iOS attaches its `MTAudioProcessingTap`. */
    private fun buildPlayer(eqProcessor: EqAudioProcessor): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessors(arrayOf(eqProcessor))
                    .build()
        }
        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(radioBypassDataSourceFactory))
            .build()
    }

    /** Not private: [AutoLibrarySessionCallback] (Android Auto's browse tree) resolves a tapped
     *  track leaf through this exact same builder, so both surfaces build an identical playable
     *  [MediaItem] (same stream URL, artwork, MIME type) for the same track. */
    internal fun TrackEntity.toMediaItem(): MediaItem {
        val config = subsonicClient.configFor(serverHost)
        val streamUrl = subsonicClient.streamUrlFor(serverHost, subsonicId) ?: ""
        val artworkUrl = coverArt?.let { artId ->
            config?.let {
                SubsonicUrlBuilder.build(
                    host = it.host, endpoint = "getCoverArt", username = it.username,
                    token = it.token, salt = it.salt, useJson = false,
                    extraParams = mapOf("id" to artId, "size" to "600")
                )
            }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUrl)
            // Unlike local ExoPlayer (which sniffs content), CastPlayer needs the MIME type set
            // explicitly on the MediaItem to know how to ask the receiver to play it.
            .setMimeType(contentType ?: MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artistName)
                    .setAlbumTitle(albumName)
                    .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
            )
            .build()
    }

    /** Plain wrapper around the member-extension [TrackEntity.toMediaItem] — [AutoLibrarySessionCallback]
     *  lives outside this class, so it can't invoke a member extension function directly. */
    internal fun buildTrackMediaItem(track: TrackEntity): MediaItem = track.toMediaItem()

    // ── State emission ──────────────────────────────────────────────────────────

    private fun emitState() {
        val idx = activePlayer.currentMediaItemIndex.coerceIn(0, max(queue.size - 1, 0))
        val current = queue.getOrNull(idx)
        _nowPlaying.update {
            it.copy(
                track = current,
                isPlaying = activePlayer.isPlaying,
                isBuffering = activePlayer.playbackState == Player.STATE_BUFFERING,
                positionMs = activePlayer.currentPosition.coerceAtLeast(0),
                durationMs = activePlayer.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L,
                queue = queue,
                currentIndex = idx,
                shuffleEnabled = activePlayer.shuffleModeEnabled,
                repeatMode = activePlayer.repeatMode,
                isCurrentTrackCached = currentTrackCached
            )
        }
    }

    private inline fun MutableStateFlow<NowPlaying>.update(block: (NowPlaying) -> NowPlaying) {
        value = block(value)
    }

    // ── Player listener: transitions, metadata (ICY / ReplayGain), errors ──────

    private val mainPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            emitState()
            reportServerState(if (isPlaying) "playing" else "paused")
            if (isPlaying) {
                startTicker()
            } else {
                tickerJob?.cancel()
                // The ticker (the only other thing that saves position) only runs while playing —
                // without this, pausing and then killing the app could lose up to
                // POSITION_SAVE_INTERVAL_MS of resume accuracy, or (if paused right after a track
                // transition, before the ticker ever ran once) the entire position within the track.
                persistQueueState()
            }
        }

        /** Buffering can start/finish independent of play/pause (ExoPlayer proactively loads
         *  enough to reach STATE_READY even while paused, e.g. right after a cold-start restore's
         *  `setMediaItems`+`prepare()` with no `play()` call) — without this, [emitState] was only
         *  ever re-run from [onIsPlayingChanged]/[onMediaItemTransition], so `NowPlaying.isBuffering`
         *  stayed stuck true (the player pill showing "Loading…" forever) until the user pressed
         *  Play, even though the player had already finished buffering and was just sitting there
         *  paused and ready. Confirmed live: restoring a saved queue on launch left the pill on
         *  "Loading…" indefinitely; iOS re-syncs this state immediately instead. */
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (_nowPlaying.value.isCasting) {
                // Diagnostic for the intermittent "Default Media Receiver" placeholder report —
                // ties a buffering->ready transition (the window a not-yet-cached/slow-to-buffer
                // track sits in longest) to what CastPlayer itself is reporting as the current
                // item's metadata at that exact moment, so a real repro's Logcat can show whether
                // the metadata was ever actually missing at the SDK level, or only in how/when the
                // phone's own notification picked it up.
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> playbackState.toString()
                }
                val md = activePlayer.mediaMetadata
                log("Cast playbackState=$stateName activePlayer.title=${md.title} activePlayer.artist=${md.artist} currentTrackCached=$currentTrackCached")
            }
            emitState()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            if (_nowPlaying.value.isCasting) {
                log("Cast onMediaMetadataChanged: title=${mediaMetadata.title} artist=${mediaMetadata.artist} albumTitle=${mediaMetadata.albumTitle}")
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // CastPlayer's local->remote handoff fires a transient PLAYLIST_CHANGED transition
            // with a synthetic, metadata-less MediaItem (confirmed live: title/artist/mediaId all
            // null) and currentMediaItemIndex defaulting to 0, seconds before the receiver's real
            // MediaStatus arrives and a second, correct transition follows. Every real MediaItem
            // this app ever builds (toMediaItem()/buildRadioMediaItem()) always sets a title, so a
            // null one here is unambiguously that phantom event, not an actual track change —
            // treating it as real was resetting scrobble state and (worse) reporting "starting"
            // playback to the server for whatever track happened to be at index 0, and showing
            // that track's metadata in the app's own Player UI until the real transition arrived.
            if (mediaItem?.mediaMetadata?.title == null) {
                if (_nowPlaying.value.isCasting) log("Cast onMediaItemTransition: ignoring phantom transition (null mediaItem/title) during Cast handoff")
                return
            }
            hasScrobbledCurrent = false
            lastPositionSaveMs = 0L
            lastNowPlayingPingMs = 0L
            currentReplayGain = 1.0f
            player.volume = currentReplayGain
            val idx = activePlayer.currentMediaItemIndex.coerceIn(0, max(queue.size - 1, 0))
            val track = queue.getOrNull(idx)
            currentTrackCached = track?.let(::isTrackCached) ?: false
            track?.let { log("Now playing '${it.title}' by ${it.artistName} (cached=$currentTrackCached)") }
            if (_nowPlaying.value.isCasting) {
                val reasonName = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    else -> reason.toString()
                }
                log("Cast onMediaItemTransition reason=$reasonName resolvedTrack=${track?.title} mediaItem.title=${mediaItem?.mediaMetadata?.title} activePlayer.title=${activePlayer.mediaMetadata.title}")
            }
            emitState()
            persistQueueState()
            reportServerState("starting")
            maybePrefetchContinuousMix()
        }

        override fun onMetadata(metadata: Metadata) {
            // ICY radio titles and ReplayGain tags both come from the local ExoPlayer's own
            // decode pipeline, which a CastPlayer bypasses entirely while remote — neither has a
            // Cast-receiver-side equivalent, so both stay at their last local value (radio falls
            // back to the station's static name via the existing radioStreamTitle ?: station.name
            // chain) instead of silently going stale mid-cast-session.
            if (_nowPlaying.value.isCasting) return
            for (i in 0 until metadata.length()) {
                when (val entry = metadata.get(i)) {
                    is IcyInfo -> {
                        // Many Icecast/Shoutcast stations send a literal `StreamTitle='';` (an
                        // empty, non-null string) between tracks or when the station just doesn't
                        // tag now-playing info — normalized to null here so every consumer's
                        // `radioStreamTitle ?: station.name` fallback chain actually falls through
                        // instead of rendering a blank title.
                        _nowPlaying.update { it.copy(radioStreamTitle = entry.title?.takeIf(String::isNotBlank)) }
                    }
                    is TextInformationFrame -> {
                        if (entry.description?.uppercase() == "REPLAYGAIN_TRACK_GAIN" || entry.id == "TXXX") {
                            parseGainDb(entry.values.firstOrNull())?.let { applyReplayGain(it) }
                        }
                    }
                    is VorbisComment -> {
                        if (entry.key.uppercase() == "REPLAYGAIN_TRACK_GAIN") {
                            parseGainDb(entry.value)?.let { applyReplayGain(it) }
                        }
                    }
                    else -> Unit
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            log("Player error: ${error.message}", error)
        }
    }

    init {
        activePlayer.addListener(mainPlayerListener)
        scope.launch { playbackSettingsStore.settings.collect { settings = it } }
        scope.launch {
            eqSettingsStore.settings.collect { eq ->
                val gainsArray = eq.gains.toFloatArray()
                for (processor in listOf(eqProcessorA, eqProcessorB)) {
                    processor.enabled = eq.enabled
                    processor.gains = gainsArray
                }
            }
        }
        restoreSavedQueue()
    }

    private fun parseGainDb(raw: String?): Float? =
        raw?.replace("dB", "", ignoreCase = true)?.trim()?.toFloatOrNull()

    private fun applyReplayGain(gainDb: Float) {
        if (!settings.replayGainEnabled) return
        val linear = 10.0.pow(gainDb / 20.0).toFloat()
        currentReplayGain = linear.coerceIn(0.001f, 4.0f)
        player.volume = currentReplayGain
        log("ReplayGain: ${gainDb}dB -> x$currentReplayGain")
    }

    // ── Periodic ticker: scrobble threshold, position save, now-playing ping ──

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                delay(1_000)
                if (!activePlayer.isPlaying) continue
                tick()
            }
        }
    }

    private fun tick() {
        // Positional UI state (progress bar) has no dedicated push channel from ExoPlayer —
        // piggyback the 1s tick to keep it live instead of only updating on discrete events.
        emitState()
        val track = _nowPlaying.value.track ?: run { maybeScheduleCrossfade(); return }
        val positionMs = activePlayer.currentPosition
        val durationMs = track.duration * 1000L

        if (settings.scrobblingEnabled && !hasScrobbledCurrent) {
            val threshold = (durationMs * settings.scrobbleThreshold).toLong()
            if (positionMs >= threshold || positionMs >= SCROBBLE_MAX_SECONDS * 1000L) {
                hasScrobbledCurrent = true
                scrobbleCurrent(track, positionMs)
            }
        }

        if (positionMs - lastPositionSaveMs >= POSITION_SAVE_INTERVAL_MS) {
            lastPositionSaveMs = positionMs
            persistQueueState()
        }

        if (positionMs - lastNowPlayingPingMs >= NOW_PLAYING_PING_INTERVAL_MS) {
            lastNowPlayingPingMs = positionMs
            reportServerState("playing", positionMs)
            savePlayQueueToServer(track, positionMs)
        }

        maybeScheduleCrossfade()
    }

    private fun scrobbleCurrent(track: TrackEntity, positionMs: Long) {
        val config = subsonicClient.configFor(track.serverHost) ?: return
        scope.launch {
            scrobbleQueue.scrobbleOrQueue(config, track.subsonicId, submission = true)
            if (config.supportsReportPlayback) {
                subsonicClient.reportPlayback(config, track.subsonicId, positionMs.toInt(), "stopped")
            }
        }
    }

    private fun reportServerState(state: String, positionMs: Long = activePlayer.currentPosition) {
        val track = _nowPlaying.value.track ?: return
        val config = subsonicClient.configFor(track.serverHost) ?: return
        scope.launch {
            if (config.supportsReportPlayback) {
                subsonicClient.reportPlayback(config, track.subsonicId, positionMs.toInt(), state)
            } else if (state == "playing" || state == "starting") {
                scrobbleQueue.scrobbleOrQueue(config, track.subsonicId, submission = false)
            }
        }
    }

    private fun savePlayQueueToServer(track: TrackEntity, positionMs: Long) {
        val config = subsonicClient.configFor(track.serverHost) ?: return
        if (!config.supportsOpenSubsonicExtensions) return
        scope.launch {
            subsonicClient.savePlayQueue(config, queue.map { it.subsonicId }, track.subsonicId, positionMs.toInt())
        }
    }

    // ── Queue persistence (resume-on-launch) ───────────────────────────────────

    /** Reads every player-derived value synchronously before launching the actual DataStore
     *  write — [release] calls this right before tearing the player down, and ExoPlayer forbids
     *  touching a released player at all, so nothing here can be deferred into the coroutine
     *  body (which could run after `player.release()` has already happened). */
    private fun persistQueueState() {
        val idx = activePlayer.currentMediaItemIndex
        val positionMs = activePlayer.currentPosition
        val trackIds = queue.map { it.id }
        val albumId = currentAlbumId
        val playlistId = currentPlaylistId
        scope.launch {
            playbackStateStore.save(
                PlaybackState(
                    queueTrackIds = trackIds,
                    currentIndex = idx,
                    positionMs = positionMs,
                    albumId = albumId,
                    playlistId = playlistId
                )
            )
        }
    }

    private fun restoreSavedQueue() {
        scope.launch {
            val saved = playbackStateStore.current()
            if (saved.queueTrackIds.isEmpty()) return@launch
            // trackDao.getByIds is a suspend DB round-trip — a real command (user tapped a track
            // right after a cold start, before this restore finished) can land while it's still
            // in flight. Re-check right before writing to the player, not just at the top of this
            // function, or the restore clobbers whatever the user actually asked to play with
            // stale state (confirmed live: tapping a track moments after launch briefly played it,
            // then silently switched to whatever was playing when the app was last closed).
            val fetched = trackDao.getByIds(saved.queueTrackIds).associateBy { it.id }
            val ordered = saved.queueTrackIds.mapNotNull { fetched[it] }
            if (ordered.isEmpty() || queue.isNotEmpty()) return@launch
            queue = ordered
            currentAlbumId = saved.albumId
            currentPlaylistId = saved.playlistId
            val items = ordered.map { it.toMediaItem() }
            val startIndex = saved.currentIndex.coerceIn(0, items.lastIndex)
            activePlayer.setMediaItems(items, startIndex, saved.positionMs)
            activePlayer.prepare()
            emitState()
        }
    }

    /** Mirrors iOS's `AudioPlayerManager.loadQueueFromServer` — pulls whatever queue/position was
     *  last saved server-side via [PlaybackSubsonicClient.savePlayQueue] (by this device or
     *  another OpenSubsonic client) and loads it here, paused, same as [restoreSavedQueue]'s
     *  "restore is decoupled from playback" behavior. Triggered only by the user (Home's toolbar
     *  action) — there's no auto-load on launch, that's [restoreSavedQueue]'s job from local
     *  state, which is instant and doesn't need a round-trip. */
    suspend fun loadQueueFromServer(host: String) {
        val config = subsonicClient.configFor(host) ?: return
        if (!config.supportsOpenSubsonicExtensions) return
        val node = subsonicClient.getPlayQueue(config) ?: return
        val entries = node.entry.orEmpty()
        if (entries.isEmpty()) return
        val ids = entries.map { compositeId(host, it.id) }
        val fetched = trackDao.getByIds(ids).associateBy { it.id }
        val ordered = ids.mapNotNull { fetched[it] }
        if (ordered.isEmpty()) return
        val startIndex = node.current
            ?.let { current -> ordered.indexOfFirst { it.subsonicId == current } }
            ?.takeIf { it >= 0 } ?: 0
        queue = ordered
        currentAlbumId = null
        currentPlaylistId = null
        val items = ordered.map { it.toMediaItem() }
        activePlayer.setMediaItems(items, startIndex, (node.position ?: 0).toLong())
        activePlayer.prepare()
        emitState()
    }

    // ── Continuous mix (queue auto-extension) ──────────────────────────────────

    private fun maybePrefetchContinuousMix() {
        val cp = settings.continuousPlaybackEnabled || isInstantMixActive
        if (!cp || activePlayer.repeatMode != Player.REPEAT_MODE_OFF) return
        if (queue.isEmpty() || activePlayer.currentMediaItemIndex + 1 < activePlayer.mediaItemCount || isExtendingQueue) return
        val track = _nowPlaying.value.track ?: return
        val config = subsonicClient.configFor(track.serverHost) ?: return
        isExtendingQueue = true
        scope.launch {
            val excludeIds = queue.map { it.id }.toSet()
            val next = continuousMixResolver.fetchNextBatch(
                settings.continuousMixMode, config, track, excludeIds, settings.sonicSimilarSongsEnabled
            ).take(CONTINUOUS_MIX_FETCH_SIZE)
            if (next.isNotEmpty()) {
                queue = queue + next
                // Reverted from an earlier full-setMediaItems-reload "fix" — real Logcat evidence
                // (see the git history/PR discussion around this line) showed that a full reload
                // is what actually causes the Cast receiver's own MediaStatus to briefly go blank
                // (a PLAYLIST_CHANGED event), which the Google Home app's own now-playing card then
                // displays as its generic "ExoPlayer Default Receiver" fallback — and that a plain
                // incremental addMediaItems() append (queueInsertItems on the Cast SDK side) never
                // triggers that: every normal AUTO track transition to an already-queued item in
                // the same log showed onMediaMetadataChanged arriving cleanly ahead of the
                // transition, no gap at all. The only confirmed source of the null-metadata window
                // is the local->remote handoff itself (once, at Cast session start) — not something
                // an incremental append should ever reproduce.
                activePlayer.addMediaItems(next.map { it.toMediaItem() })
                persistQueueState()
            }
            isExtendingQueue = false
        }
    }

    // ── Crossfade (dual-player fade + promote) ─────────────────────────────────

    private fun maybeScheduleCrossfade() {
        if (!settings.crossfadeEnabled || settings.crossfadeSeconds <= 0) return
        // Crossfade promotes a brand-new local ExoPlayer and rebuilds the CastPlayer wrapper
        // around it (see buildCastPlayer's doc comment) — never safe to do mid-remote-session.
        if (_nowPlaying.value.isCasting) return
        if (player.shuffleModeEnabled || crossfadePlayer != null) return
        val idx = player.currentMediaItemIndex
        val nextIndex = idx + 1
        if (nextIndex >= queue.size) return
        val durationMs = queue[idx].duration * 1000L
        val remainingMs = durationMs - player.currentPosition
        val triggerMs = settings.crossfadeSeconds * 1000L
        if (durationMs <= 0 || remainingMs > triggerMs || remainingMs <= 0) return
        beginCrossfade(nextIndex)
    }

    private fun beginCrossfade(nextIndex: Int) {
        val nextTrack = queue.getOrNull(nextIndex) ?: return
        val incomingEqProcessor = if (playerEqProcessor === eqProcessorA) eqProcessorB else eqProcessorA
        crossfadePlayerEqProcessor = incomingEqProcessor
        val incoming = buildPlayer(incomingEqProcessor)
        incoming.setMediaItem(nextTrack.toMediaItem())
        incoming.volume = 0f
        incoming.prepare()
        incoming.play()
        crossfadePlayer = incoming

        val outgoing = player
        val startVolume = outgoing.volume
        val durationSeconds = settings.crossfadeSeconds
        crossfadeJob = scope.launch {
            val steps = max(10, durationSeconds * 10)
            val stepMs = (durationSeconds * 1000L / steps).coerceAtLeast(10)
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                outgoing.volume = startVolume * (1f - t)
                incoming.volume = t * currentReplayGain
                delay(stepMs)
            }
            outgoing.volume = 0f
            incoming.volume = currentReplayGain
            promoteCrossfadedPlayer(incoming, nextIndex)
        }
    }

    private fun promoteCrossfadedPlayer(newPlayer: ExoPlayer, nextIndex: Int) {
        if (crossfadePlayer !== newPlayer) return
        crossfadePlayer = null
        val old = player
        // Whichever player currently owns mainPlayerListener/mediaSession — normally castPlayer,
        // since it wraps `old` and every command already routes through it (see activePlayer's
        // doc comment). Captured before any reassignment below so the detach targets the right
        // instance regardless.
        val oldSessionPlayer = activePlayer

        val remaining = queue.drop(nextIndex + 1).map { it.toMediaItem() }
        if (remaining.isNotEmpty()) newPlayer.addMediaItems(remaining)
        newPlayer.repeatMode = old.repeatMode

        old.pause()
        oldSessionPlayer.removeListener(mainPlayerListener)
        player = newPlayer
        playerEqProcessor = crossfadePlayerEqProcessor ?: playerEqProcessor
        crossfadePlayerEqProcessor = null
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = buildCastPlayer(player)
        activePlayer.addListener(mainPlayerListener)
        mediaSession.player = activePlayer
        old.release()

        hasScrobbledCurrent = false
        lastPositionSaveMs = 0L
        lastNowPlayingPingMs = 0L
        emitState()
        persistQueueState()
        reportServerState("starting")
        log("Crossfade promoted '${queue.getOrNull(nextIndex)?.title}'")
    }

    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        crossfadePlayer?.release()
        crossfadePlayer = null
        crossfadePlayerEqProcessor = null
        player.volume = currentReplayGain
    }
}
