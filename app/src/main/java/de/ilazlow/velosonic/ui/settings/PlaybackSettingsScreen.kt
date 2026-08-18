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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.datastore.formatTranscodingSummary
import de.ilazlow.velosonic.playback.ContinuousMixMode

/** Mirrors PlaybackSettingsView.swift's row order/footers exactly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    onNavigateToEq: () -> Unit,
    onNavigateToWifiTranscoding: () -> Unit,
    onNavigateToCellularTranscoding: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val playback by viewModel.playbackSettings.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Playback", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Scrobble",
                    subtitle = "Report plays back to the server",
                    checked = playback.scrobblingEnabled,
                    onCheckedChange = viewModel::setScrobblingEnabled
                )
            }
            if (playback.scrobblingEnabled) {
                item {
                    SettingsPickerRow(
                        label = "Scrobble After",
                        valueLabel = "${(playback.scrobbleThreshold * 100).toInt()}% Complete",
                        options = listOf(0.1, 0.2, 0.25, 0.5, 0.75, 0.9),
                        optionLabel = { "${(it * 100).toInt()}% Complete" },
                        onSelect = { viewModel.setScrobbleThreshold(it) }
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "ReplayGain",
                    subtitle = "Audio normalization based on metadata values",
                    checked = playback.replayGainEnabled,
                    onCheckedChange = viewModel::setReplayGainEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Gapless Playback",
                    checked = playback.gaplessEnabled,
                    enabled = !playback.crossfadeEnabled,
                    onCheckedChange = viewModel::setGaplessEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Crossfade",
                    subtitle = "Overlaps the end of one song with the start of the next over the duration below.",
                    checked = playback.crossfadeEnabled,
                    enabled = !playback.gaplessEnabled,
                    onCheckedChange = viewModel::setCrossfadeEnabled
                )
            }
            if (playback.crossfadeEnabled) {
                item {
                    SettingsSliderRow(
                        title = "Crossfade Duration",
                        valueLabel = "${playback.crossfadeSeconds}s",
                        value = playback.crossfadeSeconds.toFloat(),
                        valueRange = 0f..60f,
                        steps = 59,
                        onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) }
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToEq)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Equalizer", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    BetaBadge()
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Continuous Playback",
                    subtitle = "Keep queueing similar or random tracks when the queue ends",
                    checked = playback.continuousPlaybackEnabled,
                    onCheckedChange = viewModel::setContinuousPlaybackEnabled
                )
            }
            if (playback.continuousPlaybackEnabled) {
                item {
                    SettingsPickerRow(
                        label = "Mode",
                        valueLabel = continuousMixModeLabel(playback.continuousMixMode),
                        options = ContinuousMixMode.entries,
                        optionLabel = ::continuousMixModeLabel,
                        onSelect = viewModel::setContinuousMixMode
                    )
                }
                item {
                    SettingsPickerRow(
                        label = "Mix Limit",
                        valueLabel = "${playback.continuousMixLimit}",
                        options = listOf(1, 5, 10, 20, 50, 100),
                        optionLabel = { "$it" },
                        onSelect = viewModel::setContinuousMixLimit
                    )
                }
            }
            item {
                SettingsSwitchRow(
                    title = "Sonic Similar Songs",
                    subtitle = "Use the server's AudioMuse-AI plugin (Navidrome ≥ 0.62) for similarity, when it's available — falls back to plain metadata similarity otherwise.",
                    checked = playback.sonicSimilarSongsEnabled,
                    onCheckedChange = viewModel::setSonicSimilarSongsEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Sync Play Queue",
                    subtitle = "Save and restore your play queue across devices via the server",
                    checked = playback.syncPlayQueueEnabled,
                    onCheckedChange = viewModel::setSyncPlayQueueEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Transcoding") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToCellularTranscoding)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Cellular", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = formatTranscodingSummary(playback.cellularFormat, playback.cellularBitrate, playback.customCellularFormat, playback.customCellularBitrate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToWifiTranscoding)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "WiFi", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = formatTranscodingSummary(playback.wifiFormat, playback.wifiBitrate, playback.customWifiFormat, playback.customWifiBitrate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Streaming Mode",
                    subtitle = "Stream everything, skip local downloads entirely",
                    checked = playback.streamingModeEnabled,
                    onCheckedChange = viewModel::setStreamingModeEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Prefer Lossless on WiFi",
                    subtitle = "Use the original lossless file on WiFi even if the cached/downloaded copy was transcoded to a lossy format",
                    checked = playback.preferLosslessOnWifiEnabled,
                    onCheckedChange = viewModel::setPreferLosslessOnWifiEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Keep Screen Awake",
                    subtitle = "Prevent the screen from sleeping while playing",
                    checked = playback.keepScreenAwakeEnabled,
                    onCheckedChange = viewModel::setKeepScreenAwakeEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = "SFW Mode",
                    subtitle = "Hide explicit content markers and artwork",
                    checked = playback.sfwModeEnabled,
                    onCheckedChange = viewModel::setSfwModeEnabled
                )
            }
        }
    }
}

private fun continuousMixModeLabel(mode: ContinuousMixMode): String = when (mode) {
    ContinuousMixMode.SIMILAR -> "Similar Mix"
    ContinuousMixMode.ARTIST -> "Artist Mix"
    ContinuousMixMode.GENRE -> "Genre Mix"
    ContinuousMixMode.GLOBAL -> "Global Mix"
}
