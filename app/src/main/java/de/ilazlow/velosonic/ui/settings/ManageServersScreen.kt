package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.ServerStats

/**
 * Mirrors ManageServersView.swift: a Section grouping "Manage Addresses" (the server list itself
 * — see [ServerListScreen], a separate screen exactly like iOS's `ServerListView`) and "Manage
 * Shares" nav rows, a Sync Status section, and aggregate Local Database counters. The server list
 * used to be inlined directly on this screen; split out to match iOS's actual two-screen
 * structure instead of flattening it into one.
 */
@Composable
fun ManageServersScreen(
    onBack: () -> Unit,
    onNavigateToShares: () -> Unit,
    onNavigateToServerList: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    var stats by remember { mutableStateOf(ServerStats()) }

    LaunchedEffect(servers) {
        var aggregate = ServerStats()
        for (config in servers) {
            val s = viewModel.statsFor(config.host)
            aggregate = ServerStats(
                artists = aggregate.artists + s.artists,
                albums = aggregate.albums + s.albums,
                songs = aggregate.songs + s.songs,
                genres = maxOf(aggregate.genres, s.genres),
                playlists = aggregate.playlists + s.playlists,
                radioStations = aggregate.radioStations + s.radioStations,
                downloadedSongs = aggregate.downloadedSongs + s.downloadedSongs
            )
        }
        stats = aggregate
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Manage Servers", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Dns,
                    label = "Manage Addresses",
                    onClick = onNavigateToServerList
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Filled.Share,
                    label = "Manage Shares",
                    onClick = onNavigateToShares
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Sync Status") }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    if (syncState.isSyncing) {
                        Text(syncState.serverProgressLabel.ifEmpty { "Syncing…" }, style = MaterialTheme.typography.bodyMedium)
                        Text(syncState.statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { syncState.progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.padding(top = 2.dp))
                            Text("Up to date", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { viewModel.resyncAll(servers.map { it.host }) }, enabled = !syncState.isSyncing) {
                        Text("Resync")
                    }
                    TextButton(onClick = { viewModel.fullResyncAll(servers.map { it.host }) }, enabled = !syncState.isSyncing) {
                        Text("Full Resync")
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Local Database") }
            item { StatRow("Artists", stats.artists) }
            item { StatRow("Albums", stats.albums) }
            item { StatRow("Songs", stats.songs) }
            item { StatRow("Genres", stats.genres) }
            item { StatRow("Playlists", stats.playlists) }
            item { StatRow("Radio Stations", stats.radioStations) }
            item { StatRow("Downloaded Songs", stats.downloadedSongs) }
        }
    }
}

@Composable
internal fun StatRow(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = "$value", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsNavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
