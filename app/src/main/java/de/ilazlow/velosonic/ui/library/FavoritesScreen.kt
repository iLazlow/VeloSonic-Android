package de.ilazlow.velosonic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.ui.common.AlbumGridItem
import de.ilazlow.velosonic.ui.common.ArtistAvatar
import de.ilazlow.velosonic.ui.common.LibrarySearchField
import de.ilazlow.velosonic.ui.common.NowPlayingIndicator
import de.ilazlow.velosonic.ui.common.TrackListRow
import de.ilazlow.velosonic.ui.common.formatTrackDuration
import de.ilazlow.velosonic.ui.settings.SettingsTopBar

/** Mirrors `LibraryFavoritesView.swift`: three independently-hideable sections (Artists carousel,
 *  Albums grid, Songs list) instead of a flat song list, plus a manual "refresh starred" action
 *  (iOS's `syncStarred`) since starred state can otherwise only change on the next scheduled sync. */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onArtistClick: (id: String, name: String) -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val artists by viewModel.starredArtists.collectAsStateWithLifecycle()
    val albums by viewModel.starredAlbums.collectAsStateWithLifecycle()
    val songs by viewModel.starredTracks.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(
            title = stringResource(id = R.string.library_favorites_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = viewModel::refreshStarred, enabled = !isSyncing) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.library_favorites_refresh_content_description))
                    }
                }
            }
        )
        LibrarySearchField(value = searchText, onValueChange = viewModel::onSearchTextChange)

        if (artists.isEmpty() && albums.isEmpty() && songs.isEmpty() && !isSyncing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.Filled.HeartBroken,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(60.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.library_favorites_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.library_favorites_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            if (artists.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(id = R.string.library_favorites_section_artists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(artists, key = { it.id }) { artist ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(80.dp)
                                    .clickable { onArtistClick(artist.id, artist.name) }
                            ) {
                                ArtistAvatar(url = viewModel.avatarUrl(artist), size = 80.dp)
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (albums.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(id = R.string.library_favorites_section_albums),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    FavoriteAlbumsGrid(albums, viewModel::coverArtUrl, onAlbumClick)
                }
            }

            if (songs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(id = R.string.library_favorites_section_songs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    songs.forEach { track ->
                        val isCurrentTrack = nowPlaying.track?.id == track.id
                        TrackListRow(
                            title = track.title,
                            subtitle = track.artistName,
                            coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt ?: track.albumId),
                            onClick = { viewModel.onTrackClick(track) },
                            isCurrentTrack = isCurrentTrack,
                            trailingContent = {
                                if (isCurrentTrack) {
                                    NowPlayingIndicator(isPlaying = nowPlaying.isPlaying)
                                } else {
                                    Text(formatTrackDuration(track.duration))
                                }
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteAlbumsGrid(
    albums: List<AlbumEntity>,
    coverArtUrl: (String, String?, Int) -> String?,
    onAlbumClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        albums.chunked(2).forEach { rowAlbums ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowAlbums.forEach { album ->
                    AlbumGridItem(
                        title = album.name,
                        artist = album.artistName,
                        coverArtUrl = coverArtUrl(album.serverHost, album.coverArt, 300),
                        onClick = { onAlbumClick(album.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowAlbums.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
