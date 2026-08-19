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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R
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

    val percentCompleteFormat = stringResource(id = R.string.settings_playback_percent_complete)
    val secondsValueFormat = stringResource(id = R.string.settings_playback_seconds_value)
    val similarMixLabel = stringResource(id = R.string.settings_playback_mix_similar)
    val artistMixLabel = stringResource(id = R.string.settings_playback_mix_artist)
    val genreMixLabel = stringResource(id = R.string.settings_playback_mix_genre)
    val globalMixLabel = stringResource(id = R.string.settings_playback_mix_global)
    fun mixModeLabel(mode: ContinuousMixMode): String = when (mode) {
        ContinuousMixMode.SIMILAR -> similarMixLabel
        ContinuousMixMode.ARTIST -> artistMixLabel
        ContinuousMixMode.GENRE -> genreMixLabel
        ContinuousMixMode.GLOBAL -> globalMixLabel
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(id = R.string.settings_playback_title), onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_scrobble),
                    subtitle = stringResource(id = R.string.settings_playback_scrobble_subtitle),
                    checked = playback.scrobblingEnabled,
                    onCheckedChange = viewModel::setScrobblingEnabled
                )
            }
            if (playback.scrobblingEnabled) {
                item {
                    SettingsPickerRow(
                        label = stringResource(id = R.string.settings_playback_scrobble_after),
                        valueLabel = String.format(percentCompleteFormat, (playback.scrobbleThreshold * 100).toInt()),
                        options = listOf(0.1, 0.2, 0.25, 0.5, 0.75, 0.9),
                        optionLabel = { String.format(percentCompleteFormat, (it * 100).toInt()) },
                        onSelect = { viewModel.setScrobbleThreshold(it) }
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_replaygain),
                    subtitle = stringResource(id = R.string.settings_playback_replaygain_subtitle),
                    checked = playback.replayGainEnabled,
                    onCheckedChange = viewModel::setReplayGainEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_gapless),
                    checked = playback.gaplessEnabled,
                    enabled = !playback.crossfadeEnabled,
                    onCheckedChange = viewModel::setGaplessEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_crossfade),
                    subtitle = stringResource(id = R.string.settings_playback_crossfade_subtitle),
                    checked = playback.crossfadeEnabled,
                    enabled = !playback.gaplessEnabled,
                    onCheckedChange = viewModel::setCrossfadeEnabled
                )
            }
            if (playback.crossfadeEnabled) {
                item {
                    SettingsSliderRow(
                        title = stringResource(id = R.string.settings_playback_crossfade_duration),
                        valueLabel = String.format(secondsValueFormat, playback.crossfadeSeconds),
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
                    Text(text = stringResource(id = R.string.settings_playback_equalizer), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    BetaBadge()
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_continuous_playback),
                    subtitle = stringResource(id = R.string.settings_playback_continuous_playback_subtitle),
                    checked = playback.continuousPlaybackEnabled,
                    onCheckedChange = viewModel::setContinuousPlaybackEnabled
                )
            }
            if (playback.continuousPlaybackEnabled) {
                item {
                    SettingsPickerRow(
                        label = stringResource(id = R.string.settings_playback_mode),
                        valueLabel = mixModeLabel(playback.continuousMixMode),
                        options = ContinuousMixMode.entries,
                        optionLabel = ::mixModeLabel,
                        onSelect = viewModel::setContinuousMixMode
                    )
                }
                item {
                    SettingsPickerRow(
                        label = stringResource(id = R.string.settings_playback_mix_limit),
                        valueLabel = "${playback.continuousMixLimit}",
                        options = listOf(1, 5, 10, 20, 50, 100),
                        optionLabel = { "$it" },
                        onSelect = viewModel::setContinuousMixLimit
                    )
                }
            }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_sonic_similar),
                    subtitle = stringResource(id = R.string.settings_playback_sonic_similar_subtitle),
                    checked = playback.sonicSimilarSongsEnabled,
                    onCheckedChange = viewModel::setSonicSimilarSongsEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_sync_play_queue),
                    subtitle = stringResource(id = R.string.settings_playback_sync_play_queue_subtitle),
                    checked = playback.syncPlayQueueEnabled,
                    onCheckedChange = viewModel::setSyncPlayQueueEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_playback_section_transcoding)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToCellularTranscoding)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(id = R.string.settings_playback_cellular), style = MaterialTheme.typography.bodyLarge)
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
                    Text(text = stringResource(id = R.string.settings_playback_wifi), style = MaterialTheme.typography.bodyLarge)
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
                    title = stringResource(id = R.string.settings_playback_streaming_mode),
                    subtitle = stringResource(id = R.string.settings_playback_streaming_mode_subtitle),
                    checked = playback.streamingModeEnabled,
                    onCheckedChange = viewModel::setStreamingModeEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_prefer_lossless_wifi),
                    subtitle = stringResource(id = R.string.settings_playback_prefer_lossless_wifi_subtitle),
                    checked = playback.preferLosslessOnWifiEnabled,
                    onCheckedChange = viewModel::setPreferLosslessOnWifiEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_keep_screen_awake),
                    subtitle = stringResource(id = R.string.settings_playback_keep_screen_awake_subtitle),
                    checked = playback.keepScreenAwakeEnabled,
                    onCheckedChange = viewModel::setKeepScreenAwakeEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_playback_sfw_mode),
                    subtitle = stringResource(id = R.string.settings_playback_sfw_mode_subtitle),
                    checked = playback.sfwModeEnabled,
                    onCheckedChange = viewModel::setSfwModeEnabled
                )
            }
        }
    }
}
