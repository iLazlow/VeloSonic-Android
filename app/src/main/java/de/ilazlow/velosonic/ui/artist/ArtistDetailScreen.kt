package de.ilazlow.velosonic.ui.artist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import coil3.compose.AsyncImage
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.data.network.dto.SimilarArtistDto
import de.ilazlow.velosonic.data.sync.compositeId
import de.ilazlow.velosonic.domain.goToArtistTarget
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.ui.common.AlbumGridItem
import de.ilazlow.velosonic.ui.common.CoverArtTile
import de.ilazlow.velosonic.ui.common.ExplicitBadge
import de.ilazlow.velosonic.ui.common.NowPlayingIndicator
import de.ilazlow.velosonic.ui.common.TrackOverflowMenu
import de.ilazlow.velosonic.ui.common.trackStatusLabel
import de.ilazlow.velosonic.ui.player.SongInfoSheet
import de.ilazlow.velosonic.ui.playlists.AddToPlaylistSheet
import de.ilazlow.velosonic.ui.share.ShareSheet
import de.ilazlow.velosonic.ui.share.ShareTarget

/**
 * Mirrors ArtistDetailView.swift's content — avatar, "X Albums • Y Songs", shuffle/play/favorite
 * row, Top Songs, Albums grid, biography (tap-to-expand), Similar Artists (recursing into another
 * instance of this same screen) — on a plain Material background rather than AlbumDetailScreen's
 * dominant-color wash: [de.ilazlow.velosonic.ui.common.AlbumGridItem] (reused here for the Albums
 * grid) assumes normal theme text colors, and forcing it white-on-color for one screen wasn't
 * worth the risk of breaking its other three call sites. Same trade PlaylistDetailScreen already
 * documents for itself.
 */
@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val artist by viewModel.artist.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val appearsInAlbums by viewModel.appearsInAlbums.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val bio by viewModel.bio.collectAsStateWithLifecycle()
    val similarArtists by viewModel.similarArtists.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val animatedGrid by viewModel.animatedAlbumGridEnabled.collectAsStateWithLifecycle()
    val totalSongCount by viewModel.totalSongCount.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val canReachNetwork by viewModel.canReachNetwork.collectAsStateWithLifecycle()
    val artistTracks by viewModel.artistTracks.collectAsStateWithLifecycle()
    val isFullyDownloaded = artistTracks.isNotEmpty() && artistTracks.all { downloads[it.id]?.state == Download.STATE_COMPLETED }

    var showMenu by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showFullBio by remember { mutableStateOf(false) }
    var addToPlaylistTarget by remember { mutableStateOf<TrackEntity?>(null) }
    var trackShareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var songInfoTarget by remember { mutableStateOf<TrackEntity?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = viewModel.artistName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isFullyDownloaded) {
                        DropdownMenuItem(
                            text = { Text("Remove Downloads", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; viewModel.removeArtistDownloads() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Download Artist") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                            onClick = { showMenu = false; viewModel.downloadArtist() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { showMenu = false; showShareSheet = true }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("View on Last.fm") },
                        leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lastFmUrl(viewModel.artistName))))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("View on Spotify") },
                        leadingIcon = { Icon(Icons.Filled.PlayCircle, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUrl(viewModel.artistName))))
                        }
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ArtistHeader(
                    name = viewModel.artistName,
                    avatarUrl = viewModel.avatarUrl(),
                    albumCount = (albums.map { it.id } + appearsInAlbums.map { it.id }).distinct().size,
                    songCount = totalSongCount,
                    isStarred = artist?.isStarred == true,
                    serverBadgeLabel = artist?.let { a -> serverBadgeFor(servers, a.serverHost) },
                    onShuffle = viewModel::playShuffled,
                    onPlay = { viewModel.playTopSongs() },
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }

            if (topSongs.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TopSongsSection(
                        songs = topSongs,
                        artistName = viewModel.artistName,
                        nowPlaying = nowPlaying,
                        downloads = downloads,
                        isTrackCached = viewModel::isTrackCached,
                        canReachNetwork = canReachNetwork,
                        coverArtUrl = viewModel::coverArtUrl,
                        onSongClick = { index -> viewModel.playTopSongs(index) },
                        onToggleStar = viewModel::toggleTrackFavorite,
                        onPlayNext = viewModel::playNext,
                        onInstantMix = viewModel::playInstantMix,
                        onGoToArtist = onArtistClick,
                        onGoToAlbum = onAlbumClick,
                        onToggleDownload = viewModel::toggleTrackDownload,
                        onAddToPlaylist = { addToPlaylistTarget = it },
                        onShare = { trackShareTarget = ShareTarget(it.serverHost, it.subsonicId, it.title) },
                        onShowInfo = { songInfoTarget = it }
                    )
                }
            }

            if (albums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }
                items(albums, key = { it.id }) { album ->
                    AlbumGridItem(
                        title = album.name,
                        artist = null,
                        year = album.year,
                        coverArtUrl = viewModel.coverArtUrl(album.serverHost, album.coverArt),
                        onClick = { onAlbumClick(album.id) },
                        animate = animatedGrid
                    )
                }
            }

            if (appearsInAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Appears On",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }
                items(appearsInAlbums, key = { it.id }) { album ->
                    AlbumGridItem(
                        title = album.name,
                        artist = album.artistName,
                        year = album.year,
                        coverArtUrl = viewModel.coverArtUrl(album.serverHost, album.coverArt),
                        onClick = { onAlbumClick(album.id) },
                        animate = animatedGrid
                    )
                }
            }

            if (!bio.isNullOrEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BioSection(
                        artistName = viewModel.artistName,
                        bio = bio.orEmpty(),
                        expanded = showFullBio,
                        onToggle = { showFullBio = !showFullBio }
                    )
                }
            }

            if (similarArtists.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SimilarArtistsSection(
                        similar = similarArtists,
                        coverArtUrl = { id -> artist?.let { viewModel.coverArtUrl(it.serverHost, id) } },
                        onArtistClick = { similarArtist ->
                            artist?.let { current ->
                                onArtistClick(compositeId(current.serverHost, similarArtist.id), similarArtist.name)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showShareSheet) {
        artist?.let { a ->
            ShareSheet(
                target = ShareTarget(a.serverHost, a.subsonicId, viewModel.artistName),
                onDismiss = { showShareSheet = false }
            )
        }
    }

    addToPlaylistTarget?.let { track ->
        AddToPlaylistSheet(track = track, onDismiss = { addToPlaylistTarget = null })
    }
    trackShareTarget?.let { target ->
        ShareSheet(target = target, onDismiss = { trackShareTarget = null })
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

/** Last.fm artist pages are keyed by name directly in the URL — links straight to the artist's
 *  page, same as iOS's `lastFmURL`. */
private fun lastFmUrl(artistName: String): String =
    "https://www.last.fm/music/${java.net.URLEncoder.encode(artistName, "UTF-8")}"

/** Spotify has no name-based artist URL — opens a search, same as iOS's `spotifyURL`, since
 *  there's no Spotify artist id to link to directly. */
private fun spotifyUrl(artistName: String): String =
    "https://open.spotify.com/search/${java.net.URLEncoder.encode(artistName, "UTF-8")}"

private fun serverBadgeFor(servers: List<ServerConfigEntity>, host: String): String? {
    if (servers.size <= 1) return null
    val config = servers.firstOrNull { it.host == host } ?: return host
    return config.name.ifBlank { host }
}

@Composable
private fun ArtistHeader(
    name: String,
    avatarUrl: String?,
    albumCount: Int,
    songCount: Int,
    isStarred: Boolean,
    serverBadgeLabel: String?,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
        )

        if (serverBadgeLabel != null) {
            Text(
                text = serverBadgeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Text(
            text = "$albumCount Albums • $songCount Songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircleActionButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
            }
            Row(
                modifier = Modifier
                    .height(50.dp)
                    .width(160.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Play", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            }
            CircleActionButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isStarred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isStarred) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun TopSongsSection(
    songs: List<TrackEntity>,
    artistName: String,
    nowPlaying: NowPlaying,
    downloads: Map<String, Download>,
    isTrackCached: (TrackEntity) -> Boolean,
    canReachNetwork: Boolean,
    coverArtUrl: (String, String?, Int) -> String?,
    onSongClick: (Int) -> Unit,
    onToggleStar: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onInstantMix: (TrackEntity) -> Unit,
    onGoToArtist: (id: String, name: String) -> Unit,
    onGoToAlbum: (id: String) -> Unit,
    onToggleDownload: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onShare: (TrackEntity) -> Unit,
    onShowInfo: (TrackEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Top Songs",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        songs.take(5).forEachIndexed { index, track ->
            val downloadState = downloads[track.id]?.state
            val isCached = isTrackCached(track)
            ArtistTrackRow(
                track = track,
                albumArtist = artistName,
                coverArtUrl = coverArtUrl(track.serverHost, track.coverArt, 100),
                isCurrentTrack = nowPlaying.track?.id == track.id,
                isPlaying = nowPlaying.isPlaying,
                downloadState = downloadState,
                isCached = isCached,
                isUnavailableOffline = !canReachNetwork && downloadState != Download.STATE_COMPLETED && !isCached,
                canReachNetwork = canReachNetwork,
                onClick = { onSongClick(index) },
                onToggleStar = { onToggleStar(track) },
                onPlayNext = { onPlayNext(track) },
                onInstantMix = { onInstantMix(track) },
                onGoToArtist = track.goToArtistTarget()?.let { (id, name) -> { onGoToArtist(id, name) } },
                onGoToAlbum = track.albumCompositeId?.let { id -> { onGoToAlbum(id) } },
                onToggleDownload = { onToggleDownload(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onShare = { onShare(track) },
                onShowInfo = { onShowInfo(track) }
            )
        }
    }
}

@Composable
private fun ArtistTrackRow(
    track: TrackEntity,
    albumArtist: String,
    coverArtUrl: String?,
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
    onToggleDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onShowInfo: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isUnavailableOffline, onClick = onClick)
            .padding(vertical = 6.dp)
            .alpha(if (isUnavailableOffline) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isCurrentTrack) {
            NowPlayingIndicator(isPlaying = isPlaying, modifier = Modifier.size(48.dp))
        } else {
            CoverArtTile(
                url = coverArtUrl,
                contentDescription = track.title,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.artistName != albumArtist) {
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (track.explicitStatus.equals("explicit", ignoreCase = true)) {
            ExplicitBadge(
                containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (track.isStarred) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
        }
        Text(
            text = formatTrackDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Status only — see AlbumDetailScreen's TrackRow for why this isn't a button.
        if (downloadState == Download.STATE_COMPLETED) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        } else if (downloadState != null) {
            Icon(Icons.Filled.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        } else if (isCached) {
            Icon(Icons.Filled.Cached, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Box {
            IconButton(onClick = { showMenu = true }, enabled = !isUnavailableOffline) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
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
        }
    }
}

private fun formatTrackDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun BioSection(artistName: String, bio: String, expanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Text(
            text = "About $artistName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = bio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SimilarArtistsSection(
    similar: List<SimilarArtistDto>,
    coverArtUrl: (String) -> String?,
    onArtistClick: (SimilarArtistDto) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Similar Artists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(similar, key = { it.id }) { sArtist ->
                Column(
                    modifier = Modifier.width(100.dp).clickable { onArtistClick(sArtist) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val url = sArtist.coverArt?.let { coverArtUrl(it) }
                    Box(
                        modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (url != null) {
                            AsyncImage(model = url, contentDescription = sArtist.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
                        } else {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = sArtist.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
