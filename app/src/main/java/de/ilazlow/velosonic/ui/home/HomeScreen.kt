package de.ilazlow.velosonic.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.db.AlbumEntity
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.data.db.ServerConfigEntity
import de.ilazlow.velosonic.playback.NowPlaying
import de.ilazlow.velosonic.ui.common.AlbumGridItem
import de.ilazlow.velosonic.ui.common.CoverArtTile
import de.ilazlow.velosonic.ui.library.DownloadQueueScreen
import de.ilazlow.velosonic.ui.player.QueueScreen

/**
 * Mirrors HomeView.swift: connectivity banner, tappable download-progress banner, "Up Next"
 * card, recently-played-playlists row, then the four album carousels — plus a title bar with a
 * multi-server filter menu and pull-to-refresh, none of which existed on Android before. The
 * download-queue and "Up Next" sheets are local overlays (same pattern as [DownloadsScreen] and
 * [de.ilazlow.velosonic.ui.player.PlayerScreen]), not NavHost destinations.
 */
@Composable
fun HomeScreen(
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val animatedGrid by viewModel.animatedAlbumGridEnabled.collectAsStateWithLifecycle()
    val recentPlaylists by viewModel.recentPlaylists.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isFetchingQueue by viewModel.isFetchingQueue.collectAsStateWithLifecycle()

    var showServerMenu by remember { mutableStateOf(false) }
    var showDownloadQueue by remember { mutableStateOf(false) }
    var showUpNext by remember { mutableStateOf(false) }

    // LazyListScope's builder lambda is not itself a composable context — item {} / items {}
    // blocks are — so string resources must be resolved up here, not inline in the calls below.
    val recentlyPlayedTitle = stringResource(id = R.string.home_recently_played)
    val recentlyAddedTitle = stringResource(id = R.string.home_recently_added)
    val frequentlyPlayedTitle = stringResource(id = R.string.home_frequently_played)
    val surpriseMeTitle = stringResource(id = R.string.home_surprise_me)
    val recentPlaylistsTitle = stringResource(id = R.string.home_recent_playlists)
    val upNextTitle = stringResource(id = R.string.home_up_next)
    val upNextTracksLabel = stringResource(id = R.string.home_up_next_tracks, nowPlaying.queue.size)
    val downloadingTrackWord = stringResource(id = R.string.home_downloading_track)
    val downloadingTracksWord = stringResource(id = R.string.home_downloading_tracks)
    val downloadingLabel = stringResource(
        id = R.string.home_downloading,
        activeDownloadCount,
        if (activeDownloadCount == 1) downloadingTrackWord else downloadingTracksWord
    )
    val combinedLabel = stringResource(id = R.string.home_server_filter_combined)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (servers.size > 1) {
                    val visibleCount = servers.count { it.isVisible }
                    Box {
                        TextButton(onClick = { showServerMenu = true }) {
                            Text(if (visibleCount == servers.size) combinedLabel else "$visibleCount/${servers.size}")
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = showServerMenu, onDismissRequest = { showServerMenu = false }) {
                            servers.forEach { config ->
                                DropdownMenuItem(
                                    text = { Text(config.name.ifBlank { config.host }) },
                                    trailingIcon = {
                                        Switch(
                                            checked = config.isVisible,
                                            onCheckedChange = { viewModel.setServerVisible(config, it) }
                                        )
                                    },
                                    onClick = { viewModel.setServerVisible(config, !config.isVisible) }
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = { showDownloadQueue = true }) {
                    Icon(
                        Icons.Filled.Downloading,
                        contentDescription = stringResource(id = R.string.home_downloads_content_description),
                        tint = if (activeDownloadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Mirrors iOS's Home toolbar icon exactly — NOT a library refresh (pull-to-refresh
                // below already covers that, matching `.refreshable`): this loads whatever play
                // queue/position was last saved server-side via the OpenSubsonic play-queue
                // extension, so playback can resume where another device left off.
                IconButton(onClick = viewModel::loadQueueFromServer, enabled = !isFetchingQueue) {
                    if (isFetchingQueue) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(id = R.string.home_load_queue_content_description), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (activeDownloadCount > 0) {
                        item { DownloadProgressBanner(label = downloadingLabel, onClick = { showDownloadQueue = true }) }
                    }
                    if (nowPlaying.track != null && nowPlaying.queue.isNotEmpty()) {
                        item {
                            UpNextCard(
                                nowPlaying = nowPlaying,
                                title = upNextTitle,
                                tracksLabel = upNextTracksLabel,
                                coverArtUrl = viewModel::coverArtUrl,
                                onClick = { showUpNext = true }
                            )
                        }
                    }
                    if (recentPlaylists.isNotEmpty()) {
                        playlistSection(
                            title = recentPlaylistsTitle,
                            playlists = recentPlaylists,
                            coverArtUrl = viewModel::coverArtUrl,
                            onPlaylistClick = onPlaylistClick
                        )
                    }
                    if (sections.recentlyPlayed.isNotEmpty()) {
                        albumSection(
                            title = recentlyPlayedTitle,
                            albums = sections.recentlyPlayed,
                            coverArtUrl = viewModel::coverArtUrl,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            serverBadgeLabel = { host -> serverBadgeFor(servers, host) },
                            animate = animatedGrid
                        )
                    }
                    if (sections.recentlyAdded.isNotEmpty()) {
                        albumSection(
                            title = recentlyAddedTitle,
                            albums = sections.recentlyAdded,
                            coverArtUrl = viewModel::coverArtUrl,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            serverBadgeLabel = { host -> serverBadgeFor(servers, host) },
                            animate = animatedGrid
                        )
                    }
                    if (sections.frequentlyPlayed.isNotEmpty()) {
                        albumSection(
                            title = frequentlyPlayedTitle,
                            albums = sections.frequentlyPlayed,
                            coverArtUrl = viewModel::coverArtUrl,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            serverBadgeLabel = { host -> serverBadgeFor(servers, host) },
                            animate = animatedGrid
                        )
                    }
                    if (sections.random.isNotEmpty()) {
                        albumSection(
                            title = surpriseMeTitle,
                            albums = sections.random,
                            coverArtUrl = viewModel::coverArtUrl,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            serverBadgeLabel = { host -> serverBadgeFor(servers, host) },
                            animate = animatedGrid
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showDownloadQueue,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            DownloadQueueScreen(onDismiss = { showDownloadQueue = false })
        }

        AnimatedVisibility(
            visible = showUpNext,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            QueueScreen(onDismiss = { showUpNext = false })
        }
    }
}

@Composable
private fun DownloadProgressBanner(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Downloading, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun UpNextCard(
    nowPlaying: NowPlaying,
    title: String,
    tracksLabel: String,
    coverArtUrl: (String, String?, Int) -> String?,
    onClick: () -> Unit
) {
    val track = nowPlaying.track
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverArtTile(
            url = track?.let { coverArtUrl(it.serverHost, it.coverArt, 100) },
            contentDescription = track?.title,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = track?.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = tracksLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [servers] not visible here — the app's own visible-hosts filtering already happened upstream
 *  in [de.ilazlow.velosonic.data.LibraryRepository], so this only needs the full server list to
 *  decide whether a badge is warranted at all (iOS: `allConfigs.count > 1`) and to resolve a
 *  display name for one host. */
private fun serverBadgeFor(servers: List<ServerConfigEntity>, host: String): String? {
    if (servers.size <= 1) return null
    val config = servers.firstOrNull { it.host == host } ?: return host
    return config.name.ifBlank { host }
}

private fun androidx.compose.foundation.lazy.LazyListScope.albumSection(
    title: String,
    albums: List<AlbumEntity>,
    coverArtUrl: (String, String?) -> String?,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    serverBadgeLabel: (String) -> String?,
    animate: Boolean
) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Deliberately no key={it.id} here — confirmed live as a real bug, not just a missed
            // optimization: the same album id can legitimately recur across sections (Recently
            // Played vs. Recently Added), and this row's content also flips once, from the local
            // Room-derived fallback to the live per-server fetch's result (see HomeViewModel's
            // two-phase `sections`). With an explicit key, that swap — which inserts a new first
            // item once a second server resolves — left the wrong AlbumEntity bound to the first
            // visible slot (both its text AND its click target), while every log at every layer
            // (repository, ViewModel, this composable) confirmed the correct album was actually
            // first in the list. Positional (unkeyed) items sidestep whatever in Compose's keyed
            // slot-reuse was misattributing that slot.
            items(albums) { album ->
                AlbumGridItem(
                    title = album.name,
                    artist = album.artistName,
                    year = album.year,
                    coverArtUrl = coverArtUrl(album.serverHost, album.coverArt),
                    onClick = { onAlbumClick(album.id) },
                    onArtistClick = album.artistCompositeId?.let { artistId ->
                        { onArtistClick(artistId, album.artistName) }
                    },
                    serverBadgeLabel = serverBadgeLabel(album.serverHost),
                    modifier = Modifier.width(140.dp),
                    animate = animate
                )
            }
        }
    }
}

/** Mirrors `RecentPlaylistsSection.swift`'s shape exactly — NOT the vertical cover-over-title
 *  carousel cards the album sections use: a 2-column grid of horizontal cards (square cover
 *  left, name filling the rest), capped at 8. Built as manually-chunked `Row`s rather than a
 *  nested `LazyVerticalGrid` — this whole section is already one item in the outer `LazyColumn`,
 *  and a lazy grid nested inside a lazy column needs an explicit height to lay out at all. */
private fun androidx.compose.foundation.lazy.LazyListScope.playlistSection(
    title: String,
    playlists: List<PlaylistEntity>,
    coverArtUrl: (String, String?, Int) -> String?,
    onPlaylistClick: (String) -> Unit
) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            playlists.take(8).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { playlist ->
                        RecentPlaylistCard(
                            playlist = playlist,
                            coverArtUrl = coverArtUrl(playlist.serverHost, playlist.coverArt, 120),
                            onClick = { onPlaylistClick(playlist.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size < 2) Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentPlaylistCard(
    playlist: PlaylistEntity,
    coverArtUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtTile(
            url = coverArtUrl,
            contentDescription = playlist.name,
            modifier = Modifier.size(52.dp)
        )
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
