package de.ilazlow.velosonic.ui.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.PlaylistSortField
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.domain.goToArtistTarget
import de.ilazlow.velosonic.ui.common.CoverArtTile
import de.ilazlow.velosonic.ui.common.DetailArtwork
import de.ilazlow.velosonic.ui.common.ExplicitBadge
import de.ilazlow.velosonic.ui.common.NowPlayingIndicator
import de.ilazlow.velosonic.ui.common.TrackOverflowMenu
import de.ilazlow.velosonic.ui.common.trackStatusLabel
import de.ilazlow.velosonic.ui.player.SongInfoSheet
import de.ilazlow.velosonic.ui.share.ShareSheet
import de.ilazlow.velosonic.ui.share.ShareTarget
import kotlinx.coroutines.launch

/**
 * Mirrors PlaylistDetailView.swift — deliberately NOT the Album Detail treatment: plain
 * background, no dominant-color wash, no blurred banner (see AlbumDetailScreen's HeroBanner for
 * that other look). 260dp artwork with a plain drop shadow instead. Track rows show per-track
 * artwork (unlike Album Detail's track-number-leading rows), and the action row's Play button is
 * a full-width capsule rather than Album Detail's fixed-width pill.
 */
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (id: String, name: String) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val animatedVideoUri by viewModel.animatedVideoUri.collectAsStateWithLifecycle()
    val animateWebpArtwork by viewModel.animateWebpArtwork.collectAsStateWithLifecycle()
    val canReachNetwork by viewModel.canReachNetwork.collectAsStateWithLifecycle()
    val isFullyDownloaded = tracks.isNotEmpty() && tracks.all { downloads[it.id]?.state == Download.STATE_COMPLETED }

    var searchText by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var songInfoTarget by remember { mutableStateOf<TrackEntity?>(null) }

    val filteredTracks = remember(tracks, searchText) {
        if (searchText.isBlank()) tracks
        else tracks.filter { it.title.contains(searchText, ignoreCase = true) || it.artistName.contains(searchText, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Mirrors PlaylistDetailView.swift's `currentPlayingSDTrackId` — only offered while not
    // searching, since a filtered list may not even contain the row to jump to.
    val jumpTargetIndex = if (searchText.isBlank()) {
        filteredTracks.indexOfFirst { it.id == nowPlaying.track?.id }.takeIf { it >= 0 }
    } else {
        null
    }
    // +2 for the header and search-field items ahead of the track rows in the LazyColumn below.
    val leadingItemCount = 2

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = playlist?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showOverflowMenu = false; showEditSheet = true })
                    HorizontalDivider()
                    SortMenuItem("Sort: Custom", PlaylistSortField.CUSTOM, playlist?.sortField, viewModel::setSortField) { showOverflowMenu = false }
                    SortMenuItem("Sort: Title", PlaylistSortField.TITLE, playlist?.sortField, viewModel::setSortField) { showOverflowMenu = false }
                    SortMenuItem("Sort: Duration", PlaylistSortField.DURATION, playlist?.sortField, viewModel::setSortField) { showOverflowMenu = false }
                    DropdownMenuItem(
                        text = { Text(if (playlist?.isSortReversed == true) "✓ Reverse Order" else "Reverse Order") },
                        onClick = { showOverflowMenu = false; viewModel.toggleSortReversed() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showOverflowMenu = false
                            playlist?.let { shareTarget = ShareTarget(it.serverHost, it.subsonicId, it.name) }
                        }
                    )
                    HorizontalDivider()
                    if (isFullyDownloaded) {
                        DropdownMenuItem(
                            text = { Text("Remove Downloads", color = MaterialTheme.colorScheme.error) },
                            onClick = { showOverflowMenu = false; viewModel.removeAllDownloads() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showOverflowMenu = false; showDeleteDialog = true }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    PlaylistHeader(
                        playlist = playlist,
                        artworkUrl = playlist?.let { viewModel.coverArtUrl(it.serverHost, it.coverArt, 800) },
                        animatedVideoUri = animatedVideoUri,
                        animateWebpArtwork = animateWebpArtwork,
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::playShuffled,
                        isFullyDownloaded = isFullyDownloaded,
                        onDownload = viewModel::downloadPlaylist,
                        onRemoveDownloads = viewModel::removeAllDownloads
                    )
                }
                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                itemsIndexed(filteredTracks, key = { _, track -> track.id }) { _, track ->
                    val downloadState = downloads[track.id]?.state
                    val isCached = viewModel.isTrackCached(track)
                    PlaylistTrackRow(
                        track = track,
                        coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt, 150),
                        isCurrentTrack = nowPlaying.track?.id == track.id,
                        isPlaying = nowPlaying.isPlaying,
                        downloadState = downloadState,
                        isCached = isCached,
                        isUnavailableOffline = !canReachNetwork && downloadState != Download.STATE_COMPLETED && !isCached,
                        canReachNetwork = canReachNetwork,
                        onClick = { viewModel.onTrackClick(track) },
                        onToggleStar = { viewModel.toggleTrackFavorite(track) },
                        onPlayNext = { viewModel.playNext(track) },
                        onInstantMix = { viewModel.playInstantMix(track) },
                        onGoToArtist = track.goToArtistTarget()?.let { (id, name) -> { onArtistClick(id, name) } },
                        onGoToAlbum = track.albumCompositeId?.let { id -> { onAlbumClick(id) } },
                        onAddToPlaylist = { addToPlaylistTrack = track },
                        onRemove = { viewModel.removeTrack(track) },
                        onToggleDownload = { viewModel.toggleTrackDownload(track) },
                        onShare = { shareTarget = ShareTarget(track.serverHost, track.subsonicId, track.title) },
                        onShowInfo = { songInfoTarget = track }
                    )
                }
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            text = "No tracks in this playlist yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            JumpToCurrentSongButton(
                visible = jumpTargetIndex != null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
                onClick = {
                    jumpTargetIndex?.let { target ->
                        scope.launch {
                            val viewportHeight = listState.layoutInfo.viewportSize.height
                            listState.animateScrollToItem(target + leadingItemCount, scrollOffset = -(viewportHeight / 2))
                        }
                    }
                }
            )
        }
    }

    if (showEditSheet) {
        playlist?.let { p ->
            CreateEditPlaylistSheet(
                isEdit = true,
                initialName = p.name,
                initialPublic = p.publicStatus ?: false,
                currentCoverArtUrl = viewModel.coverArtUrlForEdit(),
                showArtworkPicker = viewModel.supportsArtworkUpload(),
                isSmartPlaylist = viewModel.isSmartPlaylistEditable(),
                smartRulesJson = viewModel.smartRulesJsonForEdit(),
                fetchSmartRules = { viewModel.fetchSmartRules() },
                onDismiss = { showEditSheet = false },
                onConfirm = { name, isPublic, _, coverBytes, coverMimeType, isSmart, rulesJson ->
                    viewModel.rename(name, isPublic, coverBytes, coverMimeType, if (isSmart) rulesJson else null)
                    showEditSheet = false
                }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete \"${playlist?.name.orEmpty()}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete(onDeleted = onBack) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    shareTarget?.let { target ->
        ShareSheet(target = target, onDismiss = { shareTarget = null })
    }
    addToPlaylistTrack?.let { track ->
        AddToPlaylistSheet(track = track, onDismiss = { addToPlaylistTrack = null })
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

/**
 * Mirrors PlaylistDetailView.swift's "scope" overlay button — appears bottom-trailing only while
 * the playlist's currently-playing track is both present and unfiltered, and animate-scrolls the
 * list to center it when tapped.
 */
@Composable
private fun JumpToCurrentSongButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(tween(300)) + fadeIn(tween(300)),
        exit = scaleOut(tween(300)) + fadeOut(tween(300))
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.GpsFixed, contentDescription = "Jump to current song", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    field: PlaylistSortField,
    current: PlaylistSortField?,
    onSelect: (PlaylistSortField) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(if (current == field) "✓ $label" else label) },
        onClick = { onSelect(field); onDismiss() }
    )
}

@Composable
private fun PlaylistHeader(
    playlist: PlaylistEntity?,
    artworkUrl: String?,
    animatedVideoUri: String?,
    animateWebpArtwork: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isFullyDownloaded: Boolean,
    onDownload: () -> Unit,
    onRemoveDownloads: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DetailArtwork(
            staticUrl = artworkUrl,
            videoUri = animatedVideoUri,
            contentDescription = playlist?.name,
            animateWebp = animateWebpArtwork,
            modifier = Modifier
                .size(260.dp)
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(12.dp), ambientColor = androidx.compose.ui.graphics.Color.Black)
                .clip(RoundedCornerShape(12.dp))
        )
        Text(
            text = playlist?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp)
        )
        Text(
            text = playlist?.owner ?: "System",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (playlist != null) {
            Text(
                text = "${playlist.songCount} Songs • ${formatPlaylistHeaderDuration(playlist.duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 30.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircleActionButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Play", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            }
            CircleActionButton(onClick = if (isFullyDownloaded) onRemoveDownloads else onDownload) {
                Icon(
                    if (isFullyDownloaded) Icons.Filled.Delete else Icons.Filled.FileDownload,
                    contentDescription = null,
                    tint = if (isFullyDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CircleActionButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun PlaylistTrackRow(
    track: TrackEntity,
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
    onAddToPlaylist: () -> Unit,
    onToggleDownload: () -> Unit,
    onShare: () -> Unit,
    onShowInfo: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isUnavailableOffline, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
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
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                onToggleDownload = onToggleDownload,
                onRemoveFromPlaylist = onRemove
            )
        }
    }
}

private fun formatTrackDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatPlaylistHeaderDuration(totalSeconds: Int): String {
    val totalMinutes = (totalSeconds / 60).coerceAtLeast(if (totalSeconds > 0) 1 else 0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}
