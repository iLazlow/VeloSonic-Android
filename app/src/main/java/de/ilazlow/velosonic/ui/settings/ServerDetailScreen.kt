package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.network.dto.NowPlayingEntryDto
import de.ilazlow.velosonic.data.network.dto.ScanStatusDto
import de.ilazlow.velosonic.ui.common.CoverArtTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Mirrors ServerDetailView.swift: connection info, admin-only Quick/Full Scan actions (a
 *  server-side library rescan of files on disk — distinct from this app's own local sync) with
 *  live scan-status polling, an admin-only Now Playing panel (every user's currently playing
 *  track, server-wide, position extrapolated live between 5s polls), Local Database counters
 *  scoped to this host, and Edit/Delete actions. The admin sections are gated on
 *  [de.ilazlow.velosonic.data.db.ServerConfigEntity.isAdmin], re-checked against the server every
 *  time this screen opens (see [ServerDetailViewModel.onAdminScreenShown]) rather than trusted
 *  from whatever was guessed at add-time. */
@Composable
fun ServerDetailScreen(host: String, onBack: () -> Unit, viewModel: ServerDetailViewModel = hiltViewModel()) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    val nowPlayingEntries by viewModel.nowPlayingEntries.collectAsStateWithLifecycle()
    val nowPlayingFetchedAt by viewModel.nowPlayingFetchedAt.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    val config = servers.firstOrNull { it.host == host }

    LaunchedEffect(host) {
        viewModel.loadStats(host)
        viewModel.onAdminScreenShown(host)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(config?.name?.ifBlank { host } ?: host, onBack)
        if (config == null) {
            Text(stringResource(id = R.string.settings_server_detail_not_found), modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSectionHeader(stringResource(id = R.string.settings_server_detail_section_info)) }
            item { InfoRow(stringResource(id = R.string.settings_server_detail_name_label), config.name.ifBlank { config.host }) }
            item { InfoRow(stringResource(id = R.string.settings_server_detail_domain_label), config.host) }
            item { InfoRow(stringResource(id = R.string.settings_server_detail_username_label), config.username) }
            config.serverTypeRaw?.let { item { InfoRow(stringResource(id = R.string.settings_server_detail_server_type_label), it) } }
            config.serverVersion?.let { item { InfoRow(stringResource(id = R.string.settings_server_detail_server_version_label), it) } }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(id = R.string.settings_server_detail_connection_label), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Text(stringResource(id = R.string.settings_server_detail_connected_status), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (config.isAdmin) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(id = R.string.settings_server_detail_section_actions)) }
                item {
                    ScanActionsSection(
                        scanStatus = scanStatus,
                        onQuickScan = { viewModel.triggerScan(host, fullScan = false) },
                        onFullScan = { viewModel.triggerScan(host, fullScan = true) }
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(id = R.string.settings_server_detail_section_now_playing)) }
                if (nowPlayingEntries.isEmpty()) {
                    item {
                        Text(
                            stringResource(id = R.string.settings_server_detail_no_one_listening),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(nowPlayingEntries, key = { "${it.username}_${it.playerId}_${it.songId}" }) { entry ->
                        NowPlayingRow(
                            entry = entry,
                            fetchedAtMs = nowPlayingFetchedAt,
                            coverArtUrl = viewModel.coverArtUrl(host, entry.coverArt ?: entry.albumId, 100)
                        )
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_server_detail_section_local_database)) }
            item { StatRow(stringResource(id = R.string.settings_server_detail_stat_artists), stats.artists) }
            item { StatRow(stringResource(id = R.string.settings_server_detail_stat_albums), stats.albums) }
            item { StatRow(stringResource(id = R.string.settings_server_detail_stat_songs), stats.songs) }
            item { StatRow(stringResource(id = R.string.settings_server_detail_stat_genres), stats.genres) }
            item { StatRow(stringResource(id = R.string.settings_server_detail_stat_playlists), stats.playlists) }
            item { StatRow(stringResource(id = R.string.settings_server_detail_stat_radio_stations), stats.radioStations) }
            item { StatRow(stringResource(id = R.string.settings_server_detail_stat_downloaded_songs), stats.downloadedSongs) }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    OutlinedButton(onClick = { showEditSheet = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(id = R.string.settings_server_detail_edit))
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(id = R.string.settings_server_detail_delete_connection), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(id = R.string.settings_server_detail_remove_dialog_title, config?.name?.ifBlank { host } ?: host)) },
            text = { Text(stringResource(id = R.string.settings_server_detail_remove_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    config?.let(viewModel::removeServer)
                    confirmDelete = false
                    onBack()
                }) {
                    Text(stringResource(id = R.string.settings_server_detail_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(id = R.string.settings_server_detail_cancel)) } }
        )
    }

    if (showEditSheet && config != null) {
        AddServerSheet(onDismiss = { showEditSheet = false }, existingConfig = config)
    }
}

@Composable
private fun ScanActionsSection(
    scanStatus: ScanStatusDto?,
    onQuickScan: () -> Unit,
    onFullScan: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (scanStatus?.scanning == true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = scanStatus.count?.let { stringResource(id = R.string.settings_server_detail_scanning_with_count, it) }
                        ?: stringResource(id = R.string.settings_server_detail_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            scanStatus?.folderCount?.let { InlineStatRow(stringResource(id = R.string.settings_server_detail_remote_folders_label), "$it") }
            scanStatus?.count?.let { InlineStatRow(stringResource(id = R.string.settings_server_detail_remote_songs_label), "$it") }
            InlineStatRow(stringResource(id = R.string.settings_server_detail_last_scan_label), formatLastScan(scanStatus?.lastScan, stringResource(id = R.string.settings_server_detail_never)))
            Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onQuickScan) { Text(stringResource(id = R.string.settings_server_detail_quick_scan)) }
                TextButton(onClick = onFullScan) { Text(stringResource(id = R.string.settings_server_detail_full_scan)) }
            }
        }
    }
}

@Composable
private fun InlineStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatLastScan(raw: String?, neverLabel: String): String {
    if (raw.isNullOrBlank()) return neverLabel
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return raw
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

/** Prefers Navidrome's `playbackReport` extension (`state`/`positionMs`) for a real, growing
 *  position; only falls back to the base-Subsonic `minutesAgo` heuristic on servers without that
 *  extension — `minutesAgo` alone is unusable for a live position since it resets toward 0 on
 *  every now-playing heartbeat re-submission from any client, not just VeloSonic. */
@Composable
private fun NowPlayingRow(entry: NowPlayingEntryDto, fetchedAtMs: Long, coverArtUrl: String?) {
    var tickMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(entry.songId, entry.username) {
        while (isActive) {
            delay(1_000)
            tickMs += 1_000
        }
    }
    val nowMs = fetchedAtMs + tickMs
    val elapsedSeconds = remember(entry, nowMs) {
        val elapsedSincePollSec = ((nowMs - fetchedAtMs).coerceAtLeast(0)) / 1000.0
        val estimated = if (entry.positionMs != null) {
            val basePosition = entry.positionMs / 1000.0
            val isAdvancing = entry.state == null || entry.state == "playing" || entry.state == "starting"
            if (isAdvancing) basePosition + elapsedSincePollSec * (entry.playbackRate ?: 1.0) else basePosition
        } else {
            entry.minutesAgo * 60.0 + elapsedSincePollSec
        }
        val duration = entry.duration
        if (duration != null && duration > 0) estimated.toInt().coerceAtMost(duration) else estimated.toInt()
    }
    val progress = remember(entry, elapsedSeconds) {
        val duration = entry.duration
        if (duration != null && duration > 0) (elapsedSeconds.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverArtTile(
            url = coverArtUrl,
            contentDescription = entry.title,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.artist?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(3.dp))
                Text(
                    text = "${formatSeconds(elapsedSeconds)} / ${formatSeconds(entry.duration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Text(entry.username, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatSeconds(seconds: Int?): String {
    if (seconds == null) return "--:--"
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
