package de.ilazlow.velosonic.ui.playlists

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.data.datastore.LikedSongsSortOrder
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.domain.goToArtistTarget
import de.ilazlow.velosonic.ui.common.trackStatusLabel
import de.ilazlow.velosonic.ui.player.SongInfoSheet
import de.ilazlow.velosonic.ui.share.ShareSheet
import de.ilazlow.velosonic.ui.share.ShareTarget

private val LikedSongsSortOrder.label: String
    get() = when (this) {
        LikedSongsSortOrder.DATE_LIKED -> "Date Liked"
        LikedSongsSortOrder.TITLE -> "Title"
        LikedSongsSortOrder.ARTIST -> "Artist"
        LikedSongsSortOrder.DURATION -> "Duration"
    }

/**
 * The "Liked Songs" pseudo-playlist detail screen — mirrors iOS's `LikedSongsView`: same starred-
 * tracks source as Library's Favorites tab, play/shuffle/download-all header, per-track actions
 * via the shared [PlaylistTrackRow] (no remove-from-playlist item, matching iOS's `playlistId:
 * nil` — unstarring via the row's own menu is the only way a track leaves this list). Two
 * deliberate additions beyond iOS parity, explicitly requested since this behaves like a real
 * playlist: a sort menu and a per-server include/exclude filter (iOS has neither for this screen).
 */
@Composable
fun LikedSongsScreen(
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (id: String, name: String) -> Unit,
    viewModel: LikedSongsViewModel = hiltViewModel()
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val visibleServers by viewModel.visibleServers.collectAsStateWithLifecycle()

    var searchText by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var songInfoTarget by remember { mutableStateOf<TrackEntity?>(null) }

    val filteredTracks = remember(tracks, searchText) {
        if (searchText.isBlank()) tracks
        else tracks.filter { it.title.contains(searchText, ignoreCase = true) || it.artistName.contains(searchText, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = "Liked Songs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            // Same widget shape as HomeScreen's own server-filter dropdown (TextButton showing a
            // "N/total" count + dropdown arrow, DropdownMenu of per-server Switch rows) — NOT the
            // same underlying setting, though: Home's toggles the global `ServerConfigEntity.
            // isVisible` (affects every screen), this one only excludes a server from THIS
            // pseudo-playlist's own aggregation (see [LikedSongsSettingsStore.excludedHosts]),
            // since the whole point is filtering within the already-visible set, not changing it.
            if (visibleServers.size > 1) {
                val includedCount = visibleServers.count { it.host !in settings.excludedHosts }
                Box {
                    TextButton(onClick = { showFilterMenu = true }) {
                        Text(if (includedCount == visibleServers.size) "All Servers" else "$includedCount/${visibleServers.size}")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        visibleServers.forEach { server ->
                            val included = server.host !in settings.excludedHosts
                            DropdownMenuItem(
                                text = { Text(server.name.ifBlank { server.host }) },
                                trailingIcon = {
                                    Switch(
                                        checked = included,
                                        onCheckedChange = { viewModel.toggleServerExcluded(server.host) }
                                    )
                                },
                                onClick = { viewModel.toggleServerExcluded(server.host) }
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Sort")
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    LikedSongsSortOrder.entries.forEach { order ->
                        val isSelected = settings.sortOrder == order
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = if (settings.sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = { showSortMenu = false; viewModel.selectSortOrder(order) }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        )

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(60.dp)
                    )
                    Text(
                        text = "No Liked Songs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap the heart on any song to see it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    LikedSongsHeroHeader(
                        trackCount = tracks.size,
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::playShuffled,
                        onDownload = viewModel::downloadAll
                    )
                }
                items(filteredTracks, key = { it.id }) { track ->
                    val downloadState = downloads[track.id]?.state
                    PlaylistTrackRow(
                        track = track,
                        coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt),
                        isCurrentTrack = nowPlaying.track?.id == track.id,
                        isPlaying = nowPlaying.isPlaying,
                        downloadState = downloadState,
                        isCached = viewModel.isTrackCached(track),
                        canReachNetwork = true,
                        onClick = { viewModel.onTrackClick(track) },
                        onToggleStar = { viewModel.toggleTrackFavorite(track) },
                        onPlayNext = { viewModel.playNext(track) },
                        onInstantMix = { viewModel.playInstantMix(track) },
                        onGoToArtist = track.goToArtistTarget()?.let { (id, name) -> { onArtistClick(id, name) } },
                        onGoToAlbum = track.albumCompositeId?.let { id -> { onAlbumClick(id) } },
                        onAddToPlaylist = { addToPlaylistTrack = track },
                        onToggleDownload = { viewModel.toggleTrackDownload(track) },
                        onShare = { shareTarget = ShareTarget(track.serverHost, track.subsonicId, track.title) },
                        onShowInfo = { songInfoTarget = track }
                    )
                }
            }
        }
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

@Composable
private fun LikedSongsHeroHeader(
    trackCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF69B4).copy(alpha = 0.8f), Color(0xFFFF3B30).copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
        }
        Text(
            text = "Liked Songs",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "$trackCount Songs",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

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
            CircleActionButton(onClick = onDownload) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
