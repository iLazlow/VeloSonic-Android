package de.ilazlow.velosonic.ui.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download as DownloadState
import coil3.compose.AsyncImage
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.db.ArtistEntry
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.dto.SimilarArtistDto
import de.ilazlow.velosonic.data.sync.compositeId
import de.ilazlow.velosonic.ui.common.DetailArtwork
import de.ilazlow.velosonic.ui.common.FullBleedBackdrop
import de.ilazlow.velosonic.ui.common.trackStatusLabel
import de.ilazlow.velosonic.ui.playlists.AddToPlaylistSheet
import de.ilazlow.velosonic.ui.share.ShareSheet
import de.ilazlow.velosonic.ui.share.ShareTarget
import dev.kawarp.KawarpEngine

/**
 * Full-screen "now playing" — mirrors PlayerMainContent/PlayerControls: the current artwork
 * stretched full-bleed and blurred behind everything (see FullBleedBackdrop; falls back to a
 * flat black fill when there's no artwork at all, e.g. a radio station), a square artwork that
 * scales down slightly while paused, a compact centered transport cluster, a secondary action
 * row, and a Similar Songs strip. Presented as a real overlay sheet by AppShell, not a pushed nav
 * destination. Dismissal is a real drag-down across the whole sheet, snapping back below a
 * threshold.
 */
@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val similarSongs by viewModel.similarSongs.collectAsStateWithLifecycle()
    val similarArtists by viewModel.similarArtists.collectAsStateWithLifecycle()
    val artistBiography by viewModel.artistBiography.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsSparklesEnabled by viewModel.lyricsSparklesEnabled.collectAsStateWithLifecycle()
    val animatedVideoUri by viewModel.animatedVideoUri.collectAsStateWithLifecycle()
    val animateWebpArtwork by viewModel.animateWebpArtwork.collectAsStateWithLifecycle()
    val kawarpSettings by viewModel.kawarpSettings.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val otherRadioStations by viewModel.otherRadioStations.collectAsStateWithLifecycle()
    val track = nowPlaying.track

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    // Drag state for dismissal — declared here (not down where the gesture is attached) so it can
    // also drive the OUTER sheet Box's own translation below, not just the inner content: mirrors
    // iOS's real UISheetPresentationController drag, where the whole sheet (backdrop included)
    // slides down together and the app content underneath becomes visible through the gap, rather
    // than a fixed full-screen background with only the scrollable content sliding within it.
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val offsetY = remember { Animatable(0f) }
    // Reading synced lyrics is a hands-off, screen-watching activity — mirrors iOS keeping the
    // display on for its lyrics view unconditionally, independent of Settings → Playback → Keep
    // Screen Awake (that toggle governs the player generally; someone leaving it off to save
    // battery during normal playback still doesn't want the screen dimming mid-lyric).
    val lyricsView = LocalView.current
    DisposableEffect(showLyrics) {
        lyricsView.keepScreenOn = showLyrics
        onDispose { lyricsView.keepScreenOn = false }
    }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showFullBio by remember { mutableStateOf(false) }

    val artworkScale by animateFloatAsState(if (nowPlaying.isPlaying) 1f else 0.85f, label = "artwork-scale")

    // A radio station's artwork isn't looked up by its own `coverArt` id directly — Subsonic's
    // internet-radio artwork convention prefixes it with "ra-" (mirrors iOS's
    // `"ra-\(station.id)"`; see RadioScreen.kt, the other call site using this same convention).
    val artUrl = track?.let { viewModel.coverArtUrl(it.serverHost, it.coverArt, 1000) }
        ?: nowPlaying.radioStation?.takeIf { it.coverArt != null }
            ?.let { viewModel.coverArtUrl(it.serverHost, "ra-${it.subsonicId}", 1000) }

    // Factored into a lambda (instead of inline) so the exact same composable content can be
    // placed either inside the fixed lyrics-mode hero Column or inside the single scrollable
    // Column used otherwise — see the layout Column below for why there are two different
    // wrappers around the same content.
    val heroContent: @Composable ColumnScope.() -> Unit = {
        DragHandle()

        if (showLyrics) {
            // Mirrors PlayerMainContent.swift's `showLyrics` branch: a compact header (small
            // thumbnail + title/artist + heart/menu, with its own chevron-down to close lyrics)
            // sits ABOVE the lyrics content instead of the full-size artwork+title block below —
            // not the same title row reused in place of the artwork like the non-lyrics case.
            if (track != null) {
                PlayerCompactHeader(
                    track = track,
                    thumbnailUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt, 300),
                    onDismissLyrics = { showLyrics = false },
                    isDownloaded = downloads[track.id]?.state == DownloadState.STATE_COMPLETED,
                    showOverflowMenu = showOverflowMenu,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShowOverflowMenu = { showOverflowMenu = true },
                    onDismissOverflowMenu = { showOverflowMenu = false },
                    onArtistClick = { entry -> viewModel.artistRouteId(track, entry)?.let { onArtistClick(it, entry.name) } },
                    onAlbumClick = { track.albumCompositeId?.let(onAlbumClick) },
                    onAddToPlaylist = { showAddToPlaylist = true },
                    onShowInfo = { showSongInfo = true },
                    onDownload = viewModel::downloadCurrentTrack
                )
            }

            // No rounded/translucent card here — iOS's lyrics view sits directly over the
            // blurred backdrop with no card chrome of its own, unlike the artwork box below.
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                PlayerLyricsView(
                    state = lyricsState,
                    positionMs = nowPlaying.positionMs,
                    isPlaying = nowPlaying.isPlaying,
                    onSeek = viewModel::seekToLyricLine,
                    sparklesEnabled = lyricsSparklesEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .aspectRatio(1f)
                    .scale(artworkScale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (artUrl != null) {
                    DetailArtwork(
                        staticUrl = artUrl,
                        videoUri = animatedVideoUri,
                        contentDescription = track?.title,
                        animateWebp = animateWebpArtwork,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: nowPlaying.radioStreamTitle ?: nowPlaying.radioStation?.name.orEmpty(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (track?.albumCompositeId != null) {
                            Modifier.clickable { onAlbumClick(track.albumCompositeId) }
                        } else {
                            Modifier
                        }
                    )
                    if (track != null) {
                        ArtistRow(track = track, onArtistClick = { entry -> viewModel.artistRouteId(track, entry)?.let { onArtistClick(it, entry.name) } })
                    } else {
                        // The big title above already prefers the live ICY stream title, falling
                        // back to the station name — so once a stream title is playing, this
                        // subtitle switches to the station name instead (matching iOS: subtitle
                        // is whichever of {station name, homepage, "Live Stream"} isn't already
                        // shown as the title).
                        val station = nowPlaying.radioStation
                        val subtitle = if (nowPlaying.radioStreamTitle != null) {
                            station?.name.orEmpty()
                        } else {
                            station?.homePageUrl?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_live_stream)
                        }
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (track != null) {
                    PlayerActionButtonsCluster(
                        track = track,
                        isDownloaded = downloads[track.id]?.state == DownloadState.STATE_COMPLETED,
                        showOverflowMenu = showOverflowMenu,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onShowOverflowMenu = { showOverflowMenu = true },
                        onDismissOverflowMenu = { showOverflowMenu = false },
                        onArtistClick = { entry -> viewModel.artistRouteId(track, entry)?.let { onArtistClick(it, entry.name) } },
                        onAlbumClick = { track.albumCompositeId?.let(onAlbumClick) },
                        onAddToPlaylist = { showAddToPlaylist = true },
                        onShowInfo = { showSongInfo = true },
                        onDownload = viewModel::downloadCurrentTrack
                    )
                } else if (nowPlaying.isPlayingRadio) {
                    val homePageUrl = nowPlaying.radioStation?.homePageUrl?.takeIf { it.isNotBlank() }
                    val context = LocalContext.current
                    IconButton(
                        onClick = { homePageUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
                        enabled = homePageUrl != null,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = stringResource(R.string.player_website_content_description),
                            tint = Color.White.copy(alpha = if (homePageUrl != null) 0.7f else 0.25f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!nowPlaying.isPlayingRadio) {
            val durationMs = nowPlaying.durationMs.coerceAtLeast(1L)
            val liveFraction = (nowPlaying.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val displayFraction = if (isScrubbing) scrubFraction else liveFraction

            PlayerProgressBar(
                fraction = displayFraction,
                isScrubbing = isScrubbing,
                onScrubStart = { isScrubbing = true; scrubFraction = liveFraction },
                onScrub = { scrubFraction = it },
                onScrubEnd = { f ->
                    viewModel.seekTo((f * durationMs).toLong())
                    isScrubbing = false
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMs(if (isScrubbing) (scrubFraction * durationMs).toLong() else nowPlaying.positionMs),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (track != null) {
                    PlayerQualityPill(
                        track = track,
                        isDownloaded = downloads[track.id]?.state == DownloadState.STATE_COMPLETED,
                        isCached = nowPlaying.isCurrentTrackCached,
                        isBuffering = nowPlaying.isBuffering
                    )
                }
                Text(
                    text = formatMs(nowPlaying.durationMs),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Mirrors iOS's radio-mode status row: a permanently-full, non-interactive bar
            // (seeking a live stream isn't meaningful) — no quality pill (Radiant/bitrate info
            // doesn't apply to a live stream either), "LIVE" instead of an elapsed-time label, an
            // infinity symbol instead of a duration, and a "Loading" indicator that only appears
            // while the stream is (re)buffering.
            PlayerProgressBar(
                fraction = 1f,
                isScrubbing = false,
                onScrubStart = {},
                onScrub = {},
                onScrubEnd = {},
                enabled = false
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFE53935)))
                    Text(text = stringResource(R.string.player_live_label), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (nowPlaying.isBuffering) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White.copy(alpha = 0.7f))
                        Text(text = stringResource(R.string.player_buffering_loading), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
                Text(
                    text = stringResource(R.string.player_infinite_duration),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle/prev/next/repeat are all meaningless for a live stream (there's no queue
            // to shuffle or reorder through) — dimmed and non-interactive during radio playback,
            // mirroring iOS rendering them as plain (non-Button) images at 0.25 opacity rather
            // than merely graying out a still-tappable control.
            val transportEnabled = !nowPlaying.isPlayingRadio
            // Gaps are weighted, not fixed dp, so they compress on a narrow width instead of
            // forcing a fixed minimum row width — fixed Spacers here previously pushed this row's
            // total width past the screen edge on narrow layouts (e.g. a foldable's cover screen),
            // since nothing in a plain fixed-Spacer Row can shrink to fit. The five icon buttons
            // stay fixed-size (there's no meaningful smaller size for a tap target); only the
            // spacing between them flexes, keeping the roughly 1:1.6 side/center gap ratio the
            // original fixed 20dp/32dp Spacers had.
            TransportIconButton(onClick = viewModel::toggleShuffle, size = 44.dp, enabled = transportEnabled) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = null,
                    tint = if (!transportEnabled) Color.White.copy(alpha = 0.25f)
                        else if (nowPlaying.shuffleEnabled) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TransportIconButton(onClick = viewModel::skipToPrevious, size = 44.dp, enabled = transportEnabled) {
                Icon(Icons.Filled.FastRewind, contentDescription = null, tint = Color.White.copy(alpha = if (transportEnabled) 1f else 0.25f), modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.weight(1.6f))
            TransportIconButton(onClick = viewModel::togglePlayPause, size = 60.dp) {
                Icon(
                    imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1.6f))
            TransportIconButton(onClick = viewModel::skipToNext, size = 44.dp, enabled = transportEnabled) {
                Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White.copy(alpha = if (transportEnabled) 1f else 0.25f), modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            TransportIconButton(onClick = viewModel::cycleRepeatMode, size = 44.dp, enabled = transportEnabled) {
                Icon(
                    imageVector = if (nowPlaying.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = null,
                    tint = if (!transportEnabled) Color.White.copy(alpha = 0.25f)
                        else if (nowPlaying.repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (nowPlaying.isPlayingRadio && otherRadioStations.isNotEmpty()) {
            MoreRadioStationsSection(
                stations = otherRadioStations,
                currentStationId = nowPlaying.radioStation?.id,
                coverArtUrl = viewModel::coverArtUrl,
                onStationClick = viewModel::playRadioStation
            )
        }

        // Cast/device stay visual-only placeholders — SharePlay/Watch-mode have no Android
        // equivalent built (see the port plan's won't-port list / later Wear OS phase).
        // Lyrics, Share, and Up Next are all real now.
        //
        // This whole row is absent for radio — mirrors iOS, where it isn't a set of dimmed
        // icons but simply isn't in the radio content tree at all: lyrics needs a track, Share
        // has nothing track-shaped to share, and Up Next has no queue to show during a live
        // stream (the transport row below already dims/disables shuffle/prev/next/repeat for
        // the same reason, but there's no meaningful "disabled" state for these three — they'd
        // just do nothing if tapped, so they're hidden instead of shown inert).
        if (!nowPlaying.isPlayingRadio) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Subtitles,
                    contentDescription = null,
                    tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp).clickable(onClick = { showLyrics = !showLyrics })
                )
                Icon(Icons.Filled.Cast, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                if (track != null) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.player_share_content_description),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp).clickable(onClick = { showShareSheet = true })
                    )
                } else {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                }
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.player_up_next_content_description),
                    tint = if (showQueue) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp).clickable(onClick = { showQueue = true })
                )
                Icon(Icons.Filled.Devices, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
            }
        }
    }

    // Similar Songs/Artists + Song Info + Artist Bio — mirrors iOS's PlayerAboutSection, which
    // lives inside the SAME ScrollView as the hero content above rather than a separate one.
    val aboutContent: @Composable ColumnScope.() -> Unit = {
        if (track != null) {
            SimilarSongsSection(
                songs = similarSongs,
                coverArtUrl = { host, coverArt -> viewModel.coverArtUrl(host, coverArt, 300) },
                onSongClick = viewModel::playSimilar
            )
            SimilarArtistsSection(
                artists = similarArtists,
                coverArtUrl = { id -> viewModel.similarArtistAvatarUrl(track, id) },
                onArtistClick = { id, name ->
                    onArtistClick(compositeId(track.serverHost, id), name)
                }
            )
            PlayerSongInfoCard(track = track, onShowFileDetails = { showSongInfo = true })
            PlayerArtistBioCard(
                artistName = viewModel.primaryArtistDisplayName(track),
                avatarUrl = viewModel.primaryArtistAvatarUrl(track),
                biography = artistBiography,
                expanded = showFullBio,
                onToggleExpanded = { showFullBio = !showFullBio },
                onArtistClick = viewModel.primaryArtistRouteId(track)?.let { id ->
                    { onArtistClick(id, viewModel.primaryArtistDisplayName(track)) }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.toInt()) }
            // A plain Box with no input handling doesn't participate in hit-testing, so without
            // this, taps on any empty area of the sheet (there's plenty — the Spacer gaps, the
            // backdrop itself) fall straight through to whatever NavHost content AppShell is
            // rendering underneath, even though this sheet is drawn on top of it.
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
    ) {
        if (kawarpSettings.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && KawarpEngine.isSupported()) {
            KawarpBackdrop(artworkUrl = artUrl, isPlaying = nowPlaying.isPlaying, settings = kawarpSettings)
        } else {
            FullBleedBackdrop(artworkUrl = artUrl)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Without this, the transport/icon row in lyrics mode (a fixed, non-scrollable
                // layout — see the showLyrics branch below) sits flush against the physical
                // bottom edge with no allowance for a gesture nav bar, reading as "almost off
                // screen". The non-lyrics scrollable layout doesn't need this as badly (there's
                // always more content below to scroll past) but it's a correct inset there too.
                .navigationBarsPadding()
        ) {
            if (showLyrics) {
                // Lyrics have their own internal scroll (PlayerLyricsView) and there's no About
                // section showing right now, so this stays the old fixed-height hero with a plain
                // drag-to-dismiss — no descendant scrollable to conflict with.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        if (offsetY.value > dismissThresholdPx) onDismiss() else offsetY.animateTo(0f)
                                    }
                                },
                                onDragCancel = { coroutineScope.launch { offsetY.animateTo(0f) } }
                            )
                        }
                        .padding(horizontal = 32.dp)
                ) {
                    heroContent()
                }
            } else {
                // The whole sheet's content — hero AND the About section — lives in ONE
                // scrollable Column, mirroring iOS's single ScrollView (PlayerView.swift wraps
                // PlayerMainContent + PlayerAboutSection together, not in separate scroll areas).
                // Drag-to-dismiss is gated through a NestedScrollConnection instead of a raw
                // pointerInput: onPreScroll only steals the gesture into the dismiss offset while
                // already partway dismissed, or while the scroll is sitting at its very top edge
                // (equivalent to iOS's sheetDismissCoordinator "isEnabled = atTop" gate) — any
                // other downward/upward drag is left alone so the Column's own verticalScroll
                // handles it normally, which is what makes the About content scrollable at all.
                val scrollState = rememberScrollState()
                val dismissConnection = remember(scrollState) {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        override fun onPreScroll(
                            available: androidx.compose.ui.geometry.Offset,
                            source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
                        ): androidx.compose.ui.geometry.Offset {
                            if (available.y > 0f && (offsetY.value > 0f || scrollState.value == 0)) {
                                val newOffset = (offsetY.value + available.y).coerceAtLeast(0f)
                                val consumedY = newOffset - offsetY.value
                                coroutineScope.launch { offsetY.snapTo(newOffset) }
                                return androidx.compose.ui.geometry.Offset(0f, consumedY)
                            }
                            return androidx.compose.ui.geometry.Offset.Zero
                        }

                        override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                            if (offsetY.value > 0f) {
                                if (offsetY.value > dismissThresholdPx) onDismiss() else offsetY.animateTo(0f)
                                return available
                            }
                            return androidx.compose.ui.unit.Velocity.Zero
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(dismissConnection)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 32.dp)
                ) {
                    heroContent()
                    aboutContent()
                }
            }
        }

        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            QueueScreen(onDismiss = { showQueue = false })
        }
    }

    if (track != null) {
        if (showAddToPlaylist) {
            AddToPlaylistSheet(track = track, onDismiss = { showAddToPlaylist = false })
        }
        if (showShareSheet) {
            ShareSheet(
                target = ShareTarget(track.serverHost, track.subsonicId, track.title),
                onDismiss = { showShareSheet = false }
            )
        }
        if (showSongInfo) {
            SongInfoSheet(
                track = track,
                coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt, 300),
                storageStatusLabel = trackStatusLabel(
                    isDownloaded = downloads[track.id]?.state == DownloadState.STATE_COMPLETED,
                    isCached = nowPlaying.isCurrentTrackCached
                ),
                onDismiss = { showSongInfo = false }
            )
        }
    }
}

/** Mirrors iOS's `moreRadioStationsSection`: every other station on the same server as the one
 *  currently playing, as a vertical list with a divider between rows (none after the last) —
 *  not a carousel. Tapping a row switches playback straight to that station. */
@Composable
private fun MoreRadioStationsSection(
    stations: List<RadioStationEntity>,
    currentStationId: String?,
    coverArtUrl: (String, String?, Int) -> String?,
    onStationClick: (RadioStationEntity) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 30.dp)) {
        Text(
            text = stringResource(R.string.player_more_radio_stations),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
        stations.forEachIndexed { index, station ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStationClick(station) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val artUrl = station.coverArt?.let { coverArtUrl(station.serverHost, "ra-${station.subsonicId}", 120) }
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Radio, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = station.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = station.homePageUrl?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_live_stream),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }
            if (index < stations.lastIndex) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun DragHandle() {
    // Purely visual now — the actual drag-to-dismiss gesture is attached to the whole sheet's
    // content Column (see PlayerScreen), since a target this small was too easy to miss.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.4f))
        )
    }
}

/** Mirrors PlayerCompactHeader.swift — shown ABOVE the lyrics content instead of the normal
 *  artwork+title block: a chevron-down (closes lyrics), a small 52dp thumbnail, title/artist
 *  (not tap-to-navigate here, matching iOS — only the full-size title row below the artwork is),
 *  then the same heart/overflow-menu cluster as the normal header. */
@Composable
private fun PlayerCompactHeader(
    track: TrackEntity,
    thumbnailUrl: String?,
    onDismissLyrics: () -> Unit,
    isDownloaded: Boolean,
    showOverflowMenu: Boolean,
    onToggleFavorite: () -> Unit,
    onShowOverflowMenu: () -> Unit,
    onDismissOverflowMenu: () -> Unit,
    onArtistClick: (ArtistEntry) -> Unit,
    onAlbumClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowInfo: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.player_close_lyrics_content_description),
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp).clickable(onClick = onDismissLyrics)
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(model = thumbnailUrl, contentDescription = track.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = track.artistName, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayerActionButtonsCluster(
            track = track,
            isDownloaded = isDownloaded,
            showOverflowMenu = showOverflowMenu,
            onToggleFavorite = onToggleFavorite,
            onShowOverflowMenu = onShowOverflowMenu,
            onDismissOverflowMenu = onDismissOverflowMenu,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onAddToPlaylist = onAddToPlaylist,
            onShowInfo = onShowInfo,
            onDownload = onDownload
        )
    }
}

/** Heart (favorite) + "..." overflow menu cluster — shared between the normal title row and
 *  [PlayerCompactHeader]'s lyrics-mode header, matching how iOS reuses PlayerActionButtons in
 *  both PlayerMainContent's titleArtistRow and PlayerCompactHeader. */
@Composable
private fun PlayerActionButtonsCluster(
    track: TrackEntity,
    isDownloaded: Boolean,
    showOverflowMenu: Boolean,
    onToggleFavorite: () -> Unit,
    onShowOverflowMenu: () -> Unit,
    onDismissOverflowMenu: () -> Unit,
    onArtistClick: (ArtistEntry) -> Unit,
    onAlbumClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowInfo: () -> Unit,
    onDownload: () -> Unit
) {
    RoundIconButton(onClick = onToggleFavorite) {
        Icon(
            imageVector = if (track.isStarred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (track.isStarred) Color.Red else Color.White.copy(alpha = 0.7f)
        )
    }
    Box {
        RoundIconButton(onClick = onShowOverflowMenu) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        }
        PlayerOverflowMenu(
            expanded = showOverflowMenu,
            onDismiss = onDismissOverflowMenu,
            track = track,
            isDownloaded = isDownloaded,
            onArtistClick = onArtistClick,
            onAlbumClick = onAlbumClick,
            onAddToPlaylist = onAddToPlaylist,
            onShowInfo = onShowInfo,
            onDownload = onDownload
        )
    }
}

@Composable
private fun SimilarSongsSection(
    songs: List<TrackEntity>,
    coverArtUrl: (String, String?) -> String?,
    onSongClick: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = stringResource(R.string.player_similar_songs_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(end = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onSongClick(index) },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        val url = coverArtUrl(song.serverHost, song.coverArt)
                        if (url != null) {
                            AsyncImage(model = url, contentDescription = song.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Text(text = song.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = song.artistName, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SimilarArtistsSection(
    artists: List<SimilarArtistDto>,
    coverArtUrl: (String) -> String?,
    onArtistClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = stringResource(R.string.player_similar_artists_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (artists.isEmpty()) {
            Text(text = stringResource(R.string.player_no_similar_artists_found), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        } else {
            LazyRow(
                contentPadding = PaddingValues(end = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(artists, key = { _, artist -> artist.id }) { _, artist ->
                    Column(
                        modifier = Modifier
                            .width(100.dp)
                            .clickable { onArtistClick(artist.id, artist.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            val url = coverArtUrl(artist.id)
                            if (url != null) {
                                AsyncImage(model = url, contentDescription = artist.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Text(
                            text = artist.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSongInfoCard(track: TrackEntity, onShowFileDetails: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.player_song_info_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        val albumLabel = stringResource(R.string.player_song_info_album)
        val artistLabel = stringResource(R.string.player_song_info_artist)
        val albumArtistLabel = stringResource(R.string.player_song_info_album_artist)
        val yearLabel = stringResource(R.string.player_song_info_year)
        val genreLabel = stringResource(R.string.player_song_info_genre)
        val formatLabelText = stringResource(R.string.player_song_info_format)
        val playCountLabel = stringResource(R.string.player_song_info_play_count)
        val commentLabel = stringResource(R.string.player_song_info_comment)
        val rows = listOfNotNull(
            track.albumName?.takeIf(String::isNotEmpty)?.let { albumLabel to it },
            (track.displayAlbumArtist?.takeIf(String::isNotEmpty) ?: track.artistName.takeIf(String::isNotEmpty))
                ?.let { (if (track.displayAlbumArtist.isNullOrEmpty()) artistLabel else albumArtistLabel) to it },
            track.year?.let { yearLabel to it.toString() },
            track.genre?.takeIf(String::isNotEmpty)?.let { genreLabel to it },
            formatQualityDetail(track)?.let { formatLabelText to it },
            track.playCount?.takeIf { it > 0 }?.let { playCountLabel to it.toString() },
            track.comment?.takeIf(String::isNotEmpty)?.let { commentLabel to it }
        )
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.width(110.dp))
                Text(text = value, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowFileDetails)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.player_file_details), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlayerArtistBioCard(
    artistName: String,
    avatarUrl: String?,
    biography: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onArtistClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val avatarModifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .then(if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier)
            Box(modifier = avatarModifier) {
                if (avatarUrl != null) {
                    AsyncImage(model = avatarUrl, contentDescription = artistName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center).size(24.dp)
                    )
                }
            }
            Column(
                modifier = if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier
            ) {
                Text(text = stringResource(R.string.player_about_the_artist), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text(
                    text = artistName.ifEmpty { stringResource(R.string.player_unknown_artist) },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val bioWords = biography?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        val isTruncatable = bioWords.size > 80
        if (!biography.isNullOrEmpty()) {
            val truncationEllipsis = stringResource(R.string.player_bio_truncation_ellipsis)
            val displayText = if (isTruncatable && !expanded) bioWords.take(80).joinToString(" ") + truncationEllipsis else biography
            Text(
                text = displayText,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
            if (isTruncatable) {
                Text(
                    text = if (expanded) stringResource(R.string.player_show_less) else stringResource(R.string.player_show_more),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = onToggleExpanded)
                )
            }
        } else {
            Text(
                text = stringResource(R.string.player_no_biography_available),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun TransportIconButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ArtistRow(track: TrackEntity, onArtistClick: (ArtistEntry) -> Unit) {
    val entries = track.artistEntries()
    Row(modifier = Modifier.padding(top = 2.dp)) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                Text(text = stringResource(R.string.player_artist_separator), color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }
            Text(
                text = entry.name,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (entry.id.isNotEmpty()) Modifier.clickable { onArtistClick(entry) } else Modifier
            )
        }
    }
}

@Composable
private fun PlayerOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    track: TrackEntity,
    isDownloaded: Boolean,
    onArtistClick: (ArtistEntry) -> Unit,
    onAlbumClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowInfo: () -> Unit,
    onDownload: () -> Unit
) {
    val entries = track.artistEntries()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (entries.size == 1) {
            val entry = entries[0]
            if (entry.id.isNotEmpty()) {
                val goToArtistLabel = stringResource(R.string.player_go_to_artist)
                DropdownMenuItem(
                    text = { Text(goToArtistLabel) },
                    onClick = { onDismiss(); onArtistClick(entry) }
                )
            }
        } else {
            entries.forEach { entry ->
                if (entry.id.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(entry.name) },
                        onClick = { onDismiss(); onArtistClick(entry) }
                    )
                }
            }
        }
        if (track.albumCompositeId != null) {
            val goToAlbumLabel = stringResource(R.string.player_go_to_album)
            DropdownMenuItem(
                text = { Text(goToAlbumLabel) },
                onClick = { onDismiss(); onAlbumClick() }
            )
        }
        HorizontalDivider()
        val addToPlaylistLabel = stringResource(R.string.player_add_to_playlist)
        DropdownMenuItem(
            text = { Text(addToPlaylistLabel) },
            leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
            onClick = { onDismiss(); onAddToPlaylist() }
        )
        val showInfoLabel = stringResource(R.string.player_show_info)
        DropdownMenuItem(
            text = { Text(showInfoLabel) },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
            onClick = { onDismiss(); onShowInfo() }
        )
        if (!isDownloaded) {
            val downloadLabel = stringResource(R.string.player_download)
            DropdownMenuItem(
                text = { Text(downloadLabel) },
                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                onClick = { onDismiss(); onDownload() }
            )
        }
    }
}

private val LOSSLESS_SUFFIXES = setOf("flac", "alac", "wav")

@Composable
private fun PlayerQualityPill(track: TrackEntity, isDownloaded: Boolean, isCached: Boolean, isBuffering: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        if (isBuffering) {
            CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp, color = Color.White.copy(alpha = 0.7f))
            Text(text = stringResource(R.string.player_quality_loading), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            return@Row
        }
        val prefix = trackStatusLabel(isDownloaded, isCached)
        val suffix = track.suffix?.lowercase()
        // Android streams at raw/original quality only — there's no transcoding-quality setting
        // yet (unlike iOS's StreamingSettings.maxBitrate), so unlike iOS this is never anything
        // other than "Original" for a non-lossless file; revisit once that setting exists.
        val isLossless = suffix in LOSSLESS_SUFFIXES
        if (isLossless) {
            val flacFallback = stringResource(R.string.player_quality_flac_fallback)
            Text(
                text = stringResource(R.string.player_quality_lossless_line, prefix, suffix?.uppercase() ?: flacFallback),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
            Text(text = stringResource(R.string.player_quality_lossless), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        } else {
            val unknownFormat = stringResource(R.string.player_quality_unknown_format)
            val formatLabel = (track.suffix ?: track.contentType)?.uppercase() ?: unknownFormat
            Text(
                text = stringResource(R.string.player_quality_original_line, prefix, formatLabel),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Mirrors iOS's `Track.formatDetail()` — "{SUFFIX} • {bitrate} kbps • {bitDepth}-bit", each
 *  segment only included when actually known. */
@Composable
private fun formatQualityDetail(track: TrackEntity): String? {
    val suffix = track.suffix?.uppercase()?.takeIf(String::isNotEmpty) ?: return null
    val kbpsFormat = stringResource(R.string.player_quality_kbps)
    val bitDepthFormat = stringResource(R.string.player_quality_bit_depth)
    val parts = buildList {
        add(suffix)
        track.bitRate?.takeIf { it > 0 }?.let { add(String.format(kbpsFormat, it)) }
        track.bitDepth?.takeIf { it > 0 }?.let { add(String.format(bitDepthFormat, it)) }
    }
    return parts.joinToString(" • ")
}
