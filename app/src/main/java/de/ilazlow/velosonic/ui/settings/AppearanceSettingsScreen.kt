package de.ilazlow.velosonic.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R

/**
 * Occupies the same hub slot as iOS's Appearance screen — dynamic color (Android-only, no iOS
 * equivalent to mirror) plus the animated-artwork controls that re-mirror iOS's real Appearance
 * screen: native animated-WebP playback and the third-party trainswift.net "Apple animated
 * artwork" HLS/MP4 pipeline (see [de.ilazlow.velosonic.data.artwork.AnimatedArtworkRepository]).
 * Only iOS's lock-screen animated-artwork toggle has no Android equivalent here — Android's
 * media notification has no animated-artwork surface at all (`MediaStyle` supports a single
 * static bitmap only), so there's nothing to gate.
 */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    onNavigateToLiquidCover: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    var apiUrlText by remember(appearance.animatedArtworkApiUrl) { mutableStateOf(appearance.animatedArtworkApiUrl) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(id = R.string.settings_appearance_title), onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsSwitchRow(
                        title = stringResource(id = R.string.settings_appearance_dynamic_color_title),
                        subtitle = stringResource(id = R.string.settings_appearance_dynamic_color_subtitle),
                        checked = appearance.dynamicColorEnabled,
                        onCheckedChange = viewModel::setDynamicColorEnabled
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.settings_appearance_dynamic_color_unsupported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(id = R.string.settings_appearance_section_player)) }
                item {
                    NavHubRow(
                        icon = Icons.Filled.Waves,
                        label = stringResource(id = R.string.settings_appearance_liquid_cover_backdrop),
                        onClick = onNavigateToLiquidCover
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_appearance_section_animated_artwork)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_appearance_animated_artwork_title),
                    subtitle = stringResource(id = R.string.settings_appearance_animated_artwork_subtitle),
                    checked = appearance.animatedArtworksEnabled,
                    onCheckedChange = viewModel::setAnimatedArtworksEnabled
                )
            }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_appearance_animate_grid_list_title),
                    subtitle = stringResource(id = R.string.settings_appearance_animate_grid_list_subtitle),
                    checked = appearance.animatedAlbumGridEnabled,
                    onCheckedChange = viewModel::setAnimatedAlbumGridEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_appearance_section_animated_artwork_api)) }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_appearance_fetch_missing_title),
                    subtitle = stringResource(id = R.string.settings_appearance_fetch_missing_subtitle),
                    checked = appearance.applyMissingAnimatedArtworks,
                    onCheckedChange = viewModel::setApplyMissingAnimatedArtworks
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = apiUrlText,
                        onValueChange = { apiUrlText = it },
                        label = { Text(stringResource(id = R.string.settings_appearance_api_url_label)) },
                        supportingText = { Text(stringResource(id = R.string.settings_appearance_api_url_supporting_text)) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            viewModel.resetAnimatedArtworkApiUrl()
                        }) {
                            Text(stringResource(id = R.string.settings_appearance_reset_default))
                        }
                        TextButton(
                            onClick = { viewModel.setAnimatedArtworkApiUrl(apiUrlText) },
                            enabled = apiUrlText.isNotBlank() && apiUrlText != appearance.animatedArtworkApiUrl
                        ) {
                            Text(stringResource(id = R.string.settings_appearance_save))
                        }
                    }
                }
            }
        }
    }
}
