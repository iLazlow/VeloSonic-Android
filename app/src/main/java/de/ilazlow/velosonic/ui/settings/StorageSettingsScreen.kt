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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
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

    val clearLabel = stringResource(id = R.string.settings_storage_clear)
    val cancelLabel = stringResource(id = R.string.settings_storage_cancel)
    val runLabel = stringResource(id = R.string.settings_storage_run)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(id = R.string.settings_storage_title), onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSectionHeader(stringResource(id = R.string.settings_storage_section_information)) }
            item {
                StorageRow(label = stringResource(id = R.string.settings_storage_temp_cache), valueLabel = formatBytes(streamCacheBytes), actionLabel = clearLabel, onAction = { confirmClearCache = true })
            }
            item {
                StorageRow(label = stringResource(id = R.string.settings_storage_permanent_downloads), valueLabel = formatBytes(downloadsBytes), actionLabel = clearLabel, onAction = { confirmClearDownloads = true })
            }
            item {
                StorageRow(label = stringResource(id = R.string.settings_storage_artwork_cache), valueLabel = formatBytes(artworkCacheBytes), actionLabel = clearLabel, onAction = { confirmClearArtwork = true })
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { confirmClearAll = true }) {
                        Text(stringResource(id = R.string.settings_storage_clear_all), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_storage_section_maintenance)) }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(id = R.string.settings_storage_cleanup_orphaned), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::cleanUpOrphanedFiles) { Text(runLabel) }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(id = R.string.settings_storage_verify_playlists), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::verifyDownloadedPlaylists) { Text(runLabel) }
                }
            }
            item {
                Text(
                    text = stringResource(id = R.string.settings_storage_maintenance_description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_storage_section_cache_limit)) }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_storage_max_cache_size),
                    valueLabel = stringResource(id = R.string.settings_storage_cache_size_value, settings.cacheLimitMb),
                    value = settings.cacheLimitMb.toFloat(),
                    valueRange = 100f..10000f,
                    steps = 98,
                    onValueChange = { viewModel.setCacheLimitMb(it.toInt()) }
                )
            }
            item {
                Text(
                    text = stringResource(id = R.string.settings_storage_cache_limit_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_storage_section_precaching)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_storage_precache_next_songs),
                    checked = settings.preCacheSongsEnabled,
                    onCheckedChange = viewModel::setPreCacheSongsEnabled
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_storage_next_songs_to_cache),
                    valueLabel = "${settings.songsToCache}",
                    value = settings.songsToCache.toFloat(),
                    valueRange = 1f..20f,
                    steps = 18,
                    onValueChange = { viewModel.setSongsToCache(it.toInt()) }
                )
            }
            item {
                Text(
                    text = stringResource(id = R.string.settings_storage_precache_description),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_storage_section_download_quality)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDownloadTranscoding)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(id = R.string.settings_storage_quality), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = formatTranscodingSummary(settings.downloadFormat, settings.downloadBitrate, settings.customDownloadFormat, settings.customDownloadBitrate, offLabel = stringResource(id = R.string.settings_storage_original)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SettingsPickerRow(
                    label = stringResource(id = R.string.settings_storage_download_via),
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
            title = { Text(stringResource(id = R.string.settings_storage_section_maintenance)) },
            text = { Text(maintenanceResult.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearMaintenanceResult) { Text(stringResource(id = R.string.settings_storage_ok)) } }
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(id = R.string.settings_storage_clear_stream_cache_title)) },
            text = { Text(stringResource(id = R.string.settings_storage_clear_stream_cache_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStreamCache(); confirmClearCache = false }) {
                    Text(clearLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearCache = false }) { Text(cancelLabel) } }
        )
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text(stringResource(id = R.string.settings_storage_clear_downloads_title)) },
            text = { Text(stringResource(id = R.string.settings_storage_clear_downloads_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDownloads(); confirmClearDownloads = false }) {
                    Text(clearLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearDownloads = false }) { Text(cancelLabel) } }
        )
    }

    if (confirmClearArtwork) {
        AlertDialog(
            onDismissRequest = { confirmClearArtwork = false },
            title = { Text(stringResource(id = R.string.settings_storage_clear_artwork_title)) },
            text = { Text(stringResource(id = R.string.settings_storage_clear_artwork_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearArtworkCache(); confirmClearArtwork = false }) {
                    Text(clearLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearArtwork = false }) { Text(cancelLabel) } }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(id = R.string.settings_storage_clear_all_title)) },
            text = { Text(stringResource(id = R.string.settings_storage_clear_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearStreamCache()
                    viewModel.clearArtworkCache()
                    confirmClearAll = false
                }) {
                    Text(clearLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text(cancelLabel) } }
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
