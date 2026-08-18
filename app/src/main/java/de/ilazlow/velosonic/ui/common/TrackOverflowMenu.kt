package de.ilazlow.velosonic.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Direct port of `TrackRow.swift`'s "..." menu — the single canonical track-row overflow menu
 * every song list in the iOS app shares. Same item order: Like/Unlike, Play Next, Instant Mix,
 * [Go to Artist], [Go to Album], Add to Playlist, Share Song, Show Info, Download/Remove Download,
 * and — only when [onRemoveFromPlaylist] is non-null (mirrors iOS's `playlistId != nil`) — a
 * divider then Delete. iOS groups the first three into a compact `ControlGroup` icon strip;
 * `DropdownMenu` has no equivalent construct, so here they're just the first three ordinary
 * items — same order and behavior, different chrome. `Instant Mix`/`Add to Playlist` stay visible
 * but disabled offline (matches iOS's `.disabled`); the Download/Remove Download item is hidden
 * entirely rather than disabled when offline and not already downloaded, since there's nothing a
 * disabled "Download" button could do in that state (matches iOS's `if`/`else if` — no `else`).
 */
@Composable
fun TrackOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isStarred: Boolean,
    onToggleStar: () -> Unit,
    onPlayNext: () -> Unit,
    canReachNetwork: Boolean,
    onInstantMix: () -> Unit,
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onShowInfo: () -> Unit,
    isDownloaded: Boolean,
    onToggleDownload: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(if (isStarred) "Unlike" else "Like") },
            leadingIcon = {
                Icon(
                    imageVector = if (isStarred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isStarred) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            },
            onClick = { onDismissRequest(); onToggleStar() }
        )
        DropdownMenuItem(
            text = { Text("Play Next") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
            onClick = { onDismissRequest(); onPlayNext() }
        )
        DropdownMenuItem(
            text = { Text("Instant Mix") },
            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
            enabled = canReachNetwork,
            onClick = { onDismissRequest(); onInstantMix() }
        )
        if (onGoToArtist != null) {
            DropdownMenuItem(
                text = { Text("Go to Artist") },
                leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                onClick = { onDismissRequest(); onGoToArtist() }
            )
        }
        if (onGoToAlbum != null) {
            DropdownMenuItem(
                text = { Text("Go to Album") },
                leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
                onClick = { onDismissRequest(); onGoToAlbum() }
            )
        }
        DropdownMenuItem(
            text = { Text("Add to Playlist") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
            enabled = canReachNetwork,
            onClick = { onDismissRequest(); onAddToPlaylist() }
        )
        DropdownMenuItem(
            text = { Text("Share Song") },
            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            onClick = { onDismissRequest(); onShare() }
        )
        DropdownMenuItem(
            text = { Text("Show Info") },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
            onClick = { onDismissRequest(); onShowInfo() }
        )
        if (isDownloaded) {
            DropdownMenuItem(
                text = { Text("Remove Download", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { onDismissRequest(); onToggleDownload() }
            )
        } else if (canReachNetwork) {
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                onClick = { onDismissRequest(); onToggleDownload() }
            )
        }
        if (onRemoveFromPlaylist != null) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { onDismissRequest(); onRemoveFromPlaylist() }
            )
        }
    }
}
