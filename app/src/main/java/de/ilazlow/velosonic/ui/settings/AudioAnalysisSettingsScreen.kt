package de.ilazlow.velosonic.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Scoped-down port of AudioAnalysisSettingsView.swift: this is manual-only, matching iOS (no
 * automatic post-sync trigger — the toggle iOS itself never actually gates on is skipped, see
 * [de.ilazlow.velosonic.data.datastore.AudioAnalysisSettingsStore]'s doc comment). No BPM-vs-
 * heart-rate workout section here — that consumer doesn't exist until the Wear OS phase.
 */
@Composable
fun AudioAnalysisSettingsScreen(onBack: () -> Unit, viewModel: AudioAnalysisViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) { if (!isRunning) viewModel.refreshCounts() }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar("Audio Analysis", onBack)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = "Analyzed ${counts.analyzed} of ${counts.total} tracks" +
                            if (counts.skipped > 0) " · ${counts.skipped} skipped" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isRunning) {
                        val (analyzed, total) = progress
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            LinearProgressIndicator(
                                progress = { if (total > 0) analyzed.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "$analyzed / $total this pass",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isRunning) {
                        Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
                    } else {
                        Button(onClick = viewModel::startFullScan, modifier = Modifier.fillMaxWidth()) {
                            Text("Start Analysis")
                        }
                    }
                }
            }
            if (!isRunning && counts.skipped > 0) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        OutlinedButton(onClick = viewModel::startSkippedScan, modifier = Modifier.fillMaxWidth()) {
                            Text("Scan Skipped Tracks")
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
            item {
                SettingsSwitchRow(
                    title = "Analyze Non-Downloaded Tracks",
                    subtitle = "Fetches a short audio sample from the server for tracks not already downloaded",
                    checked = settings.analyzeNonDownloaded,
                    onCheckedChange = viewModel::setAnalyzeNonDownloaded
                )
            }
            item {
                SettingsSliderRow(
                    title = "Concurrent Songs",
                    valueLabel = settings.concurrentSongs.toString(),
                    value = settings.concurrentSongs.toFloat(),
                    valueRange = 1f..16f,
                    steps = 14,
                    onValueChange = { viewModel.setConcurrentSongs(it.toInt()) }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    TextButton(onClick = viewModel::clearSkippedQueue, enabled = counts.skipped > 0) {
                        Text("Clear Skipped Queue")
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = counts.analyzed > 0 || counts.skipped > 0
                    ) {
                        Text("Delete Analysis Data", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Analysis Data?") },
            text = { Text("Removes all BPM/key/mood analysis results and the skip queue from this device. Nothing is deleted from the server.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAllAnalysisData(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
