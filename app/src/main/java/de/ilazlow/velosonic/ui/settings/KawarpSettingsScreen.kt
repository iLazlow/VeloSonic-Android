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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.R

/**
 * Every tunable [Kawarp-AGSL](https://github.com/meowarex/kawarp-agsl) exposes, at its own
 * documented defaults — see [de.ilazlow.velosonic.data.datastore.KawarpSettings]'s doc comment
 * and [de.ilazlow.velosonic.ui.player.KawarpBackdrop] for where these get applied. Android-only,
 * no iOS screen to mirror the layout of.
 */
@Composable
fun KawarpSettingsScreen(onBack: () -> Unit, viewModel: KawarpSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val msValueFormat = stringResource(id = R.string.settings_kawarp_ms_value)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(stringResource(id = R.string.settings_kawarp_title), onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_kawarp_enable_title),
                    subtitle = stringResource(id = R.string.settings_kawarp_enable_subtitle),
                    checked = settings.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_kawarp_section_motion)) }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_warp_intensity),
                    valueLabel = "%.2f".format(settings.warpIntensity),
                    value = settings.warpIntensity,
                    valueRange = 0f..3f,
                    steps = 29,
                    onValueChange = viewModel::setWarpIntensity
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_animation_speed),
                    valueLabel = "%.2f".format(settings.animationSpeed),
                    value = settings.animationSpeed,
                    valueRange = 0f..3f,
                    steps = 29,
                    onValueChange = viewModel::setAnimationSpeed
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_scale),
                    valueLabel = "%.2f".format(settings.scale),
                    value = settings.scale,
                    valueRange = 0.5f..2f,
                    steps = 29,
                    onValueChange = viewModel::setScale
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_transition_duration),
                    valueLabel = String.format(msValueFormat, settings.transitionDurationMs),
                    value = settings.transitionDurationMs.toFloat(),
                    valueRange = 0f..3000f,
                    steps = 29,
                    onValueChange = { viewModel.setTransitionDurationMs(it.toInt()) }
                )
            }
            item {
                SettingsSwitchRow(
                    title = stringResource(id = R.string.settings_kawarp_playback_reactive),
                    subtitle = stringResource(id = R.string.settings_kawarp_playback_reactive_subtitle),
                    checked = settings.playbackReactive,
                    onCheckedChange = viewModel::setPlaybackReactive
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_kawarp_section_look)) }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_blur_passes),
                    valueLabel = settings.blurPasses.toString(),
                    value = settings.blurPasses.toFloat(),
                    valueRange = 1f..40f,
                    steps = 38,
                    onValueChange = { viewModel.setBlurPasses(it.toInt()) }
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_saturation),
                    valueLabel = "%.2f".format(settings.saturation),
                    value = settings.saturation,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = viewModel::setSaturation
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_contrast),
                    valueLabel = "%.2f".format(settings.contrast),
                    value = settings.contrast,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = viewModel::setContrast
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_brightness),
                    valueLabel = "%.2f".format(settings.brightness),
                    value = settings.brightness,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = viewModel::setBrightness
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_dithering),
                    valueLabel = "%.3f".format(settings.dithering),
                    value = settings.dithering,
                    valueRange = 0f..0.05f,
                    steps = 49,
                    onValueChange = viewModel::setDithering
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_auto_darken),
                    valueLabel = "%.2f".format(settings.autoDarken),
                    value = settings.autoDarken,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = viewModel::setAutoDarken
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(id = R.string.settings_kawarp_section_tint)) }
            item {
                Text(
                    text = stringResource(id = R.string.settings_kawarp_tint_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_tint_red),
                    valueLabel = "%.2f".format(settings.tintColorR),
                    value = settings.tintColorR,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = { viewModel.setTintColor(it, settings.tintColorG, settings.tintColorB) }
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_tint_green),
                    valueLabel = "%.2f".format(settings.tintColorG),
                    value = settings.tintColorG,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = { viewModel.setTintColor(settings.tintColorR, it, settings.tintColorB) }
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_tint_blue),
                    valueLabel = "%.2f".format(settings.tintColorB),
                    value = settings.tintColorB,
                    valueRange = 0f..1f,
                    steps = 19,
                    onValueChange = { viewModel.setTintColor(settings.tintColorR, settings.tintColorG, it) }
                )
            }
            item {
                SettingsSliderRow(
                    title = stringResource(id = R.string.settings_kawarp_tint_intensity),
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
                        Text(stringResource(id = R.string.settings_kawarp_reset_look))
                    }
                }
            }
        }
    }
}
