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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
        SettingsTopBar("Appearance", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsSwitchRow(
                        title = "Use Dynamic Color",
                        subtitle = "Theme the app from your device's system color (Settings → Wallpaper & style, Android 12+)",
                        checked = appearance.dynamicColorEnabled,
                        onCheckedChange = viewModel::setDynamicColorEnabled
                    )
                } else {
                    Text(
                        text = "Dynamic color theming requires Android 12 or newer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader("Player") }
                item {
                    NavHubRow(
                        icon = Icons.Filled.Waves,
                        label = "Liquid Cover Backdrop",
                        onClick = onNavigateToLiquidCover
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Animated Artwork") }
            item {
                SettingsSwitchRow(
                    title = "Animated Artwork",
                    subtitle = "Play animated cover art (native animated WebP, plus looping video where available) in the player and album/playlist headers",
                    checked = appearance.animatedArtworksEnabled,
                    onCheckedChange = viewModel::setAnimatedArtworksEnabled
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Animate in Grid/List",
                    subtitle = "Also animate WebP artwork in album grids, not just detail screens",
                    checked = appearance.animatedAlbumGridEnabled,
                    onCheckedChange = viewModel::setAnimatedAlbumGridEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Animated Artwork API") }
            item {
                SettingsSwitchRow(
                    title = "Fetch Missing Animated Artwork",
                    subtitle = "When a track has no animated cover of its own, look one up from a third-party API and play it as a looping muted video. This calls an unofficial, unowned external service (ama.trainswift.net by default) with no uptime guarantee.",
                    checked = appearance.applyMissingAnimatedArtworks,
                    onCheckedChange = viewModel::setApplyMissingAnimatedArtworks
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = apiUrlText,
                        onValueChange = { apiUrlText = it },
                        label = { Text("API URL") },
                        supportingText = { Text("Placeholders: {artistName}, {albumName}, {songName}") },
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
                            Text("Reset to Default")
                        }
                        TextButton(
                            onClick = { viewModel.setAnimatedArtworkApiUrl(apiUrlText) },
                            enabled = apiUrlText.isNotBlank() && apiUrlText != appearance.animatedArtworkApiUrl
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
