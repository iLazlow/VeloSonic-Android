package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.ilazlow.velosonic.R
import de.ilazlow.velosonic.data.datastore.StreamBitrate
import de.ilazlow.velosonic.data.datastore.StreamFormat

/**
 * Mirrors TranscodingDetailView.swift — one shared screen for choosing a transcode format +
 * bitrate (+ optional "only transcode lossless" toggle), reused for WiFi/Cellular (from Playback)
 * and Download (from Storage) quality. [onlyLossless] is null for the Download call site, since
 * iOS binds it to a fixed `.constant(false)` there (i.e. the row doesn't apply/isn't shown).
 */
@Composable
fun TranscodingDetailScreen(
    title: String,
    format: StreamFormat,
    bitrate: StreamBitrate,
    onlyLossless: Boolean?,
    customFormat: String,
    customBitrate: String,
    onBack: () -> Unit,
    onFormatChange: (StreamFormat) -> Unit,
    onBitrateChange: (StreamBitrate) -> Unit,
    onOnlyLosslessChange: (Boolean) -> Unit,
    onCustomFormatChange: (String) -> Unit,
    onCustomBitrateChange: (String) -> Unit
) {
    val customLabel = stringResource(id = R.string.settings_transcoding_custom)
    val unlimitedLabel = stringResource(id = R.string.settings_transcoding_unlimited)
    val offLabel = stringResource(id = R.string.settings_transcoding_off)
    val kbpsFormat = stringResource(id = R.string.settings_transcoding_kbps)

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title, onBack)

        SettingsPickerRow(
            label = stringResource(id = R.string.settings_transcoding_format_label),
            valueLabel = if (format == StreamFormat.CUSTOM) customLabel else format.label,
            options = StreamFormat.entries,
            optionLabel = { if (it == StreamFormat.RAW) offLabel else it.label },
            onSelect = onFormatChange
        )
        if (format == StreamFormat.CUSTOM) {
            OutlinedTextField(
                value = customFormat,
                onValueChange = onCustomFormatChange,
                label = { Text(stringResource(id = R.string.settings_transcoding_custom_format_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        SettingsPickerRow(
            label = stringResource(id = R.string.settings_transcoding_bitrate_label),
            valueLabel = if (bitrate == StreamBitrate.ORIGINAL) unlimitedLabel else if (bitrate == StreamBitrate.CUSTOM) customLabel else String.format(kbpsFormat, bitrate.kbps),
            options = StreamBitrate.entries,
            optionLabel = { when (it) { StreamBitrate.ORIGINAL -> unlimitedLabel; StreamBitrate.CUSTOM -> customLabel; else -> String.format(kbpsFormat, it.kbps) } },
            onSelect = onBitrateChange
        )
        if (bitrate == StreamBitrate.CUSTOM) {
            OutlinedTextField(
                value = customBitrate,
                onValueChange = { new -> onCustomBitrateChange(new.filter(Char::isDigit)) },
                label = { Text(stringResource(id = R.string.settings_transcoding_custom_bitrate_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        if (onlyLossless != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSwitchRow(
                title = stringResource(id = R.string.settings_transcoding_only_lossless),
                checked = onlyLossless,
                onCheckedChange = onOnlyLosslessChange
            )
        }
    }
}
