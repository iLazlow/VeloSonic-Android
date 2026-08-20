package de.ilazlow.velosonic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.ArtistEntity
import de.ilazlow.velosonic.data.db.RadioStationEntity
import de.ilazlow.velosonic.data.db.TrackEntity
import de.ilazlow.velosonic.domain.goToArtistTarget
import de.ilazlow.velosonic.ui.common.CoverArtTile
import de.ilazlow.velosonic.ui.common.ExplicitBadge
import de.ilazlow.velosonic.ui.common.NowPlayingIndicator
import de.ilazlow.velosonic.ui.common.TrackOverflowMenu
import de.ilazlow.velosonic.ui.common.trackStatusLabel
import de.ilazlow.velosonic.ui.player.SongInfoSheet
import de.ilazlow.velosonic.ui.playlists.AddToPlaylistSheet
import de.ilazlow.velosonic.ui.share.ShareSheet
import de.ilazlow.velosonic.ui.share.ShareTarget

/** Mirrors SearchView.swift / SearchViewModel.swift's layout (Top Hits caps: artists 1 / albums 3
 *  / songs 5 / radio 2, recent-searches history) but not its purely-local search — this hits every
 *  visible server's `search3` live with the already-synced local library as a per-host fallback,
 *  see [SearchViewModel]'s doc comment. 300ms debounce before firing, same as iOS's local-filter
 *  debounce. */
@Composable
fun SearchScreen(
    onArtistClick: (id: String, name: String) -> Unit,
    onAlbumClick: (id: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serverNames by viewModel.serverNames.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var addToPlaylistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var songInfoTarget by remember { mutableStateOf<TrackEntity?>(null) }

    fun badgeFor(host: String) = serverNames[host]

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(id = R.string.search_field_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                when {
                    state.isSearching -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    query.isNotEmpty() -> IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(id = R.string.search_query_clear_content_description))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onSubmit(); focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (query.isNotBlank()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(SearchFilter.entries) { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.onFilterSelect(filter) },
                        label = { Text(filter.label()) }
                    )
                }
            }
            Spacer(modifier = Modifier.padding(top = 4.dp))
        }

        when {
            query.isBlank() -> {
                if (state.recentSearches.isEmpty()) {
                    EmptyState(icon = Icons.Filled.Search, text = stringResource(id = R.string.search_empty_no_query))
                } else {
                    RecentSearchesList(
                        terms = state.recentSearches,
                        onTermClick = viewModel::onRecentSearchTap,
                        onRemove = viewModel::removeRecentSearch,
                        onClearAll = viewModel::clearRecentSearches
                    )
                }
            }
            !state.hasResults && state.isSearching -> EmptyState(icon = Icons.Filled.Search, text = stringResource(id = R.string.search_searching))
            !state.hasResults -> EmptyState(icon = Icons.Filled.Search, text = stringResource(id = R.string.search_empty_no_results, query))
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val showArtists = state.filter == SearchFilter.TOP_HITS || state.filter == SearchFilter.ARTISTS
                    val showAlbums = state.filter == SearchFilter.TOP_HITS || state.filter == SearchFilter.ALBUMS
                    val showSongs = state.filter == SearchFilter.TOP_HITS || state.filter == SearchFilter.SONGS
                    val showRadio = state.filter == SearchFilter.TOP_HITS || state.filter == SearchFilter.RADIO
                    val isTopHits = state.filter == SearchFilter.TOP_HITS

                    val artists = if (isTopHits) state.artists.take(1) else state.artists
                    val albums = if (isTopHits) state.albums.take(3) else state.albums
                    val tracks = if (isTopHits) state.tracks.take(5) else state.tracks
                    val radio = if (isTopHits) state.radioStations.take(2) else state.radioStations

                    if (showArtists && artists.isNotEmpty()) {
                        item { SectionHeader(stringResource(id = R.string.search_section_artists)) }
                        items(artists, key = { "artist_${it.id}" }) { artist ->
                            SearchArtistRow(
                                artist = artist,
                                serverBadge = badgeFor(artist.serverHost),
                                onClick = { onArtistClick(artist.id, artist.name) }
                            )
                        }
                    }
                    if (showAlbums && albums.isNotEmpty()) {
                        item { SectionHeader(stringResource(id = R.string.search_section_albums)) }
                        items(albums, key = { "album_${it.id}" }) { album ->
                            SearchAlbumRow(
                                album = album,
                                coverArtUrl = viewModel.coverArtUrl(album.serverHost, album.coverArt),
                                serverBadge = badgeFor(album.serverHost),
                                onClick = { onAlbumClick(album.id) }
                            )
                        }
                    }
                    if (showSongs && tracks.isNotEmpty()) {
                        item { SectionHeader(stringResource(id = R.string.search_section_songs)) }
                        items(tracks, key = { "track_${it.id}" }) { track ->
                            val downloadState = state.downloads[track.id]?.state
                            val isCached = viewModel.isTrackCached(track)
                            SearchTrackRow(
                                track = track,
                                coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt),
                                isCurrentTrack = state.nowPlaying.track?.id == track.id,
                                isPlaying = state.nowPlaying.isPlaying,
                                downloadState = downloadState,
                                isCached = isCached,
                                isUnavailableOffline = !state.canReachNetwork && downloadState != Download.STATE_COMPLETED && !isCached,
                                canReachNetwork = state.canReachNetwork,
                                serverBadge = badgeFor(track.serverHost),
                                onClick = { viewModel.onTrackTap(track) },
                                onToggleStar = { viewModel.toggleTrackFavorite(track) },
                                onPlayNext = { viewModel.playNext(track) },
                                onInstantMix = { viewModel.playInstantMix(track) },
                                onGoToArtist = track.goToArtistTarget()?.let { (id, name) -> { onArtistClick(id, name) } },
                                onGoToAlbum = track.albumCompositeId?.let { id -> { onAlbumClick(id) } },
                                onToggleDownload = { viewModel.toggleTrackDownload(track) },
                                onAddToPlaylist = { addToPlaylistTrack = track },
                                onShare = { shareTarget = ShareTarget(track.serverHost, track.subsonicId, track.title) },
                                onShowInfo = { songInfoTarget = track }
                            )
                        }
                    }
                    if (showRadio && radio.isNotEmpty()) {
                        item { SectionHeader(stringResource(id = R.string.search_section_radio)) }
                        items(radio, key = { "radio_${it.id}" }) { station ->
                            SearchRadioRow(
                                station = station,
                                serverBadge = badgeFor(station.serverHost),
                                isPlaying = state.nowPlaying.radioStation?.id == station.id && state.nowPlaying.isPlaying,
                                onClick = { viewModel.onRadioTap(station) }
                            )
                        }
                    }
                }
            }
        }
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistSheet(track = track, onDismiss = { addToPlaylistTrack = null })
    }
    shareTarget?.let { target ->
        ShareSheet(target = target, onDismiss = { shareTarget = null })
    }
    songInfoTarget?.let { track ->
        SongInfoSheet(
            track = track,
            coverArtUrl = viewModel.coverArtUrl(track.serverHost, track.coverArt, 300),
            storageStatusLabel = trackStatusLabel(
                isDownloaded = state.downloads[track.id]?.state == Download.STATE_COMPLETED,
                isCached = viewModel.isTrackCached(track)
            ),
            onDismiss = { songInfoTarget = null }
        )
    }
}

@Composable
private fun SearchFilter.label(): String = when (this) {
    SearchFilter.TOP_HITS -> stringResource(id = R.string.search_filter_top_hits)
    SearchFilter.ARTISTS -> stringResource(id = R.string.search_filter_artists)
    SearchFilter.ALBUMS -> stringResource(id = R.string.search_filter_albums)
    SearchFilter.SONGS -> stringResource(id = R.string.search_filter_songs)
    SearchFilter.RADIO -> stringResource(id = R.string.search_filter_radio)
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServerBadge(label: String?) {
    if (label == null) return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Composable
private fun RecentSearchesList(
    terms: List<String>,
    onTermClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(id = R.string.search_recent_searches_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClearAll) { Text(stringResource(id = R.string.search_recent_searches_clear_button)) }
            }
        }
        items(terms, key = { it }) { term ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onTermClick(term) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(term, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemove(term) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(id = R.string.search_recent_search_remove_content_description), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchArtistRow(artist: ArtistEntity, serverBadge: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(id = R.string.search_artist_row_type_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ServerBadge(serverBadge)
            }
        }
    }
}

@Composable
private fun SearchAlbumRow(album: AlbumEntity, coverArtUrl: String?, serverBadge: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CoverArtTile(url = coverArtUrl, contentDescription = album.name, modifier = Modifier.size(50.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(album.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ServerBadge(serverBadge)
            }
        }
    }
}

@Composable
private fun SearchRadioRow(station: RadioStationEntity, serverBadge: String?, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(station.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(id = R.string.search_radio_row_type_label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ServerBadge(serverBadge)
            }
        }
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchTrackRow(
    track: TrackEntity,
    coverArtUrl: String?,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    downloadState: Int?,
    isCached: Boolean,
    isUnavailableOffline: Boolean = false,
    canReachNetwork: Boolean,
    serverBadge: String?,
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .alpha(if (isUnavailableOffline) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isCurrentTrack) {
            NowPlayingIndicator(isPlaying = isPlaying, modifier = Modifier.size(44.dp))
        } else {
            CoverArtTile(url = coverArtUrl, contentDescription = track.title, modifier = Modifier.size(44.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(track.artistName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ServerBadge(serverBadge)
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
