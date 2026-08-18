package de.ilazlow.velosonic.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.domain.goToArtistTarget
import de.ilazlow.velosonic.ui.common.CoverArtTile
import de.ilazlow.velosonic.ui.common.DetailArtwork
import de.ilazlow.velosonic.ui.common.ExplicitBadge
import de.ilazlow.velosonic.ui.common.GradientBackdrop
import de.ilazlow.velosonic.ui.common.HeroBanner
import de.ilazlow.velosonic.ui.common.NowPlayingIndicator
import de.ilazlow.velosonic.ui.common.TrackOverflowMenu
import de.ilazlow.velosonic.ui.common.boostSaturation
import de.ilazlow.velosonic.ui.common.rememberDominantColor
import de.ilazlow.velosonic.ui.common.trackStatusLabel
import de.ilazlow.velosonic.ui.player.SongInfoSheet
import de.ilazlow.velosonic.ui.playlists.AddToPlaylistSheet
import de.ilazlow.velosonic.ui.share.ShareSheet
import de.ilazlow.velosonic.ui.share.ShareTarget

private val LOSSLESS_SUFFIXES = setOf("flac", "alac", "wav", "aiff", "ape", "dsf")

/**
 * Mirrors AlbumDetailView.swift's composition: a top bar (back/title/more), a flat saturated
 * dominant-color wash (not a full-bleed blurred photo — see GradientBackdrop's doc), a glow-
 * backed artwork, title/artist/format-badges/server-pill block, a shuffle/play/favorite action
 * row, a track list with per-row format/download affordances, a footer, and a "More from Artist"
 * row. Text stays white throughout, same as the Player sheet — reliable contrast against an
 * arbitrary colorful backdrop matters more here than following the system theme.
 */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (id: String, name: String) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val moreFromArtist by viewModel.moreFromArtist.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val animatedVideoUri by viewModel.animatedVideoUri.collectAsStateWithLifecycle()
    val animateWebpArtwork by viewModel.animateWebpArtwork.collectAsStateWithLifecycle()
    val canReachNetwork by viewModel.canReachNetwork.collectAsStateWithLifecycle()
    val isFullyDownloaded = tracks.isNotEmpty() && tracks.all { downloads[it.id]?.state == Download.STATE_COMPLETED }

    val artworkUrl = album?.let { viewModel.coverArtUrl(it.serverHost, it.coverArt, 800) }
    val dominantColor = rememberDominantColor(artworkUrl, MaterialTheme.colorScheme.surfaceVariant).boostSaturation()
    var addToPlaylistTarget by remember { mutableStateOf<TrackEntity?>(null) }
    var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var songInfoTarget by remember { mutableStateOf<TrackEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        GradientBackdrop(baseColor = dominantColor)
        HeroBanner(
            artworkUrl = artworkUrl,
            fadeToColor = dominantColor,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            AlbumTopBar(
                title = album?.name.orEmpty(),
                onBack = onBack,
                isFullyDownloaded = isFullyDownloaded,
                onDownloadAlbum = viewModel::downloadAlbum,
                onRemoveDownloads = viewModel::removeAlbumDownloads,
                onShare = { album?.let { shareTarget = ShareTarget(it.serverHost, it.subsonicId, it.name) } }
            )

            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                item {
                    AlbumHeader(
                        album = album,
                        artworkUrl = artworkUrl,
                        animatedVideoUri = animatedVideoUri,
                        animateWebpArtwork = animateWebpArtwork,
                        serverName = album?.let { viewModel.serverName(it.serverHost) },
                        isLossless = tracks.any { it.suffix?.lowercase() in LOSSLESS_SUFFIXES },
                        isExplicit = tracks.any { it.explicitStatus.equals("explicit", ignoreCase = true) },
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::playShuffled,
                        onToggleFavorite = viewModel::toggleAlbumFavorite
                    )
                }
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    val downloadState = downloads[track.id]?.state
                    val isCached = viewModel.isTrackCached(track)
                    TrackRow(
                        track = track,
                        albumArtist = album?.artistName.orEmpty(),
                        isCurrentTrack = nowPlaying.track?.id == track.id,
                        isPlaying = nowPlaying.isPlaying,
                        downloadState = downloadState,
                        isCached = isCached,
                        isUnavailableOffline = !canReachNetwork && downloadState != Download.STATE_COMPLETED && !isCached,
                        canReachNetwork = canReachNetwork,
                        onClick = { viewModel.onTrackClick(index) },
                        onToggleStar = { viewModel.toggleTrackFavorite(track) },
                        onPlayNext = { viewModel.playNext(track) },
                        onInstantMix = { viewModel.playInstantMix(track) },
                        onGoToArtist = track.goToArtistTarget()?.let { (id, name) -> { onArtistClick(id, name) } },
                        onGoToAlbum = track.albumCompositeId?.let { id -> { onAlbumClick(id) } },
                        onAddToPlaylist = { addToPlaylistTarget = track },
                        onToggleDownload = { viewModel.toggleTrackDownload(track) },
                        onShare = { shareTarget = ShareTarget(track.serverHost, track.subsonicId, track.title) },
                        onShowInfo = { songInfoTarget = track }
                    )
                }
                item {
                    AlbumFooter(album = album, trackCount = tracks.size, totalDurationSeconds = viewModel.totalDurationSeconds())
                }
                if (moreFromArtist.isNotEmpty()) {
                    item {
                        MoreFromArtistSection(
                            artistName = album?.artistName.orEmpty(),
                            albums = moreFromArtist,
                            coverArtUrl = { serverHost, coverArt -> viewModel.coverArtUrl(serverHost, coverArt, 300) },
                            onAlbumClick = onAlbumClick
                        )
                    }
                }
            }
        }
    }

    addToPlaylistTarget?.let { track ->
        AddToPlaylistSheet(track = track, onDismiss = { addToPlaylistTarget = null })
    }
    shareTarget?.let { target ->
        ShareSheet(target = target, onDismiss = { shareTarget = null })
    }
    songInfoTarget?.let { track ->
        SongInfoSheet(
            track = track,
            coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt, 300),
            storageStatusLabel = trackStatusLabel(
                isDownloaded = downloads[track.id]?.state == Download.STATE_COMPLETED,
                isCached = viewModel.isTrackCached(track)
            ),
            onDismiss = { songInfoTarget = null }
        )
    }
}

@Composable
private fun AlbumTopBar(
    title: String,
    onBack: () -> Unit,
    isFullyDownloaded: Boolean,
    onDownloadAlbum: () -> Unit,
    onRemoveDownloads: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleActionButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Box {
            CircleActionButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = Color.White)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (isFullyDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Remove Downloads", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onRemoveDownloads() }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Download Album") },
                        leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                        onClick = { showMenu = false; onDownloadAlbum() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share Album") },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = { showMenu = false; onShare() }
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: AlbumEntity?,
    artworkUrl: String?,
    animatedVideoUri: String?,
    animateWebpArtwork: Boolean,
    serverName: String?,
    isLossless: Boolean,
    isExplicit: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DetailArtwork(
            staticUrl = artworkUrl,
            videoUri = animatedVideoUri,
            contentDescription = album?.name,
            animateWebp = animateWebpArtwork,
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Text(
            text = album?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
        )
        Text(
            text = album?.artistName.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(top = 2.dp)
        )

        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val infoLine = listOfNotNull(album?.genre, album?.year?.toString()).joinToString(" • ")
            if (infoLine.isNotEmpty()) {
                Text(text = infoLine, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
            if (isLossless) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                Text(text = "Lossless", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
            if (isExplicit) {
                ExplicitBadge()
            }
        }

        if (serverName != null) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier.padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircleActionButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Color.White)
            }
            Row(
                modifier = Modifier
                    .height(50.dp)
                    .width(160.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color.White)
                    .clickable(onClick = onPlay),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Play", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
            CircleActionButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (album?.isStarred == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (album?.isStarred == true) Color.Red else Color.White
                )
            }
        }
    }
}

@Composable
private fun CircleActionButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    albumArtist: String,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    downloadState: Int?,
    isCached: Boolean,
    isUnavailableOffline: Boolean = false,
    canReachNetwork: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onPlayNext: () -> Unit,
    onInstantMix: () -> Unit,
    onGoToArtist: (() -> Unit)?,
    onGoToAlbum: (() -> Unit)?,
    onAddToPlaylist: () -> Unit,
    onToggleDownload: () -> Unit,
    onShare: () -> Unit,
    onShowInfo: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isUnavailableOffline, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .alpha(if (isUnavailableOffline) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isCurrentTrack) {
            NowPlayingIndicator(isPlaying = isPlaying, color = Color.White, modifier = Modifier.width(20.dp))
        } else {
            Text(
                text = (track.trackNumber ?: 0).toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.width(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.artistName != albumArtist) {
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (track.explicitStatus.equals("explicit", ignoreCase = true)) {
            ExplicitBadge()
        }
        if (track.isStarred) {
            Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
        }
        Text(
            text = formatDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        // Status only, not a button — mirrors TrackRow.swift exactly: individual tracks aren't
        // downloaded via a tap on this icon, only via the row's "..." menu (below) or the bulk
        // album/playlist download action; this just reflects current state (filled+accent once
        // downloaded, plain outline while queued/downloading, a dashed-circle equivalent when
        // merely stream-cached from having played before, nothing for a plain never-touched track).
        if (downloadState == Download.STATE_COMPLETED) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        } else if (downloadState != null) {
            Icon(Icons.Filled.FileDownload, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        } else if (isCached) {
            Icon(Icons.Filled.Cached, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
        Box {
            IconButton(onClick = { showMenu = true }, enabled = !isUnavailableOffline, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
            TrackOverflowMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                isStarred = track.isStarred,
                onToggleStar = onToggleStar,
                onPlayNext = onPlayNext,
                canReachNetwork = canReachNetwork,
                onInstantMix = onInstantMix,
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
                onAddToPlaylist = onAddToPlaylist,
                onShare = onShare,
                onShowInfo = onShowInfo,
                isDownloaded = downloadState == Download.STATE_COMPLETED,
                onToggleDownload = onToggleDownload
            )
        }    }
}

@Composable
private fun AlbumFooter(album: AlbumEntity?, trackCount: Int, totalDurationSeconds: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        val trackWord = if (trackCount == 1) "Track" else "Tracks"
        Text(
            text = "$trackCount $trackWord • ${formatDurationMinutes(totalDurationSeconds)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        val year = album?.year
        if (year != null) {
            Text(
                text = "© $year ${album.artistName}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun MoreFromArtistSection(
    artistName: String,
    albums: List<AlbumEntity>,
    coverArtUrl: (String, String?) -> String?,
    onAlbumClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
        Text(
            text = "More from $artistName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums, key = { it.id }) { other ->
                Column(
                    modifier = Modifier
                        .width(140.dp)
                        .clickable { onAlbumClick(other.id) },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoverArtTile(
                        url = coverArtUrl(other.serverHost, other.coverArt),
                        contentDescription = other.name,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    )
                    Text(
                        text = other.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun formatDurationMinutes(totalSeconds: Int): String {
    val totalMinutes = (totalSeconds / 60).coerceAtLeast(if (totalSeconds > 0) 1 else 0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}
