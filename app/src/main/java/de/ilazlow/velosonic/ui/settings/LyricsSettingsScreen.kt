package de.ilazlow.velosonic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ilazlow.velosonic.data.datastore.LyricsSource

/** Mirrors LyricsSettingsView.swift's Radiant Lyrics toggles + cache browser/clear-cache/credit
 *  rows + "Lyrics Source" picker. */
@Composable
fun LyricsSettingsScreen(
    onBack: () -> Unit,
    onNavigateToRadiantCache: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val lyrics by viewModel.lyricsSettings.collectAsStateWithLifecycle()
    val cacheViewModel: RadiantLyricsCacheListViewModel = hiltViewModel()
    val syncViewModel: LyricsSyncViewModel = hiltViewModel()
    val syncState by syncViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmFullResync by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsTopBar("Lyrics", onBack)
        SettingsSectionHeader("Radiant Lyrics")
        SettingsSwitchRow(
            title = "Enable Radiant Lyrics",
            subtitle = "Word-synced karaoke-style lyrics when available, tried before Navidrome/lrclib",
            checked = lyrics.radiantLyricsEnabled,
            onCheckedChange = viewModel::setRadiantLyricsEnabled
        )
        if (lyrics.radiantLyricsEnabled) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            SettingsSwitchRow(
                title = "Romanization",
                subtitle = "Show romanized text for non-Latin scripts when available",
                checked = lyrics.radiantLyricsRomanization,
                onCheckedChange = viewModel::setRadiantLyricsRomanization
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            SettingsSwitchRow(
                title = "Sparkle Effect",
                subtitle = "Decorative sparkle trail at the sweep's leading edge",
                checked = lyrics.radiantLyricsSparklesEnabled,
                onCheckedChange = viewModel::setRadiantLyricsSparklesEnabled
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToRadiantCache).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cached Lyrics", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { confirmClearCache = true }.padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text("Clear Radiant Cache", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/meowarex"))) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE91E63))
                Text("Thanks to meowarex for supporting VeloSonic", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        // Kept as its own section, separate from the toggles above, so the AI disclosure is
        // impossible to miss — this isn't just a rendering option, enabling it sends the request
        // to a server-side AI model (mirrors iOS's LyricsSettingsView, which does the same).
        SettingsSectionHeader("AI Synthesize")
        SettingsSwitchRow(
            title = "AI Synthesize",
            subtitle = "When enabled, Radiant Lyrics uses a server-side AI model to generate word-for-word timing for songs that only have plain line-by-line lyrics. Off by default — turning this on sends your request to that AI model.",
            checked = lyrics.radiantLyricsAiSynthesizeEnabled,
            enabled = lyrics.radiantLyricsEnabled,
            onCheckedChange = viewModel::setRadiantLyricsAiSynthesizeEnabled
        )
        SettingsSectionHeader("Lyrics Source")
        LyricsSourceRow(
            title = "Auto",
            subtitle = "Try Navidrome first, then lrclib.net",
            selected = lyrics.source == LyricsSource.AUTO,
            onClick = { viewModel.setLyricsSource(LyricsSource.AUTO) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        LyricsSourceRow(
            title = "Navidrome",
            subtitle = "Server-provided lyrics only (requires an OpenSubsonic-compatible server)",
            selected = lyrics.source == LyricsSource.NAVIDROME,
            onClick = { viewModel.setLyricsSource(LyricsSource.NAVIDROME) }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
        LyricsSourceRow(
            title = "lrclib.net",
            subtitle = "Community-sourced lyrics only",
            selected = lyrics.source == LyricsSource.LRCLIB,
            onClick = { viewModel.setLyricsSource(LyricsSource.LRCLIB) }
        )

        SettingsSectionHeader("Lyrics Sync")
        LyricsSyncSection(
            state = syncState,
            onSync = { syncViewModel.startSync() },
            onStop = { syncViewModel.stopSync() },
            onFullResyncRequested = { confirmFullResync = true }
        )
        Text(
            text = "Fetches lyrics in the background for offline playback, preferring your library's already-cached tracks first.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }

    if (confirmFullResync) {
        AlertDialog(
            onDismissRequest = { confirmFullResync = false },
            title = { Text("Full Resync") },
            text = { Text("This re-checks lyrics for every track in your library, not just the ones never checked before.") },
            confirmButton = {
                TextButton(onClick = { syncViewModel.startSync(resetFirst = true); confirmFullResync = false }) {
                    Text("Full Resync", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmFullResync = false }) { Text("Cancel") } }
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Clear Radiant Cache") },
            text = { Text("This deletes every cached word-synced lyrics entry on this device. Nothing is removed from the server.") },
            confirmButton = {
                TextButton(onClick = { cacheViewModel.clearAll(); confirmClearCache = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearCache = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LyricsSyncSection(
    state: LyricsSyncUiState,
    onSync: () -> Unit,
    onStop: () -> Unit,
    onFullResyncRequested: () -> Unit
) {
    if (state.isOffline) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text("Offline Mode is on", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (state.isSyncing) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.currentTrackTitle.isNotEmpty()) {
                Text(state.currentTrackTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Syncing…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.checked} / ${state.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onStop).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("Stop", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Checked", style = MaterialTheme.typography.bodyMedium)
            Text("${state.checked} / ${state.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                color = if (state.pending == 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )
        }
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onSync).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(if (state.pending == 0) "Resync" else "Sync", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }
        if (state.checked > 0) {
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onFullResyncRequested).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text("Full Resync", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun LyricsSourceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
