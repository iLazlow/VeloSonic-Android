package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.playback.EQ_BAND_FREQUENCIES
import de.ilazlow.velosonic.playback.EQ_GAIN_MAX
import de.ilazlow.velosonic.playback.EQ_GAIN_MIN
import kotlin.math.roundToInt

/**
 * Mirrors EQSettingsView.swift: enable toggle, preset picker + "Save as Preset" + "Manage
 * Presets", a 10-band vertical slider grid, and a "Reset to Flat" action. The band grid is
 * disabled (dimmed, non-interactive) when EQ is off, same as iOS's `.disabled(!eq.isEnabled)`.
 */
@Composable
fun EqSettingsScreen(
    onBack: () -> Unit,
    onManagePresets: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val eq by viewModel.eqSettings.collectAsStateWithLifecycle()
    var showPresetMenu by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    val selectedPreset = eq.allPresets.firstOrNull { it.id == eq.selectedPresetId }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Equalizer", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingsSwitchRow(
                    title = "Enable Equalizer",
                    subtitle = "Applies the band gains below to all local playback.",
                    checked = eq.enabled,
                    onCheckedChange = viewModel::setEqEnabled
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Presets") }
            item {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPresetMenu = true }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Preset", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            text = selectedPreset?.name ?: "Custom",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                        eq.allPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = { viewModel.applyEqPreset(preset); showPresetMenu = false }
                            )
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { presetName = ""; showSaveDialog = true }) { Text("Save as Preset") }
                    TextButton(onClick = onManagePresets) { Text("Manage Presets") }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader("Bands") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .alpha(if (eq.enabled) 1f else 0.4f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    eq.gains.forEachIndexed { band, gain ->
                        BandColumn(
                            frequency = EQ_BAND_FREQUENCIES[band],
                            gain = gain,
                            enabled = eq.enabled,
                            onValueChange = { viewModel.setEqGain(band, it) }
                        )
                    }
                }
            }
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = viewModel::resetEqToFlat, enabled = eq.enabled) { Text("Reset to Flat") }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Preset") },
            text = {
                OutlinedTextField(value = presetName, onValueChange = { presetName = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = presetName.isNotBlank(),
                    onClick = { viewModel.saveCurrentEqAsPreset(presetName.trim()); showSaveDialog = false }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BandColumn(frequency: Float, gain: Float, enabled: Boolean, onValueChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = gainLabel(gain),
            style = MaterialTheme.typography.labelSmall,
            color = if (kotlin.math.abs(gain) > 0.1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalSlider(
            value = gain,
            onValueChange = onValueChange,
            valueRange = EQ_GAIN_MIN..EQ_GAIN_MAX,
            enabled = enabled,
            modifier = Modifier.width(28.dp).height(140.dp)
        )
        Text(text = freqLabel(frequency), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Hand-drawn rather than a rotated Material3 [androidx.compose.material3.Slider] — mirrors
 *  EQSettingsView.swift's own custom `VerticalSlider` (track + centered 0 dB mark + fill bar +
 *  thumb) more closely than a rotated native slider would, and sidesteps a real rendering bug
 *  where rotated `Slider`s past a certain index in a wide [Row] silently failed to draw on the
 *  test emulator. */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val centerMarkColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.7f else 0.3f)
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
    val range = valueRange.endInclusive - valueRange.start

    BoxWithConstraints(
        modifier = modifier.pointerInput(enabled, range) {
            if (!enabled) return@pointerInput
            detectDragGestures { change, _ ->
                change.consume()
                val fraction = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                val newValue = valueRange.start + fraction * range
                onValueChange(((newValue * 10f).roundToInt() / 10f).coerceIn(valueRange.start, valueRange.endInclusive))
            }
        }
    ) {
        val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
        val neutralFraction = ((0f - valueRange.start) / range).coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 4.dp.toPx()
            val cx = size.width / 2f

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(cx - trackWidth / 2f, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )

            val neutralY = size.height * (1f - neutralFraction)
            drawLine(
                color = centerMarkColor,
                start = Offset(cx - 5.dp.toPx(), neutralY),
                end = Offset(cx + 5.dp.toPx(), neutralY),
                strokeWidth = 1.dp.toPx()
            )

            val valueY = size.height * (1f - fraction)
            val fillTop = minOf(valueY, neutralY)
            val fillHeight = kotlin.math.abs(valueY - neutralY).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(cx - trackWidth / 2f, fillTop),
                size = Size(trackWidth, fillHeight),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )

            drawCircle(color = thumbColor, radius = 9.dp.toPx(), center = Offset(cx, valueY))
        }
    }
}

private fun gainLabel(gain: Float): String {
    if (kotlin.math.abs(gain) < 0.1f) return "0"
    val rounded = Math.round(gain)
    return if (gain > 0) "+$rounded" else "$rounded"
}

private fun freqLabel(frequency: Float): String =
    if (frequency >= 1000f) "${(frequency / 1000f).toInt()}k" else frequency.toInt().toString()
