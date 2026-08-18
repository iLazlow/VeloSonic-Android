package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.datastore.DownloadNetworkPolicy
import de.ilazlow.velosonic.data.datastore.formatTranscodingSummary
import java.util.Locale

/**
 * Mirrors StorageSettingsView.swift's full row order: size-reporting + clear-actions (Stream
 * Cache / Downloads / Artwork Cache / Clear All), Maintenance (cleanup orphans / verify
 * playlists), Cache Limit slider, Pre-Caching, and Download Quality. Downloaded content's artwork
 * is never at risk from "Clear Artwork Cache" — that's a separate, permanent store, see
 * [de.ilazlow.velosonic.data.artwork.PermanentArtworkStore].
 */
@Composable
fun StorageSettingsScreen(onBack: () -> Unit, onNavigateToDownloadTranscoding: () -> Unit, viewModel: StorageSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val streamCacheBytes by viewModel.streamCacheBytes.collectAsStateWithLifecycle()
    val downloadsBytes by viewModel.downloadsBytes.collectAsStateWithLifecycle()
    val artworkCacheBytes by viewModel.artworkCacheBytes.collectAsStateWithLifecycle()
    val maintenanceResult by viewModel.maintenanceResult.collectAsStateWithLifecycle()
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var confirmClearArtwork by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Storage", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSectionHeader("Information") }
            item {
                StorageRow(label = "Temporary Cache", valueLabel = formatBytes(streamCacheBytes), actionLabel = "Clear", onAction = { confirmClearCache = true })
            }
            item {
                StorageRow(label = "Permanent Downloads", valueLabel = formatBytes(downloadsBytes), actionLabel = "Clear", onAction = { confirmClearDownloads = true })
            }
            item {
                StorageRow(label = "Artwork Cache", valueLabel = formatBytes(artworkCacheBytes), actionLabel = "Clear", onAction = { confirmClearArtwork = true })
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { confirmClearAll = true }) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Maintenance") }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Clean Up Orphaned Files", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::cleanUpOrphanedFiles) { Text("Run") }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Verify Downloaded Playlists", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::verifyDownloadedPlaylists) { Text("Run") }
                }
            }
            item {
                Text(
                    text = "Removes stale/duplicate download files and re-checks that fully-downloaded playlists still have every track.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Cache Limit") }
            item {
                SettingsSliderRow(
                    title = "Maximum Stream Cache Size",
                    valueLabel = "${settings.cacheLimitMb} MB",
                    value = settings.cacheLimitMb.toFloat(),
                    valueRange = 100f..10000f,
                    steps = 98,
                    onValueChange = { viewModel.setCacheLimitMb(it.toInt()) }
                )
            }
            item {
                Text(
                    text = "Takes effect the next time the app starts.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Pre-Caching") }
            item {
                SettingsSwitchRow(
                    title = "Pre-Cache Next Songs",
                    checked = settings.preCacheSongsEnabled,
                    onCheckedChange = viewModel::setPreCacheSongsEnabled
                )
            }
            item {
                SettingsSliderRow(
                    title = "Next Songs to Cache",
                    valueLabel = "${settings.songsToCache}",
                    value = settings.songsToCache.toFloat(),
                    valueRange = 1f..20f,
                    steps = 18,
                    onValueChange = { viewModel.setSongsToCache(it.toInt()) }
                )
            }
            item {
                Text(
                    text = "Fetches the next few songs in your queue ahead of time so playback starts instantly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Download Quality") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDownloadTranscoding)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Quality", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = formatTranscodingSummary(settings.downloadFormat, settings.downloadBitrate, settings.customDownloadFormat, settings.customDownloadBitrate, offLabel = "Original"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SettingsPickerRow(
                    label = "Download Via",
                    valueLabel = settings.downloadNetworkPolicy.label,
                    options = DownloadNetworkPolicy.entries,
                    optionLabel = { it.label },
                    onSelect = viewModel::setDownloadNetworkPolicy
                )
            }
        }
    }

    if (maintenanceResult != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearMaintenanceResult,
            title = { Text("Maintenance") },
            text = { Text(maintenanceResult.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearMaintenanceResult) { Text("OK") } }
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Clear Stream Cache?") },
            text = { Text("Frees up space immediately, but works best when nothing is currently playing. Downloaded tracks are not affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStreamCache(); confirmClearCache = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearCache = false }) { Text("Cancel") } }
        )
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text("Clear All Downloads?") },
            text = { Text("Removes every downloaded track from this device. Nothing is deleted from the server.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDownloads(); confirmClearDownloads = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearDownloads = false }) { Text("Cancel") } }
        )
    }

    if (confirmClearArtwork) {
        AlertDialog(
            onDismissRequest = { confirmClearArtwork = false },
            title = { Text("Clear Artwork Cache?") },
            text = { Text("Artwork will be re-downloaded as needed. Artwork for downloaded tracks, albums, and playlists is kept separately and isn't affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearArtworkCache(); confirmClearArtwork = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearArtwork = false }) { Text("Cancel") } }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear Temporary Cache & Artwork?") },
            text = { Text("Clears the temporary stream cache and artwork cache. Permanent downloads are not affected — use \"Clear\" under Permanent Downloads for those.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearStreamCache()
                    viewModel.clearArtworkCache()
                    confirmClearAll = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StorageRow(label: String, valueLabel: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
