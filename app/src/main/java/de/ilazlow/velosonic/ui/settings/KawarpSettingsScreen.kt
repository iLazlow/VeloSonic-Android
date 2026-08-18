package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Every tunable [Kawarp-AGSL](https://github.com/meowarex/kawarp-agsl) exposes, at its own
 * documented defaults — see [de.ilazlow.velosonic.data.datastore.KawarpSettings]'s doc comment
 * and [de.ilazlow.velosonic.ui.player.KawarpBackdrop] for where these get applied. Android-only,
 * no iOS screen to mirror the layout of.
 */
@Composable
fun KawarpSettingsScreen(onBack: () -> Unit, viewModel: KawarpSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Liquid Cover Backdrop", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Liquid Cover Backdrop",
                    subtitle = "Replace the player's static blurred backdrop with a live, animated warp effect driven by the current cover",
                    checked = settings.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Motion") }
            item {
                SettingsSliderRow(
                    title = "Warp Intensity",
                    valueLabel = "%.2f".format(settings.warpIntensity),
                    value = settings.warpIntensity,
                    valueRange = 0f..3f,
                    steps = 29,
                    onValueChange = viewModel::setWarpIntensity
                )
            }
            item {
                SettingsSliderRow(
                    title = "Animation Speed",
                    valueLabel = "%.2f".format(settings.animationSpeed),
                    value = settings.animationSpeed,
                    valueRange = 0f..3f,
                    steps = 29,
                    onValueChange = viewModel::setAnimationSpeed
                )
            }
            item {
                SettingsSliderRow(
                    title = "Scale",
                    valueLabel = "%.2f".format(settings.scale),
                    value = settings.scale,
                    valueRange = 0.5f..2f,
                    steps = 29,
                    onValueChange = viewModel::setScale
                )
            }
            item {
                SettingsSliderRow(
                    title = "Transition Duration",
                    valueLabel = "${settings.transitionDurationMs} ms",
                    value = settings.transitionDurationMs.toFloat(),
                    valueRange = 0f..3000f,
                    steps = 29,
                    onValueChange = { viewModel.setTransitionDurationMs(it.toInt()) }
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Playback-Reactive",
                    subtitle = "Coast the animation to a stop over ~1.8s while paused, instead of freezing mid-motion or continuing at full speed",
                    checked = settings.playbackReactive,
                    onCheckedChange = viewModel::setPlaybackReactive
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Look") }
            item {
                SettingsSliderRow(
                    title = "Blur Passes",
                    valueLabel = settings.blurPasses.toString(),
                    value = settings.blurPasses.toFloat(),
                    valueRange = 1f..40f,
                    steps = 38,
                    onValueChange = { viewModel.setBlurPasses(it.toInt()) }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Saturation",
                    valueLabel = "%.2f".format(settings.saturation),
                    value = settings.saturation,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = viewModel::setSaturation
                )
            }
            item {
                SettingsSliderRow(
                    title = "Contrast",
                    valueLabel = "%.2f".format(settings.contrast),
                    value = settings.contrast,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = viewModel::setContrast
                )
            }
            item {
                SettingsSliderRow(
                    title = "Brightness",
                    valueLabel = "%.2f".format(settings.brightness),
                    value = settings.brightness,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = viewModel::setBrightness
                )
            }
            item {
                SettingsSliderRow(
                    title = "Dithering",
                    valueLabel = "%.3f".format(settings.dithering),
                    value = settings.dithering,
                    valueRange = 0f..0.05f,
                    steps = 49,
                    onValueChange = viewModel::setDithering
                )
            }
            item {
                SettingsSliderRow(
                    title = "Auto-Darken",
                    valueLabel = "%.2f".format(settings.autoDarken),
                    value = settings.autoDarken,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = viewModel::setAutoDarken
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Tint") }
            item {
                Text(
                    text = "Dark regions of the cover are pushed toward this color before blurring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item {
                SettingsSliderRow(
                    title = "Tint Red",
                    valueLabel = "%.2f".format(settings.tintColorR),
                    value = settings.tintColorR,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = { viewModel.setTintColor(it, settings.tintColorG, settings.tintColorB) }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Tint Green",
                    valueLabel = "%.2f".format(settings.tintColorG),
                    value = settings.tintColorG,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = { viewModel.setTintColor(settings.tintColorR, it, settings.tintColorB) }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Tint Blue",
                    valueLabel = "%.2f".format(settings.tintColorB),
                    value = settings.tintColorB,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = { viewModel.setTintColor(settings.tintColorR, settings.tintColorG, it) }
                )
            }
            item {
                SettingsSliderRow(
                    title = "Tint Intensity",
                    valueLabel = "%.2f".format(settings.tintIntensity),
                    value = settings.tintIntensity,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = viewModel::setTintIntensity
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = viewModel::resetToDefaults) {
                        Text("Reset Look to Defaults")
                    }
                }
            }
        }
    }
}
