package de.ilazlow.velosonic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.ui.common.CoverArtTile

private enum class DownloadCategory(val title: String) {
    PLAYLISTS("Downloaded Playlists"),
    ALBUMS("Downloaded Albums"),
    SONGS("Downloaded Songs"),
    ARTISTS("Downloaded Artists")
}

/** Mirrors LibraryDownloadsView.swift's hub-of-4-categories shape (Playlists/Albums/Songs/
 *  Artists, each a colored icon badge + count), with the four sub-lists navigated via local
 *  state rather than new NavHost routes — a system-back press still closes a sub-list first via
 *  [BackHandler] before leaving the tab, so it doesn't feel like a dead end. */
@Composable
fun DownloadsScreen(
    onPlaylistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (id: String, name: String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasActiveDownloads by viewModel.hasActiveDownloads.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<DownloadCategory?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    BackHandler(enabled = selected != null || showQueue) {
        if (showQueue) showQueue = false else selected = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected != null) {
                IconButton(onClick = { selected = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
            Text(
                text = selected?.title ?: "Downloads",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (selected != null) 4.dp else 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showQueue = true }) {
                Icon(
                    Icons.Filled.Downloading,
                    contentDescription = "Download Queue",
                    tint = if (hasActiveDownloads) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (selected) {
            null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    DownloadCategoryRow(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        color = Color(0xFF9C6ADE),
                        label = "Playlists",
                        count = state.playlists.size,
                        onClick = { selected = DownloadCategory.PLAYLISTS }
                    )
                }
                item {
                    DownloadCategoryRow(
                        icon = Icons.Filled.Album,
                        color = Color(0xFFE08A3E),
                        label = "Albums",
                        count = state.albums.size,
                        onClick = { selected = DownloadCategory.ALBUMS }
                    )
                }
                item {
                    DownloadCategoryRow(
                        icon = Icons.Filled.MusicNote,
                        color = Color(0xFFE0568A),
                        label = "Songs",
                        count = state.songs.size,
                        onClick = { selected = DownloadCategory.SONGS }
                    )
                }
                item {
                    DownloadCategoryRow(
                        icon = Icons.Filled.Mic,
                        color = Color(0xFF3EA0A0),
                        label = "Artists",
                        count = state.artists.size,
                        onClick = { selected = DownloadCategory.ARTISTS }
                    )
                }
            }
            DownloadCategory.PLAYLISTS -> DownloadedPlaylistsList(state.playlists, viewModel::coverArtUrl, onPlaylistClick)
            DownloadCategory.ALBUMS -> DownloadedAlbumsList(state.albums, viewModel::coverArtUrl, onAlbumClick)
            DownloadCategory.SONGS -> DownloadedSongsList(
                songs = state.songs,
                coverArtUrl = viewModel::coverArtUrl,
                onRemove = viewModel::removeDownload,
                onClick = { track -> viewModel.playSongs(state.songs, track) }
            )
            DownloadCategory.ARTISTS -> DownloadedArtistsList(state.artists, onArtistClick)
        }
    }

    AnimatedVisibility(
        visible = showQueue,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        DownloadQueueScreen(onDismiss = { showQueue = false })
    }
    }
}

@Composable
private fun DownloadCategoryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = count.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DownloadedPlaylistsList(playlists: List<PlaylistEntity>, coverArtUrl: (String, String?, Int) -> String?, onClick: (String) -> Unit) {
    EmptyAwareList(playlists.isEmpty(), "No downloaded playlists yet.") {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick(playlist.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CoverArtTile(url = coverArtUrl(playlist.serverHost, playlist.coverArt, 150), contentDescription = playlist.name, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "${playlist.songCount} Songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private enum class AlbumSizeFilter(val label: String) { ALBUMS("Albums"), SINGLES("Singles") }

/** iOS's `AlbumSizeFilter.singleTrackThreshold` — a release with 2 or fewer tracks counts as a
 *  single, not an album; an unknown/zero track count (`songCount == null`) is treated as an album. */
private fun AlbumEntity.isSingle(): Boolean = (songCount ?: Int.MAX_VALUE) <= 2

@Composable
private fun DownloadedAlbumsList(albums: List<AlbumEntity>, coverArtUrl: (String, String?, Int) -> String?, onClick: (String) -> Unit) {
    var filter by remember { mutableStateOf(AlbumSizeFilter.ALBUMS) }
    val filtered = remember(albums, filter) {
        albums.filter { if (filter == AlbumSizeFilter.SINGLES) it.isSingle() else !it.isSingle() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            AlbumSizeFilter.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = filter == option,
                    onClick = { filter = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AlbumSizeFilter.entries.size)
                ) {
                    Text(option.label)
                }
            }
        }
        val emptyMessage = if (filter == AlbumSizeFilter.SINGLES) "No downloaded singles yet." else "No downloaded albums yet."
        EmptyAwareList(filtered.isEmpty(), emptyMessage) {
            items(filtered, key = { it.id }) { album ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onClick(album.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CoverArtTile(url = coverArtUrl(album.serverHost, album.coverArt, 150), contentDescription = album.name, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = album.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedSongsList(
    songs: List<TrackEntity>,
    coverArtUrl: (String, String?, Int) -> String?,
    onRemove: (String) -> Unit,
    onClick: (TrackEntity) -> Unit
) {
    EmptyAwareList(songs.isEmpty(), "No individually downloaded songs yet.") {
        items(songs, key = { it.id }) { track ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick(track) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CoverArtTile(url = coverArtUrl(track.serverHost, track.coverArt, 150), contentDescription = track.title, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = track.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onRemove(track.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DownloadedArtistsList(artists: List<DownloadedArtistGroup>, onArtistClick: (id: String, name: String) -> Unit) {
    EmptyAwareList(artists.isEmpty(), "No downloaded artists yet.") {
        items(artists, key = { it.routeId ?: it.name }) { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (group.routeId != null) Modifier.clickable { onArtistClick(group.routeId, group.name) } else Modifier)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = group.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${group.count} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyAwareList(isEmpty: Boolean, emptyMessage: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    if (isEmpty) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), content = content)
    }
}
