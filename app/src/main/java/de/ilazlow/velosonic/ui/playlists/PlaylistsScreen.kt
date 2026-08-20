package de.ilazlow.velosonic.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.datastore.PlaylistFilterMode
import de.ilazlow.velosonic.data.datastore.PlaylistSortOrder
import de.ilazlow.velosonic.data.datastore.PlaylistViewMode
import de.ilazlow.velosonic.data.db.PlaylistEntity
import de.ilazlow.velosonic.ui.common.CoverArtTile

/**
 * Mirrors PlaylistsView.swift: "My Playlists"/"Shared Playlists" sections, a search field, a
 * separate filter button (all/downloaded + by-owner) and an overflow menu holding the list/grid
 * view-mode toggle, the 5-key sort menu (name/date created/last modified/duration/song count,
 * tap-again-to-flip-direction), and "New Playlist" — the last one tucked in there rather than a
 * floating action button.
 */
private val PlaylistSortOrder.label: String
    @Composable get() = when (this) {
        PlaylistSortOrder.NAME -> stringResource(R.string.playlists_sort_name)
        PlaylistSortOrder.DATE_CREATED -> stringResource(R.string.playlists_sort_date_created)
        PlaylistSortOrder.LAST_MODIFIED -> stringResource(R.string.playlists_sort_last_modified)
        PlaylistSortOrder.DURATION -> stringResource(R.string.playlists_sort_duration)
        PlaylistSortOrder.SONG_COUNT -> stringResource(R.string.playlists_sort_song_count)
    }
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (String) -> Unit,
    onLikedSongsClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val visibleServers by viewModel.visibleServers.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val viewSettings by viewModel.viewSettings.collectAsStateWithLifecycle()
    val uniqueOwners by viewModel.uniqueOwners.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PlaylistEntity?>(null) }

    // Mirrors iOS's `showLikedSongsRow()` — always shown unless the user is actively searching
    // for something that doesn't match "Liked Songs" by name (not gated on whether anything is
    // actually starred; an empty Liked Songs still opens to its own empty state).
    val likedSongsRowTitle = stringResource(R.string.playlists_liked_songs_row_title)
    val showLikedSongsRow = searchText.isBlank() || likedSongsRowTitle.contains(searchText, ignoreCase = true)

    val filtered = remember(playlists, searchText) {
        if (searchText.isBlank()) playlists else playlists.filter { it.name.contains(searchText, ignoreCase = true) }
    }
    val mine = remember(filtered) { filtered.filter(viewModel::isOwnedByMe) }
    val shared = remember(filtered) { filtered.filterNot(viewModel::isOwnedByMe) }
    val serverGroups = remember(filtered, visibleServers) {
        if (visibleServers.size > 1) viewModel.groupedByServer(filtered, visibleServers) else emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.playlists_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                val filterActive = viewSettings.filterMode != PlaylistFilterMode.ALL || viewSettings.ownerFilter != null
                IconButton(onClick = { showFilterMenu = true }) {
                    Icon(
                        imageVector = if (filterActive) Icons.Filled.FilterAlt else Icons.Outlined.FilterAlt,
                        contentDescription = stringResource(R.string.playlists_filter_content_description)
                    )
                }
                DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlists_filter_all)) },
                        leadingIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                        trailingIcon = {
                            if (viewSettings.filterMode == PlaylistFilterMode.ALL) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = { showFilterMenu = false; viewModel.setFilterMode(PlaylistFilterMode.ALL) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlists_filter_downloaded)) },
                        leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                        trailingIcon = {
                            if (viewSettings.filterMode == PlaylistFilterMode.DOWNLOADED) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = { showFilterMenu = false; viewModel.setFilterMode(PlaylistFilterMode.DOWNLOADED) }
                    )
                    if (uniqueOwners.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.playlists_filter_by_owner_header),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlists_filter_all_owners)) },
                            trailingIcon = {
                                if (viewSettings.ownerFilter == null) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = { showFilterMenu = false; viewModel.clearOwnerFilter() }
                        )
                        uniqueOwners.forEach { owner ->
                            DropdownMenuItem(
                                text = { Text(owner) },
                                trailingIcon = {
                                    if (viewSettings.ownerFilter == owner) Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                onClick = { showFilterMenu = false; viewModel.selectOwnerFilter(owner) }
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlists_new_playlist)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { showOverflowMenu = false; showCreateSheet = true }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlists_playlist_import_menu_item)) },
                        leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                        onClick = { showOverflowMenu = false; onImportClick() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlists_list_view)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                        trailingIcon = {
                            if (viewSettings.viewMode == PlaylistViewMode.LIST) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = { showOverflowMenu = false; viewModel.setViewMode(PlaylistViewMode.LIST) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlists_grid_view)) },
                        leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null) },
                        trailingIcon = {
                            if (viewSettings.viewMode == PlaylistViewMode.GRID) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = { showOverflowMenu = false; viewModel.setViewMode(PlaylistViewMode.GRID) }
                    )
                    HorizontalDivider()
                    PlaylistSortOrder.entries.forEach { order ->
                        val isSelected = viewSettings.sortOrder == order
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = if (viewSettings.sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                        contentDescription = null
                                    )
                                }
                            },
                            onClick = { showOverflowMenu = false; viewModel.selectSortOrder(order) }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text(stringResource(R.string.playlists_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
        )

        if (playlists.isEmpty() && !showLikedSongsRow) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.playlists_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (viewSettings.viewMode == PlaylistViewMode.GRID) {
            PlaylistGridContent(
                mine = mine,
                shared = shared,
                serverGroups = serverGroups,
                showLikedSongsRow = showLikedSongsRow,
                onLikedSongsClick = onLikedSongsClick,
                coverArtUrl = { p -> viewModel.coverArtUrl(p.serverHost, p.coverArt) },
                downloadStatus = { p -> viewModel.downloadStatus(p, downloads) },
                onPlaylistClick = onPlaylistClick,
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                onTogglePin = { viewModel.togglePin(it) }
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (showLikedSongsRow) {
                    item { LikedSongsRow(onClick = onLikedSongsClick) }
                }
                if (serverGroups.isNotEmpty()) {
                    serverGroups.forEach { group ->
                        val serverLabel = group.server.name.ifBlank { group.server.host }
                        if (group.mine.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.playlists_section_server_my, serverLabel)) }
                            items(group.mine, key = { it.id }) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    coverArtUrl = viewModel.coverArtUrl(playlist.serverHost, playlist.coverArt),
                                    showOwner = false,
                                    downloadStatus = viewModel.downloadStatus(playlist, downloads),
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onRename = { renameTarget = playlist },
                                    onDelete = { deleteTarget = playlist },
                                    onTogglePin = { viewModel.togglePin(playlist) }
                                )
                            }
                        }
                        if (group.shared.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.playlists_section_server_shared, serverLabel)) }
                            items(group.shared, key = { it.id }) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    coverArtUrl = viewModel.coverArtUrl(playlist.serverHost, playlist.coverArt),
                                    showOwner = true,
                                    downloadStatus = viewModel.downloadStatus(playlist, downloads),
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onRename = null,
                                    onDelete = null,
                                    onTogglePin = { viewModel.togglePin(playlist) }
                                )
                            }
                        }
                    }
                } else {
                    if (mine.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.playlists_section_my)) }
                        items(mine, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                coverArtUrl = viewModel.coverArtUrl(playlist.serverHost, playlist.coverArt),
                                showOwner = false,
                                downloadStatus = viewModel.downloadStatus(playlist, downloads),
                                onClick = { onPlaylistClick(playlist.id) },
                                onRename = { renameTarget = playlist },
                                onDelete = { deleteTarget = playlist },
                                onTogglePin = { viewModel.togglePin(playlist) }
                            )
                        }
                    }
                    if (shared.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.playlists_section_shared)) }
                        items(shared, key = { it.id }) { playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                coverArtUrl = viewModel.coverArtUrl(playlist.serverHost, playlist.coverArt),
                                showOwner = true,
                                downloadStatus = viewModel.downloadStatus(playlist, downloads),
                                onClick = { onPlaylistClick(playlist.id) },
                                onRename = null,
                                onDelete = null,
                                onTogglePin = { viewModel.togglePin(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateEditPlaylistSheet(
            isEdit = false,
            configs = viewModel.configs(),
            onDismiss = { showCreateSheet = false },
            onConfirm = { name, isPublic, serverHost, _, _, isSmart, rulesJson ->
                showCreateSheet = false
                viewModel.createPlaylist(name, isPublic, serverHost, isSmart, rulesJson) { }
            }
        )
    }

    renameTarget?.let { playlist ->
        CreateEditPlaylistSheet(
            isEdit = true,
            initialName = playlist.name,
            initialPublic = playlist.publicStatus ?: false,
            currentCoverArtUrl = viewModel.coverArtUrl(playlist.serverHost, playlist.coverArt),
            showArtworkPicker = viewModel.supportsArtworkUpload(playlist),
            isSmartPlaylist = viewModel.isSmartPlaylistEditable(playlist),
            smartRulesJson = playlist.rulesJSON,
            fetchSmartRules = { viewModel.fetchSmartRules(playlist) },
            onDismiss = { renameTarget = null },
            onConfirm = { name, isPublic, _, coverBytes, coverMimeType, isSmart, rulesJson ->
                viewModel.renamePlaylist(playlist, name, isPublic, coverBytes, coverMimeType, if (isSmart) rulesJson else null)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.playlists_delete_dialog_title, playlist.name)) },
            text = { Text(stringResource(R.string.playlists_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePlaylist(playlist); deleteTarget = null }) {
                    Text(stringResource(R.string.playlists_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.playlists_cancel)) }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

/** The "Liked Songs" pseudo-playlist row — mirrors iOS's `LikedSongsRow`/`LikedSongsGridItem`
 *  (both rendered as this same full-width row shape, even in grid mode): a pink→red gradient
 *  tile with a heart glyph instead of cover art, distinguishing it from every real playlist. */
@Composable
private fun LikedSongsRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF69B4).copy(alpha = 0.8f), Color(0xFFFF3B30).copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(text = stringResource(R.string.playlists_liked_songs_row_title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    coverArtUrl: String?,
    showOwner: Boolean,
    downloadStatus: PlaylistDownloadStatus = PlaylistDownloadStatus.NONE,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onTogglePin: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box {
            CoverArtTile(
                url = coverArtUrl,
                contentDescription = playlist.name,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp))
            )
            if (downloadStatus != PlaylistDownloadStatus.NONE) {
                Icon(
                    imageVector = if (downloadStatus == PlaylistDownloadStatus.DOWNLOADED) Icons.Filled.CheckCircle else Icons.Filled.FileDownload,
                    contentDescription = null,
                    tint = if (downloadStatus == PlaylistDownloadStatus.DOWNLOADED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(1.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (playlist.isPinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(text = playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val durationHourMinFormat = stringResource(R.string.playlists_duration_hr_min)
            val durationMinOnlyFormat = stringResource(R.string.playlists_duration_min_only)
            val songCountDuration = stringResource(
                R.string.playlists_song_count_duration,
                playlist.songCount,
                formatPlaylistDuration(playlist.duration, durationHourMinFormat, durationMinOnlyFormat)
            )
            val caption = if (showOwner && playlist.owner != null) {
                songCountDuration + stringResource(R.string.playlists_caption_owner_suffix, playlist.owner)
            } else {
                songCountDuration
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (playlist.isPinned) stringResource(R.string.playlists_unpin) else stringResource(R.string.playlists_pin)) },
                    leadingIcon = { Icon(if (playlist.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null) },
                    onClick = { showMenu = false; onTogglePin() }
                )
                if (onRename != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.playlists_rename)) }, onClick = { showMenu = false; onRename() })
                }
                if (onDelete != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.playlists_delete_action)) }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridContent(
    mine: List<PlaylistEntity>,
    shared: List<PlaylistEntity>,
    serverGroups: List<ServerPlaylistGroup>,
    showLikedSongsRow: Boolean,
    onLikedSongsClick: () -> Unit,
    coverArtUrl: (PlaylistEntity) -> String?,
    downloadStatus: (PlaylistEntity) -> PlaylistDownloadStatus,
    onPlaylistClick: (String) -> Unit,
    onRename: (PlaylistEntity) -> Unit,
    onDelete: (PlaylistEntity) -> Unit,
    onTogglePin: (PlaylistEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (showLikedSongsRow) {
            item(span = { GridItemSpan(maxLineSpan) }) { LikedSongsRow(onClick = onLikedSongsClick) }
        }
        if (serverGroups.isNotEmpty()) {
            serverGroups.forEach { group ->
                val serverLabel = group.server.name.ifBlank { group.server.host }
                if (group.mine.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.playlists_section_server_my, serverLabel)) }
                    gridItems(group.mine, key = { it.id }) { playlist ->
                        PlaylistGridItem(
                            playlist = playlist,
                            coverArtUrl = coverArtUrl(playlist),
                            downloadStatus = downloadStatus(playlist),
                            onClick = { onPlaylistClick(playlist.id) },
                            onRename = { onRename(playlist) },
                            onDelete = { onDelete(playlist) },
                            onTogglePin = { onTogglePin(playlist) }
                        )
                    }
                }
                if (group.shared.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.playlists_section_server_shared, serverLabel)) }
                    gridItems(group.shared, key = { it.id }) { playlist ->
                        PlaylistGridItem(
                            playlist = playlist,
                            coverArtUrl = coverArtUrl(playlist),
                            downloadStatus = downloadStatus(playlist),
                            onClick = { onPlaylistClick(playlist.id) },
                            onRename = null,
                            onDelete = null,
                            onTogglePin = { onTogglePin(playlist) }
                        )
                    }
                }
            }
        } else {
            if (mine.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.playlists_section_my)) }
                gridItems(mine, key = { it.id }) { playlist ->
                    PlaylistGridItem(
                        playlist = playlist,
                        coverArtUrl = coverArtUrl(playlist),
                        downloadStatus = downloadStatus(playlist),
                        onClick = { onPlaylistClick(playlist.id) },
                        onRename = { onRename(playlist) },
                        onDelete = { onDelete(playlist) },
                        onTogglePin = { onTogglePin(playlist) }
                    )
                }
            }
            if (shared.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.playlists_section_shared)) }
                gridItems(shared, key = { it.id }) { playlist ->
                    PlaylistGridItem(
                        playlist = playlist,
                        coverArtUrl = coverArtUrl(playlist),
                        downloadStatus = downloadStatus(playlist),
                        onClick = { onPlaylistClick(playlist.id) },
                        onRename = null,
                        onDelete = null,
                        onTogglePin = { onTogglePin(playlist) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridItem(
    playlist: PlaylistEntity,
    coverArtUrl: String?,
    downloadStatus: PlaylistDownloadStatus,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onTogglePin: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { showMenu = true }),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            CoverArtTile(
                url = coverArtUrl,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            )
            if (playlist.isPinned) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(14.dp)
                )
            }
            if (downloadStatus != PlaylistDownloadStatus.NONE) {
                Icon(
                    imageVector = if (downloadStatus == PlaylistDownloadStatus.DOWNLOADED) Icons.Filled.CheckCircle else Icons.Filled.FileDownload,
                    contentDescription = null,
                    tint = if (downloadStatus == PlaylistDownloadStatus.DOWNLOADED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (playlist.isPinned) stringResource(R.string.playlists_unpin) else stringResource(R.string.playlists_pin)) },
                    leadingIcon = { Icon(if (playlist.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null) },
                    onClick = { showMenu = false; onTogglePin() }
                )
                if (onRename != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.playlists_rename)) }, onClick = { showMenu = false; onRename() })
                }
                if (onDelete != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.playlists_delete_action)) }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.playlists_song_count_only, playlist.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatPlaylistDuration(totalSeconds: Int, hourMinFormat: String, minOnlyFormat: String): String {
    val totalMinutes = (totalSeconds / 60).coerceAtLeast(if (totalSeconds > 0) 1 else 0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) hourMinFormat.format(hours, minutes) else minOnlyFormat.format(minutes)
}
